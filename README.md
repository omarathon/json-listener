# # json-listener

A tool to *indefinitely listen to the creation of .json files in a directory* and *automatically upload them to a Google Firebase*, utilising [FirebasePostJson](https://github.com/omarathon/firebase-post-json).

## Functionality

Inputs:

 - FirebaseConnection object, which may be intialised as in **Example.java** (or see **lib/firebasepostjson/lib/FirebaseConnection.java**) - this is to allow a connection to a Google Firebase.
 - Directory to listen for files in.
 - Directory to generate the logs in.
 
The listener may be run with the above data, listening in the input directory and generating a log within the input log directory.

## Additional Listener Configuation

There are additional parameters one may configure their listener with, by using the associated **mutator methods** on their JsonListener object (they are set to defaults upon initialization):

 - **Max Threads** - set the maximum threads useable by the JVM while running the listener (it uses a potentially unbounded amount of threads if polling many locked files simultaneously). **Default Value:** 100. **Mutator Method**: setMaxThreads.
 - **Max Locked File Tries** - The maxiumum number of attempts at polling a locked file to await its unlock. **Default Value:** 100. **Mutator Method**: setLockedFileTries.
 - **Poll Cooldown** - The listener's cooldown between each poll for FILE_CREATE events, in milliseconds. **Default Value:** 100. **Mutator Method**: setPollCooldown.
 - **Locked File Poll Cooldown** - The cooldown between each poll checking whether a locked file has became unlocked, in milliseconds. **Default Value:** 100. **Mutator Method**: setLockedFilePollCooldown.
## Dependencies

This project was developed via Maven, and used the following dependencies as libraries:

 - **Google Firebase** - com.google.firebase, firebase-admin
 - **firebase4j** - com.github.bane73, firebase4j
 - **Google Gson** - com.google.code.gson, gson
 
One must install such dependencies within their project to allow the implementation of this tool.

Note that these dependencies come from [FirebasePostJson](https://github.com/omarathon/firebase-post-json), which was utilised within the project.

Below are the dependencies and repositories within the **pom.xml** when developing this project:

```
<dependencies>
        <dependency>
            <groupId>com.google.firebase</groupId>
            <artifactId>firebase-admin</artifactId>
            <version>6.8.1</version>
        </dependency>

        <dependency>
            <groupId>com.github.bane73</groupId>
            <artifactId>firebase4j</artifactId>
            <version>Tmaster-b6f90e9764-1</version>
        </dependency>

        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.8.5</version>
        </dependency>
</dependencies>

<properties>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
</properties>

<repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
</repositories>
 ```

## Usage

One may use this tool by adding the above dependencies to their project, and storing **JsonListener.java**, as well as the **files within lib**, somewhere within their project. Then they may proceed to interact with **JsonListener** in an *examplar fashion* shown within **Example.java**.

## Main Files
 - **JsonListener.java** - The central file, listens in a directory indefinitely and posts a JSON file to a Google Firebase when a json file is created there.
 - **Example.java** - An examplar use of JsonListener.
 
## Auxiliary Files

One may find all of the auxiliary files within the **lib** directory.

 - **firebasepostjson** - This directory stores the required files for [FirebasePostJson](https://github.com/omarathon/firebase-post-json).
 
## Remark

One is recommended only to use this code within prototype systems - *it may not be safe for production*.
