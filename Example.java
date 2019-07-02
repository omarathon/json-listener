/*
    An examplar use of JsonListener.
*/

import lib.firebasepostjson.lib.FirebaseConnection;
import net.thegreshams.firebase4j.error.FirebaseException;
import java.io.IOException;
import java.nio.file.Paths;

public class Example {
    public static void main(String[] args) {
        // Firstly attempt to construct a FirebaseConnection object, for a database at e.g "https://my-project.firebaseio.com"
        FirebaseConnection connection;
        try {
            connection = new FirebaseConnection("https://my-project.firebaseio.com");
        }
        catch (FirebaseException e) {
            // Failed to connect to the database.
            System.err.println("Failure to connect: " + e.toString());
            return;
        }
        /* Now attempt to construct a JsonListener object, where in this example the directory we're listening in is C:/data,
           we would like to POST to the "~/listener" path of our Google Firebase (where ~ is the root path used to connect as above),
           and would like to store the logs from the listener in C:/data/logs. */
        JsonListener listener;
        try {
            listener = new JsonListener(connection, Paths.get("C:/data"), "listener", Paths.get("C:/data/logs"));
        }
        catch (IOException e) {
            // Failed to construct Listener object.
            System.err.println("Failure configuring listener!" + e.toString());
            return;
        }
        // Now open a new thread, and call runListener from that thread to run the listener indefinitely asynchronously.
        new Thread(() -> {
            listener.runListener();
        }).start();
    }
}