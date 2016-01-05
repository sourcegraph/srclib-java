package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ScanCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanCommand.class);

    public static final String JDK_TEST_REPO = "github.com/sgtest/java-jdk-sample";
    public static final String ANDROID_SDK_REPO = "android.googlesource.com/platform/frameworks/base";
    public static final String ANDROID_CORE_REPO = "android.googlesource.com/platform/libcore";
    public static final String ANDROID_SUPPORT_FRAMEWORK_REPO = "android.googlesource.com/platform/frameworks/support";


    /**
     * Main method
     */
    public void Execute() {
        // TODO(sqs): Previously there was a --repo flag. This no
        // longer exists. We use the flag's value to determine if this
        // is a special repo (JDK). We need a new way to do this.
        String repoURI = "";

        try {
            // Scan for source units.
            List<SourceUnit> units = new ArrayList<>();
            switch (repoURI) {
                case JDK_TEST_REPO:
                    LOGGER.info("Collecting test JDK source units");
                    units.addAll(JDKProject.standardSourceUnits());
                    break;
                case ANDROID_SDK_REPO:
                    LOGGER.info("Collecting Android SDK source units");
                    units.add(AndroidSDKProject.createSourceUnit());
                    break;
                case ANDROID_CORE_REPO:
                    LOGGER.info("Collecting Android core source units");
                    units.add(AndroidCoreProject.createSourceUnit());
                    break;
                default:
                    if (repoURI.startsWith(JDKProject.OPENJDK_REPO_ROOT)) {
                        LOGGER.info("Collecting JDK source units");
                        units.addAll(JDKProject.standardSourceUnits());
                        break;
                    }
                    // Recursively find all Maven and Gradle projects.
                    LOGGER.info("Collecting Maven source units");
                    units.addAll(MavenProject.findAllSourceUnits(repoURI));
                    LOGGER.info("Collecting Gradle source units");
                    units.addAll(GradleProject.findAllSourceUnits(repoURI));
                    break;
            }
            normalize(units);
            JSONUtil.writeJSON(units);
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred while collecting source units", e);
            System.exit(1);
        }
    }

    /**
     * Normalizes source units produces by scan command (sorts, relativizes file paths etc)
     * @param units source units to normalize
     */
    @SuppressWarnings("unchecked")
    private static void normalize(Collection<SourceUnit> units) {

        Comparator<RawDependency> dependencyComparator = Comparator.comparing(dependency -> dependency.artifactID);
        dependencyComparator = dependencyComparator.
                thenComparing(dependency -> dependency.groupID).
                thenComparing(dependency -> dependency.version).
                thenComparing(dependency -> dependency.scope).
                thenComparing(dependency -> dependency.file == null ? StringUtils.EMPTY : dependency.file);

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
            List<String> internalFiles = new ArrayList<>();
            List<String> externalFiles = new ArrayList<>();
            splitInternalAndExternalFiles(unit.Files, internalFiles, externalFiles);
            unit.Files = internalFiles;
            if (!externalFiles.isEmpty()) {
                unit.Data.put("ExtraSourceFiles", externalFiles);
            }
            if (unit.Data.containsKey("POMFile")) {
                unit.Data.put("POMFile", PathUtil.relativizeCwd((String) unit.Data.get("POMFile")));
            }
            if (unit.Data.containsKey("ClassPath")) {
                Collection<String> classPath = (Collection<String>) unit.Data.get("ClassPath");
                classPath = classPath.stream().
                        map(PathUtil::relativizeCwd).
                        collect(Collectors.toList());
                unit.Data.put("ClassPath", classPath);
            }
            if (unit.Data.containsKey("BootClassPath")) {
                Collection<String> classPath = (Collection<String>) unit.Data.get("BootClassPath");
                classPath = classPath.stream().
                        map(PathUtil::relativizeCwd).
                        sorted().
                        collect(Collectors.toList());
                unit.Data.put("BootClassPath", classPath);
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

    /**
     * Splits files to two lists, one that will keep files inside of current working directory
     * (may be used as unit.Files) and the other that will keep files outside of current working directory.
     * Sorts both lists alphabetically after splitting
     * @param files list of files to split
     * @param internal list to keep files inside of current working directory
     * @param external list to keep files outside of current working directory
     */
    private static void splitInternalAndExternalFiles(Collection<String> files,
                                                      List<String> internal,
                                                      List<String> external) {
        for (String file : files) {
            Path p = PathUtil.CWD.resolve(file).toAbsolutePath();
            if (p.startsWith(PathUtil.CWD)) {
                internal.add(PathUtil.relativizeCwd(p));
            } else {
                external.add(PathUtil.normalize(file));
            }
        }
        internal.sort(String::compareTo);
        external.sort(String::compareTo);
    }
}
