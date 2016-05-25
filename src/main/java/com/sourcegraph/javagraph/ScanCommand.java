package com.sourcegraph.javagraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
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

        Comparator<SourcePathElement> sourcePathComparator = Comparator.comparing(
                sourcePathElement -> sourcePathElement.name);
        sourcePathComparator = sourcePathComparator.
                thenComparing(sourcePathElement -> sourcePathElement.version).
                thenComparing(sourcePathElement -> sourcePathElement.filePath);

        Comparator<Key> keyComparator = Comparator.comparing(key -> key.Name, Comparator.nullsFirst(String::compareTo));
        keyComparator = keyComparator.
                thenComparing(key -> key.Version, Comparator.nullsFirst(String::compareTo)).
                thenComparing(key -> key.Type, Comparator.nullsFirst(String::compareTo)).
                thenComparing(key -> key.CommitID, Comparator.nullsFirst(String::compareTo)).
                thenComparing(key -> key.Repo, Comparator.nullsFirst(String::compareTo));

        for (SourceUnit unit : units) {
            unit.Dir = PathUtil.relativizeCwd(unit.Dir);
            unit.Data.Dependencies = unit.Data.Dependencies.stream()
                    .map(dependency -> {
                        if (dependency.file != null) {
                            dependency.file = PathUtil.relativizeCwd(dependency.file);
                        }
                        return dependency;
                    })
                    .sorted(dependencyComparator)
                    .collect(Collectors.toList());

            unit.Dependencies = unit.Data.Dependencies.stream()
                    .map(dependency -> {
                        Key key = new Key();
                        key.Name = dependency.groupID + '/' + dependency.artifactID;
                        key.Version = dependency.version;
                        key.Type = SourceUnit.DEFAULT_TYPE;
                        return key;
                    })
                    .sorted(keyComparator)
                    .collect(Collectors.toList());

            List<String> internalFiles = new ArrayList<>();
            List<String> externalFiles = new ArrayList<>();
            splitInternalAndExternalFiles(unit.Files, internalFiles, externalFiles);
            unit.Files = internalFiles;
            if (!externalFiles.isEmpty()) {
                unit.Data.ExtraSourceFiles = externalFiles;
            }
            if (unit.Data.POMFile != null) {
                unit.Data.POMFile = PathUtil.relativizeCwd(unit.Data.POMFile);
            }

            if (unit.Data.BuildXML != null) {
                unit.Data.BuildXML = PathUtil.relativizeCwd(unit.Data.BuildXML);
            }

            if (unit.Data.ClassPath != null) {
                unit.Data.ClassPath = unit.Data.ClassPath.stream().
                        map(PathUtil::relativizeCwd).
                        collect(Collectors.toList());
            }
            if (unit.Data.BootClassPath != null) {
                unit.Data.BootClassPath = unit.Data.BootClassPath.stream().
                        map(PathUtil::relativizeCwd).
                        sorted().
                        collect(Collectors.toList());
            }
            if (unit.Data.SourcePath != null) {
                unit.Data.SourcePath = unit.Data.SourcePath.stream().
                        map(sourcePathElement -> {
                            sourcePathElement.filePath = PathUtil.relativizeCwd(sourcePathElement.filePath);
                            return sourcePathElement;
                        }).
                        sorted(sourcePathComparator).
                        collect(Collectors.toList());
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
