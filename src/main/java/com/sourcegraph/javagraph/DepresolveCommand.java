package com.sourcegraph.javagraph;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class DepresolveCommand {
    public void Execute() {
        // Read in source unit from stdin.
        SourceUnit unit = null;
        try {
            InputStreamReader reader = new InputStreamReader(System.in);
            unit = new Gson().fromJson(reader, SourceUnit.class);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        Resolver rs = new Resolver(unit.getProject());

        // Resolve all raw dependencies.
        final ArrayList<DepResolution> resolutions = new ArrayList<>();
        for (RawDependency rawDep : unit.Dependencies) {
            DepResolution res = rs.resolveRawDep(rawDep);
            resolutions.add(res);
        }

        // All units but the JDK itself depend on the JDK.
        if (!unit.getProject().getClass().equals(JDKProject.class)) {
            resolutions.add(new DepResolution(null, ResolvedTarget.jdk()));
        }

        JSONUtil.writeJSON(resolutions);
    }

}
