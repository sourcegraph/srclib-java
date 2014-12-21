package com.sourcegraph.javagraph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class DepresolveCommand {
    public void Execute() {
        Gson gson = new GsonBuilder().serializeNulls().create();

        // Read in SourceUnit from stdin
        SourceUnit unit = null;
        try {
            InputStreamReader reader = new InputStreamReader(System.in);
            unit = gson.fromJson(reader, SourceUnit.class);
            reader.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.exit(1);
        }

        // Resolve all raw dependencies
        final ArrayList<Resolution> resolutions = new ArrayList<Resolution>();
        for (RawDependency rawDep : unit.Dependencies) {
            resolutions.add(rawDep.Resolve());
        }

        // All units but the JDK itself depend on the Std lib
        if (!unit.Type.equals("Java"))
            resolutions.add(Resolution.StdLib());

        // Print out resolved dependencies
        System.out.println(gson.toJson(resolutions));
    }

    static class Resolution {
        private static Resolution stdlib = null;
        private static Resolution androidSDK = null;
        RawDependency Raw;
        ResolvedTarget Target;
        String Error;

        public static Resolution StdLib() {
            if (stdlib == null) {
                stdlib = new Resolution();
                stdlib.Target = new ResolvedTarget();
                stdlib.Target.ToRepoCloneURL = SourceUnit.StdLibRepoURI;
                stdlib.Target.ToUnitType = "Java";
                stdlib.Target.ToUnit = ".";
            }

            return stdlib;
        }

        public static Resolution AndroidSDK() {
            if (androidSDK == null) {
                androidSDK = new Resolution();
                androidSDK.Target = new ResolvedTarget();
                androidSDK.Target.ToRepoCloneURL = SourceUnit.AndroidSdkURI;
                androidSDK.Target.ToUnitType = "JavaArtifact";
                androidSDK.Target.ToUnit = ".";
            }
            return androidSDK;
        }
    }

    static class ResolvedTarget {
        String ToRepoCloneURL;
        String ToUnit;
        String ToUnitType;
        String ToVersionString;
    }
}
