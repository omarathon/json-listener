/*
    A lightweight, simple, JSON parser utilising Google's GSON library.
    Converts a JSON file into a Map<String, Object>: a recursive structure.
    Reference: https://github.com/google/gson

    Author: Omar Tanner, 2019 -- open source.
*/

package lib.firebasepostjson.lib;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.*;
import java.lang.reflect.Type;
import java.util.Map;

public class JsonToMap {
    // Takes the File object to the JSON file, and then converts and deserializes into a Map<String, Object> object.
    public static Map<String, Object> parse(File jsonFile) throws IOException {
        // Construct a BuffedReader for the input JSON File object.
        BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(jsonFile)));
        // Store in a Google object the type of the output of the upcoming fromJson.
        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
        // Construct Map<String, Object> from the input json via fromJson and the above type, from a Gson instance.
        return new Gson().fromJson(r, mapType);
    }
}