package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
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

    /**
     * Main method
     */
    public void Execute() {

        SourceUnit unit = null;
        Reader r = null;
        try {
            if (!StringUtils.isEmpty(debugUnitFile)) {
                LOGGER.debug("Reading source unit JSON data from {}", debugUnitFile);
                r = Files.newBufferedReader(FileSystems.getDefault().getPath(debugUnitFile));
            } else {
                r = new InputStreamReader(System.in);
            }
            unit = new Gson().fromJson(r, SourceUnit.class);
        } catch (IOException e) {
            LOGGER.error("Failed to read source unit data", e);
            System.exit(1);
        } finally {
            IOUtils.closeQuietly(r);
        }
        LOGGER.info("Resolving dependencies of {}", unit.Name);

        Project project = unit.getProject();
        Resolver rs = new Resolver(project, unit);

        LOGGER.debug("Resolving deps");
        // Resolve all raw dependencies.
        final ArrayList<DepResolution> resolutions = new ArrayList<>();
        for (RawDependency rawDep : unit.Dependencies) {
            DepResolution res = rs.resolveRawDep(rawDep);
            resolutions.add(res);
        }
        LOGGER.debug("Deps resolved");

        // All units but the JDK itself depend on the JDK.
        if (!(project instanceof JDKProject)) {
            LOGGER.debug("Adding JDK dep");
            if (AndroidCoreProject.is(unit)) {
                // no dependencies
            } else if (AndroidSDKProject.is(unit)) {
                // only Android Core
                resolutions.add(new DepResolution(null, ResolvedTarget.androidCore()));
            } else if (unit.Data.containsKey("Android")) {
                resolutions.add(new DepResolution(null, ResolvedTarget.androidCore()));
                resolutions.add(new DepResolution(null, ResolvedTarget.androidSDK()));
            } else {
                resolutions.add(new DepResolution(null, ResolvedTarget.jdk()));
            }
        } else {
            if (!JDKProject.is(unit)) {
                resolutions.add(new DepResolution(null, ResolvedTarget.jdk()));
            }
        }

        JSONUtil.writeJSON(resolutions);
    }

}
