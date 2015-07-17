package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ScanCommand {
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
        try {
            if (repoURI == null) {
                repoURI = StringUtils.EMPTY;
            }
            if (subdir == null) {
                subdir = ".";
            }

            // Scan for source units.
            List<SourceUnit> units = new ArrayList<>();
            if (repoURI.equals(JDK_REPO) || repoURI.equals(JDK_TEST_REPO)) {
                units.addAll(JDKProject.standardSourceUnits());
            } else if (repoURI.equals(ANDROID_SDK_REPO)) {
                units.add(AndroidSDKProject.createSourceUnit(subdir));
            } else {
                // Recursively find all Maven and Gradle projects.
                units.addAll(MavenProject.findAllSourceUnits());
                units.addAll(GradleProject.findAllSourceUnits(repoURI));
            }

            JSONUtil.writeJSON(units);
        } catch (Exception e) {
            System.err.println("Uncaught error: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
