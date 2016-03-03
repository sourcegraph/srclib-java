package com.sourcegraph.javagraph;

import com.google.gson.*;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * JSON utilities
 */
public class JSONUtil {

    private JSONUtil() {
    }

    /**
     * Writes object as UTF-8 JSON
     *
     * @param o object to write
     */
    public static void writeJSON(Object o) {
        Writer w = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        gson().toJson(o, w);
        IOUtils.closeQuietly(w);
    }

    /**
     * Constructs new writer implementation
     *
     * @return configured writer implementation
     */
    private static Gson gson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        gsonBuilder.disableHtmlEscaping();
        gsonBuilder.registerTypeAdapter(Def.class, new Def.JSONSerializer());
        gsonBuilder.registerTypeAdapter(Ref.class, new Ref.JSONSerializer());
        gsonBuilder.registerTypeAdapter(JSONObject.class, (JsonSerializer<JSONObject>) (src, typeOfSrc, context) -> {
            JsonObject ret = new JsonObject();
            for (String key : src.keySet()) {
                Object o = src.get(key);
                if (o == null) {
                    ret.add(key, JsonNull.INSTANCE);
                } else if (o instanceof JSONObject) {
                    ret.add(key, context.serialize(o));
                } else if (o instanceof JSONArray) {
                    JSONArray source = (JSONArray) o;
                    JsonArray target = new JsonArray();
                    for (Object item : source) {
                        target.add(context.serialize(item));
                    }
                    ret.add(key, target);
                } else if (o instanceof Number) {
                    ret.add(key, new JsonPrimitive((Number) o));
                } else if (o instanceof String) {
                    ret.add(key, new JsonPrimitive((String) o));
                } else if (o instanceof Character) {
                    ret.add(key, new JsonPrimitive((Character) o));
                } else if (o instanceof Boolean) {
                    ret.add(key, new JsonPrimitive((Boolean) o));
                }
            }
            return ret;
        });
        return gsonBuilder.create();
    }

}
