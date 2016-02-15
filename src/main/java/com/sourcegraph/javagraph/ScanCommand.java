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

    /**
     * Main method
     */
    public void Execute() {

        try {
            // Scan for source units.
            List<SourceUnit> units = new ArrayList<>();
            // Recursively find all Maven and Gradle projects.
            LOGGER.info("Collecting Maven source units");
            units.addAll(MavenProject.findAllSourceUnits());
            LOGGER.info("Collecting Gradle source units");
            units.addAll(GradleProject.findAllSourceUnits());
            LOGGER.info("Collecting Ant source units");
            units.addAll(AntProject.findAllSourceUnits());
            normalize(units);
            JSONUtil.writeJSON(units);
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred while collecting source units", e);
            System.exit(1);
        }
    }

    /**
     * Normalizes source units produces by scan command (sorts, relativizes file paths etc)
     *
     * @param units source units to normalize
     */
    @SuppressWarnings("unchecked")
    private static void normalize(Collection<SourceUnit> units) {

        Comparator<RawDependency> dependencyComparator = Comparator.comparing(dependency -> dependency.artifactID);
        dependencyComparator = dependencyComparator.
                thenComparing(dependency -> dependency.groupID).
                thenComparing(dependency -> dependency.version).
                thenComparing(dependency -> dependency.scope).
                thenComparing((o1, o2) -> {
                    if (o1.file == null) {
                        return o2.file == null ? 0 : -1;
                    } else if (o2.file == null) {
                        return 1;
                    }
                    return o1.file.compareTo(o2.file);
                });

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

            if (unit.Data.containsKey(AntProject.BUILD_XML_PROPERTY)) {
                unit.Data.put(AntProject.BUILD_XML_PROPERTY,
                        PathUtil.relativizeCwd((String) unit.Data.get(AntProject.BUILD_XML_PROPERTY)));
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
     *
     * @param files    list of files to split
     * @param internal list to keep files inside of current working directory
     * @param external list to keep files outside of current working directory
     */
    private static void splitInternalAndExternalFiles(Collection<String> files,
                                                      List<String> internal,
                                                      List<String> external) {

        if (files == null) {
            return;
        }
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
