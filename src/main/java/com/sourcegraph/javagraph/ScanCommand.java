package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ScanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanCommand.class);

    @Parameter(names = {"--repo"}, description = "The URI of the repository that contains the directory tree being scanned")
    String repoURI;

    @Parameter(names = {"--subdir"}, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
    String subdir;

    public static final String JDK_REPO = "hg.openjdk.java.net/jdk8/jdk8/jdk";
    public static final String JDK_TEST_REPO = "github.com/sgtest/java-jdk-sample";
    public static final String ANDROID_SDK_REPO = "android.googlesource.com/platform/frameworks/base";
    public static final String TOOLS_JAR_REPO = "http://hg.openjdk.java.net/jdk8/jdk8/langtools";
    public static final String NASHORN_REPO = "http://hg.openjdk.java.net/jdk8/jdk8/nashorn";


    public void Execute() {

        LOGGER.info("Collecting source units");

        try {
            if (repoURI == null) {
                repoURI = StringUtils.EMPTY;
            }
            if (subdir == null) {
                subdir = ".";
            }

            // Scan for source units.
            List<SourceUnit> units = new ArrayList<>();
            switch (repoURI) {
                case JDK_REPO:
                case JDK_TEST_REPO:
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting JDK source units");
                    }
                    units.addAll(JDKProject.standardSourceUnits());
                    break;
                case ANDROID_SDK_REPO:
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Android source units");
                    }
                    units.add(AndroidSDKProject.createSourceUnit(subdir));
                    break;
                default:
                    // Recursively find all Maven and Gradle projects.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Maven source units");
                    }
                    units.addAll(MavenProject.findAllSourceUnits());
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Gradle source units");
                    }
                    units.addAll(GradleProject.findAllSourceUnits(repoURI));
                    break;
            }
            LOGGER.info("Source units collected");
            JSONUtil.writeJSON(units);
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred while collecting source units", e);
            System.exit(1);
        }
    }
}
