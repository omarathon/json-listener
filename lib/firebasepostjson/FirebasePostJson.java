/*
    Posts a JSON file to a Google Firebase,
    utilising the Google GSON library and the firebase4j library. (Wrapper for firebase4j post requests)
    References: https://github.com/google/gson (GSON), https://github.com/bane73/firebase4j (firebase4j).
    **WARNING**: Authentication via OAuth 2.0 currently malfunctional until fixed issue within firebase4j!!

    Author: Omar Tanner, 2019 -- open source.
*/

package lib.firebasepostjson;

import lib.firebasepostjson.lib.FirebaseConnection;
import lib.firebasepostjson.lib.JsonToMap;
import net.thegreshams.firebase4j.error.FirebaseException;
import net.thegreshams.firebase4j.error.JacksonUtilityException;
import net.thegreshams.firebase4j.model.FirebaseResponse;
import java.io.File;
import java.io.IOException;
import java.util.Map;

// In FirebaseConnection the Firebase is established, then from such Firebase object one may perform POST requests with it
public class FirebasePostJson {
    /* Post to the firebase the given JSON file at the File object, at the given path in the database (relative to the base-url the FirebaseConnection was initialised with).
       Return a FirebaseResponse object (part of firebase4j) detailing the result. */
    public static FirebaseResponse post(FirebaseConnection con, File jsonFile, String path) throws IOException, JacksonUtilityException, FirebaseException {
        if (!con.isEstablished()) throw new IllegalStateException("firebase4j has no connection to the Google Firebase!");
        // Utilises JsonToMap parser to obtain a Map<String, Object> Object for the input JSON File object
        Map<String, Object> map = JsonToMap.parse(jsonFile);
        // Obtain Firebase connection from FirebaseConnection object, and attempt to post via firebase4j, passing the path to post at and the map representation of the JSON. Return the obtained FirebaseResponse object.
        return con.get().post(path, map);
    }

    // Posts a JSON at the root of the firebase4j connection (at the base-url)
    public static FirebaseResponse post(FirebaseConnection con, File jsonFile) throws IOException, JacksonUtilityException, FirebaseException {
        return post(con, jsonFile, null);
    }
}