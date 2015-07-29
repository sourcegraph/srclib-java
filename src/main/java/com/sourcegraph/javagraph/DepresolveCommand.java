package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.ArrayList;

public class DepresolveCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(DepresolveCommand.class);

    @Parameter(names = {"--debug-unit-file"}, description = "The path to a source unit input file, which will be read as though it came from stdin. Used to mimic stdin when you can't actually pipe to stdin (e.g., in IntelliJ run configurations).")
    String debugUnitFile;

    public void Execute() {

        LOGGER.info("Resolving dependencies");

        SourceUnit unit = null;
        try {
            Reader r;
            if (!StringUtils.isEmpty(debugUnitFile)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Reading source unit JSON data from {}", debugUnitFile);
                }
                r = Files.newBufferedReader(FileSystems.getDefault().getPath(debugUnitFile));
            } else {
                r = new InputStreamReader(System.in);
            }
            unit = new Gson().fromJson(r, SourceUnit.class);
            r.close();
        } catch (IOException e) {
            LOGGER.error("Failed to read source unit data", e);
            System.exit(1);
        }

        Resolver rs = new Resolver(unit.getProject(), unit);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolving deps");
        }
        // Resolve all raw dependencies.
        final ArrayList<DepResolution> resolutions = new ArrayList<>();
        for (RawDependency rawDep : unit.Dependencies) {
            DepResolution res = rs.resolveRawDep(rawDep);
            resolutions.add(res);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Deps resolved");
        }

        // All units but the JDK itself depend on the JDK.
        if (!unit.getProject().getClass().equals(JDKProject.class)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Adding JDK dep");
            }
            resolutions.add(new DepResolution(null, ResolvedTarget.jdk()));
        }

        LOGGER.info("Dependencies resolved");

        JSONUtil.writeJSON(resolutions);
    }

}
