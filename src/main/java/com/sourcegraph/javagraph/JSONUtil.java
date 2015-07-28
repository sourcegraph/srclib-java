package com.sourcegraph.javagraph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.IOUtils;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public class JSONUtil {
    public static void writeJSON(Object o) {
        Writer w = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        gson().toJson(o, w);
        IOUtils.closeQuietly(w);
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
