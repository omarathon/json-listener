/*
    Listens for the creation of .json files in a given directory,
    and automatically uploads them to a Google Firebase, utilising FirebasePostJson.
    Reference: https://github.com/omarathon/firebase-post-json/

    Author: Omar Tanner, 2019 -- open source.
*/

import lib.firebasepostjson.FirebasePostJson;
import lib.firebasepostjson.lib.FirebaseConnection;
import net.thegreshams.firebase4j.error.FirebaseException;
import net.thegreshams.firebase4j.error.JacksonUtilityException;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.util.PriorityQueue;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import static java.nio.file.StandardWatchEventKinds.*;

public class JsonListener {
    // FirebaseConnection object which provides a firebase4j connection to the database
    private FirebaseConnection dbConnection;
    // The directory to watch
    private Path dir;
    // The directory in the database to add the JSON files to
    private String dbDirectory;
    // The WatchKey object which generates the FILE_CREATE events
    private WatchKey key;
    // Maximum number of threads that may be opened
    private int maxThreads = 100;
    // Number of tries polling a locked file before give up
    private int maxLockedFileTries = 100;
    // Number of milliseconds between each poll on the directory
    private int pollCooldown = 100;
    // Number of milliseconds between each poll for a locked file
    private int lockedFilePollCooldown = 100;
    // The JSON files that failed to upload
    private PriorityQueue<File> failedFiles;
    // The Logger object which shall be used to send log messages to
    private Logger log;
    // The PrintWriter object used to write to the file storing the failed JSON uploads
    private PrintWriter failedFilesLog;
    // An idle flag, which indicates if events are being processed or not
    private boolean idle;
    // A listening flag, which may be toggled to stop the listener and indicates if the listener is ready to receive events.
    private boolean listen;

    /* One constructs a JsonListener with a FirebaseConnection object (used to post to the database),
       the Path object storing the local directory of JSON files,
       the directory within the database to which the obtained JSON files shall be POSTed to
       and the directory to which the .log file shall be generated to. */
    public JsonListener(FirebaseConnection con, Path path, String dbDirectory, Path logDirectory) throws IOException {
        // Firstly set the dbConnection object to the one passed if it wasn't null.
        if (con != null) this.dbConnection = con;
        else throw new IllegalArgumentException("Provided FirebaseConnection is null.");
        // Set the passed path and dbDirectory as dir and dbDirectory respectfully.
        this.dir = path;
        this.dbDirectory = dbDirectory;
        // Initialise failedFiles as an empty priority queue.
        failedFiles = new PriorityQueue<>();
        // Initialise failedFilesLog as a PrintWriter at the given logDirectory
        File f = logDirectory.resolve("json-listener-failed-files.txt").toFile();
        this.failedFilesLog = new PrintWriter(f);
        // Initialise the log as the given logDirectory
        this.log = Logger.getLogger("JsonListenerLog");
        FileHandler fh;
        // Configure the logger with handler and formatter
        fh = new FileHandler(logDirectory + "/json-listener-log.log");
        this.log.addHandler(fh);
        SimpleFormatter formatter = new SimpleFormatter();
        fh.setFormatter(formatter);
        // The listener is initially idle
        this.idle = true;
        // The listener is not initially listening
        this.listen = false;
    }

    /* The main listener method, which listen indefinitely.
       Therefore, one is advised to run this method on a new thread. */
    public void runListener() {
        // Construct a WatchService object, and register to the directory the watcher and the ENTRY_CREATE event. Obtain a WatchKey for the directory, and set this to the key attribute for the object.
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            this.key = dir.register(watcher, ENTRY_CREATE);
        }
        catch (IOException e) { // Failed to construct WatchKey, so terminate
            log.severe("[FATAL ERROR] Failed to construct WatchKey!");
            return;
        }
        // The WatchKey is now polling events, therefore the listener is ready to recieve events
        this.listen = true;
        while (listen) {
            // Firstly attempt to delay for the given pollCooldown
            try {
                Thread.sleep(pollCooldown);
            }
            catch (InterruptedException e) { // Thread interrupted, fatal error so exit the listener.
                log.severe("[FATAL ERROR] Thread interrupted when attempting to sleep the main polling loop!");
                break;
            }
            // Iterate over each event polled by the WatchKey
            for (WatchEvent<?> event: key.pollEvents()) {
                // Not idle, since processing an event
                idle = false;
                WatchEvent.Kind<?> kind = event.kind();
                // If an event is lost or discarded, an OVERFLOW event is generatated, in which case skip the event.
                if (kind == OVERFLOW) continue;

                WatchEvent<Path> ev = (WatchEvent<Path>)event;
                // context of the WatchEvent is the new filename (because the event must be an ENTRY_CREATE event)
                Path filename = ev.context();
                // The above filename Path object is just the name of the file in the directory - we require the full path including the directory.
                Path child = dir.resolve(filename);
                log.info("Found new file: " + child.toString());
                // Construct PathMatcher object that matches any JSON file, to check the file extension
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.json");
                if (!matcher.matches(filename)) { // Matcher failed to match, therefore not a JSON.
                    log.warning("New file: " + child.toString() + " is not a JSON file, aborting attempt to upload.");
                    continue;
                }
                else { // File is a JSON...
                    log.info("New file: " + child.toString() + " resolved to be a JSON file.");
                    // Obtain File object from the Path object to the JSON file
                    File newJson = child.toFile();
                    /* Check if file is locked, in which case cannot extract data and upload it.
                       Therefore we poll to wait until it's unlocked. */
                    if (isFileLocked(newJson)) { // Is locked
                        log.warning("New file: " + child.toString() + " is locked!");
                        // Check if the opening of a new Thread shall exceed max threads, otherwise open new Thread to poll the locked file until it's unlocked and then upload it.
                        if (Thread.activeCount() >= maxThreads) {
                            log.severe("[FATAL ERROR] Cannot open new thread - shall exceed maximum threads!");
                            throw new RuntimeException("Cannot open new thread - shall exceed maximum threads!");
                        }
                        new Thread(() -> {
                            log.info("Opened new thread to poll locked file: " + child.toString());
                            pollUntilUnlockedAndUpload(newJson);
                        }).start();
                    }
                    else { // File is unlocked, so attempt to upload
                        log.info("New file: " + child.toString() + " is unlocked, attempting to POST...");
                        // Attempt to post the file to the database, at the directory in the database given by dbDirectory.
                        try {
                            FirebasePostJson.post(dbConnection, newJson, dbDirectory);
                            log.info("[SUCCESS] Successfully posted file " + child.toString() + " to database!");
                        }
                        catch (IOException | JacksonUtilityException | FirebaseException e) { // Failed to upload to database, so store in failedFiles, and handle next CREATE event
                            log.severe("[FAILURE} Failed to post file " + child.toString() + " to database!");
                            failedFilesLog.println(child);
                            failedFiles.add(newJson);
                            continue;
                        }
                    }
                }
            }
            // Now may be idle, as have processed all events
            idle = true;
            // Reset the key. If the key is no longer valid, the directory is inaccessible so exit the loop.
            boolean valid = key.reset();
            if (!valid) {
                log.severe("[FATAL ERROR] Directory inaccessible, key invalidated, exiting main poll loop!");
                break;
            }
        }
        // Finished running listener, therefore not ready to recieve events
        this.listen = false;
    }

    /* Polls a file until it becomes unlocked for a specified number of tries.
       When it becomes unlocked, it's uploaded.
       Returns true if the file became unlocked, false if it remained locked during the duration of the polling. */
    private void pollUntilUnlockedAndUpload(File f) {
        // Main polling loop, iterate for maximum of maxLockedFileTries times
        for (int i = 0; i < maxLockedFileTries; i++) {
            // Delay for the given locked file polling cooldown
            try {
                Thread.sleep(lockedFilePollCooldown);
            }
            catch (InterruptedException e) { // Thread interrupted, fatal error so stop polling the locked file, and store it in failedFiles
                log.severe("[FATAL ERROR] Thread interrupted when attempting to sleep the locked file polling loop for file: " + f.toString());
                failedFilesLog.println(f);
                failedFiles.add(f);
                break;
            }
            // Check if file is locked
            if (!isFileLocked(f)) { // File is unlocked, so attempt to upload it.
                log.info("File " + f.toString() + " is now unlocked. Attempting to POST...");
                // Attempt to post the file to the database, at the directory in the database given by dbDirectory.
                try {
                    FirebasePostJson.post(dbConnection, f, dbDirectory);
                    // Successful, so leave polling loop.
                    log.info("[SUCCESS] Successfully posted file " + f.toString() + " to database!");
                    break;
                }
                catch (IOException | JacksonUtilityException | FirebaseException e) { // Failed to upload to database, so stop polling
                    log.severe("[FAILURE} Failed to post file " + f.toString() + " to database!");
                    failedFilesLog.println(f);
                    failedFiles.add(f);
                    break;
                }
            }
        }
    }

    // Determines whether a file is locked
    private static boolean isFileLocked(File f) {
        // Attempt to rename the file to itself. If returned true, then it was successful so file is unlocked, if false then it was unsuccessful so file is locked.
        return !f.renameTo(new File(f.getAbsolutePath()));
    }

    /*
        Getter and setter methods.
    */

    // Setter for database directory
    public void setDbDirectory(String x) {
        this.dbDirectory = x;
    }

    // Setter for max threads
    public void setMaxThreads(int x) {
        if (x <= 2) throw new IllegalArgumentException("Maximum threads must be greater than 2.");
        this.maxThreads = x;
    }

    // Setter for max locked file poll tries
    public void setMaxLockedFileTries(int x) {
        if (x < 1) throw new IllegalArgumentException("Maximum tries for locked files must be at least 1.");
        this.maxLockedFileTries = x;
    }

    // Setter for directory poll cooldown
    public void setPollCooldown(int x) {
        if (x <= 0) throw new IllegalArgumentException("Directory poll cooldown must be greater than 0 ms.");
        this.pollCooldown = x;
    }

    // Setter for locked file poll cooldown
    public void setLockedFilePollCooldown(int x) {
        if (x <= 0) throw new IllegalArgumentException("Locked file poll cooldown must be greater than 0 ms.");
        this.lockedFilePollCooldown = x;
    }

    // Getter for database directory
    public String getDbDirectory() {
        return this.dbDirectory;
    }

    // Getter for max threads
    public int getMaxThreads() {
        return this.maxThreads;
    }

    // Setter for max locked file poll tries
    public int getMaxLockedFileTries() {
        return this.maxLockedFileTries;
    }

    // Getter for directory poll cooldown
    public int getPollCooldown() {
        return this.pollCooldown;
    }

    // Getter for locked file poll cooldown
    public int getLockedFilePollCooldown() {
        return this.lockedFilePollCooldown;
    }

    // Getter for the idle status of the listener
    public boolean isIdle() {
        return this.idle;
    }

    // Getter for the listening status of the listener
    public boolean isListening() {
        return this.listen;
    }

    // Stops the listener by setting listen flag to false
    public void stop() {
        this.listen = false;
    }
}
