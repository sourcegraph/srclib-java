package com.sourcegraph.javagraph;

import com.google.gson.*;

public class JSONUtil {
    public static void writeJSON(Object o) {
        gson().toJson(o, System.out);
    }

    private static Gson gson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.setPrettyPrinting();
        gsonBuilder.disableHtmlEscaping();
        gsonBuilder.registerTypeAdapter(Def.class, new Def.JSONSerializer());
        gsonBuilder.registerTypeAdapter(Ref.class, new Ref.JSONSerializer());
        return gsonBuilder.create();
    }

}
