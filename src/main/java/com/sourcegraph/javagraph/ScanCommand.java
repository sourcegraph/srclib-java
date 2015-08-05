package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ScanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanCommand.class);

    @Parameter(names = {"--repo"}, description = "The URI of the repository that contains the directory tree being scanned")
    String repoURI;

    @Parameter(names = {"--subdir"}, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
    String subdir;

    public static final String JDK_REPO = "hg.openjdk.java.net/jdk8/jdk8/jdk";
    public static final String JDK_TEST_REPO = "github.com/sgtest/java-jdk-sample";
    public static final String ANDROID_SDK_REPO = "android.googlesource.com/platform/frameworks/base";
    public static final String ANDROID_CORE_REPO = "android.googlesource.com/platform/libcore";
    public static final String TOOLS_JAR_REPO = "hg.openjdk.java.net/jdk8/jdk8/langtools";
    public static final String NASHORN_REPO = "hg.openjdk.java.net/jdk8/jdk8/nashorn";


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
                case JDK_TEST_REPO:
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting JDK source units");
                    }
                    units.addAll(JDKProject.standardSourceUnits());
                    break;
                case ANDROID_SDK_REPO:
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Android SDK source units");
                    }
                    units.add(AndroidSDKProject.createSourceUnit(subdir));
                    break;
                case ANDROID_CORE_REPO:
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Android core source units");
                    }
                    units.add(AndroidCoreProject.createSourceUnit(subdir));
                    break;
                default:
                    if (repoURI.startsWith("hg.openjdk.java.net/jdk8/jdk8")) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Collecting JDK source units");
                        }
                        units.addAll(JDKProject.standardSourceUnits());
                        break;
                    }
                    // Recursively find all Maven and Gradle projects.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Maven source units");
                    }
                    units.addAll(MavenProject.findAllSourceUnits(repoURI));
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Collecting Gradle source units");
                    }
                    units.addAll(GradleProject.findAllSourceUnits(repoURI));
                    break;
            }
            LOGGER.info("Source units collected");
            normalize(units);
            JSONUtil.writeJSON(units);
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred while collecting source units", e);
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private void normalize(Collection<SourceUnit> units) {

        Comparator<RawDependency> dependencyComparator = Comparator.comparing(dependency -> dependency.artifactID);
        dependencyComparator = dependencyComparator.
                thenComparing(dependency -> dependency.groupID).
                thenComparing(dependency -> dependency.version).
                thenComparing(dependency -> dependency.scope).
                thenComparing(dependency -> dependency.file);

        Comparator<String[]> sourcePathComparator = Comparator.comparing(sourcePathElement -> sourcePathElement[0]);
        sourcePathComparator = sourcePathComparator.
                thenComparing(sourcePathElement -> sourcePathElement[1]).
                thenComparing(sourcePathElement -> sourcePathElement[2]);

        for (SourceUnit unit : units) {
            unit.Dir = PathUtil.relativizeCwd(unit.Dir);
            unit.Dependencies = unit.Dependencies.stream()
                    .map(dependency -> {
                        if (dependency.file != null) {
                            dependency.file = PathUtil.relativizeCwd(dependency.file);
                        }
                        return dependency;
                    })
                    .sorted(dependencyComparator)
                    .collect(Collectors.toList());

            unit.Files = unit.Files.stream()
                    .map(PathUtil::relativizeCwd)
                    .sorted()
                    .collect(Collectors.toList());
            if (unit.Data.containsKey("POMFile")) {
                unit.Data.put("POMFile", PathUtil.relativizeCwd((String) unit.Data.get("POMFile")));
            }
            if (unit.Data.containsKey("ClassPath")) {
                Collection<String> classPath = (Collection<String>) unit.Data.get("ClassPath");
                classPath = classPath.stream().
                        map(PathUtil::relativizeCwd).
                        sorted().
                        collect(Collectors.toList());
                unit.Data.put("ClassPath", classPath);
            }
            if (unit.Data.containsKey("SourcePath")) {
                Collection<String[]> sourcePath = (Collection<String[]>) unit.Data.get("SourcePath");
                sourcePath = sourcePath.stream().
                        map(sourcePathElement -> {
                            sourcePathElement[2] = PathUtil.relativizeCwd(sourcePathElement[2]);
                            return sourcePathElement;
                        }).
                        sorted(sourcePathComparator).
                        collect(Collectors.toList());
                unit.Data.put("SourcePath", sourcePath);
            }
        }
    }
}
