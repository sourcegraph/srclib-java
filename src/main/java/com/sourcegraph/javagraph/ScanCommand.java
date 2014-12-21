package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sourcegraph.javagraph.BuildAnalysis.POMAttrs;
import org.apache.commons.lang3.SystemUtils;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ScanCommand {
    @Parameter(names = {"--repo"}, description = "The URI of the repository that contains the directory tree being scanned")
    String repoURI;

    @Parameter(names = {"--subdir"}, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
    String subdir;

    public static String getGradleClassPath(Path build) throws IOException {
        return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).classPath;
    }

    // TODO Merge this function with ‘getGradleDependencies’.
    public static BuildAnalysis.POMAttrs getGradleAttrs(String repoURI, Path build) throws IOException {
        POMAttrs attrs = BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).attrs;

        // KLUDGE: fix the project name inside docker containers. By default, the name of a Gradle project is the name
        // of its containing directory. srclib checks out code to /src inside Docker containers, which makes the name of
        // every Gradle project rooted at the VCS root directory "src". This kludge could erroneously change the project
        // name if the name is actually supposed to be "src" (e.g., if the name is set manually).
        if (System.getenv().get("IN_DOCKER_CONTAINER") != null && attrs.artifactID.equals("src")) {
            String[] parts = repoURI.split("/");
            attrs.artifactID = parts[parts.length - 1];
        }

        return attrs;
    }

    public static Path getWrapper() {
        Path result = Paths.get("./gradlew").toAbsolutePath();
        File tmp = new File(result.toString());
        if (tmp.exists() && !tmp.isDirectory()) {
            return result;
        }

        return null;
    }

    public static HashSet<RawDependency> getGradleDependencies(Path build) throws IOException {
        return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).dependencies;
    }

    public static HashSet<Path> findMatchingFiles(String fileName) throws IOException {
        String pat = "glob:**/" + fileName;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pat);
        HashSet<Path> result = new HashSet<>();

        Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matcher.matches(file))
                    result.add(file);

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Skip common build data directories and dot-directories.
                String dirName = dir.getFileName().normalize().toString();
                if (dirName.equals("build") || dirName.equals("target") || dirName.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    public static ArrayList<String> getSourcePaths() {
        ArrayList<String> sourcePaths = new ArrayList<String>();
        sourcePaths.add("src/share/classes/");

        if (SystemUtils.IS_OS_WINDOWS) {
            sourcePaths.add("src/windows/classes/");
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            sourcePaths.add("src/macosx/classes/");
        } else {
            sourcePaths.add("src/solaris/classes/");
        }

        return sourcePaths;
    }

    public static Collection<SourceUnit> stdLibUnits() throws Exception {
        List<SourceUnit> units = new ArrayList<>();

        // Standard Library Unit
        final SourceUnit unit = new SourceUnit();
        unit.Type = "Java";
        unit.Name = ".";
        unit.Dir = "src/";
        unit.Files = scanFiles(getSourcePaths());
        // Sort for testing consistency
        unit.Files.sort((String a, String b) -> a.compareTo(b));
        units.add(unit);

        // Build tools source unit
        final SourceUnit toolsUnit = new SourceUnit();
        toolsUnit.Type = "JavaArtifact";
        toolsUnit.Name = "BuildTools";
        toolsUnit.Dir = "make/src/classes/";
        toolsUnit.Files = scanFiles("make/src/classes/");
        // Sort for testing consistency
        toolsUnit.Files.sort((String a, String b) -> a.compareTo(b));
        units.add(toolsUnit);
        return units;
    }

    public static SourceUnit androidSDKUnit(String subdir) throws Exception {
        // Android Standard Library Unit
        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = "AndroidSDK";
        unit.Dir = ".";
        unit.Files = scanFiles(subdir);
        // Sort for testing consistency
        unit.Files.sort((String a, String b) -> a.compareTo(b));
        return unit;
    }

    // Recursively find .java files under a given source path
    public static List<String> scanFiles(String sourcePath) throws IOException {
        final LinkedList<String> files = new LinkedList<String>();

        if (Files.exists(Paths.get(sourcePath))) {
            Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filename = file.toString();
                    if (filename.endsWith(".java")) {
                        if (filename.startsWith("./"))
                            filename = filename.substring(2);
                        files.add(filename);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            System.err.println(sourcePath + " does not exist... Skipping...");
        }

        return files;
    }

    public static List<String> scanFiles(Collection<String> sourcePaths) throws IOException {
        final LinkedList<String> files = new LinkedList<String>();
        for (String sourcePath : getSourcePaths())
            files.addAll(scanFiles(sourcePath));
        return files;
    }

    public static List<String> scanFiles(Path resolve) throws IOException {
        return scanFiles(resolve.toString());
    }

    public void Execute() {
        try {
            if (repoURI == null) {
                repoURI = ".";
            }
            if (subdir == null) {
                subdir = ".";
            }

            List<SourceUnit> units = new ArrayList<>();

            if (SourceUnit.isStdLib(repoURI)) {
                // Standard library special cases
                if (repoURI.equals(SourceUnit.StdLibRepoURI) || repoURI.equals(SourceUnit.StdLibTestRepoURI)) {
                    units.addAll(stdLibUnits());
                } else if (repoURI.equals(SourceUnit.AndroidSdkURI)) {
                    units.add(androidSDKUnit(this.subdir));
                }
            } else {
                // Recursively find all pom.xml and build.gradle files
                HashSet<Path> pomFiles = findMatchingFiles("pom.xml");
                HashSet<Path> gradleFiles = findMatchingFiles("build.gradle");

                for (Path pomFile : pomFiles) {
                    try {
                        final SourceUnit unit = new SourceUnit();

                        // Add POMFile so we can open the corresponding Maven project.
                        unit.Data.put("POMFile", pomFile.toString());

                        BuildAnalysis.POMAttrs attrs = unit.getPOMAttrs();
                        unit.Type = "JavaArtifact";
                        unit.Name = attrs.groupID + "/" + attrs.artifactID;
                        unit.Dir = pomFile.getParent().toString();
                        unit.Data.put("Description", attrs.description);

                        // TODO: Java source files can be other places './src'
                        unit.Files = scanFiles(pomFile.getParent().resolve("src"));

                        // Sort for test consistency.
                        unit.Files.sort((String a, String b) -> a.compareTo(b));

                        // This will list all dependencies, not just direct ones.
                        unit.Dependencies = new ArrayList<>(unit.getRawPOMDependencies());
                        units.add(unit);
                    } catch (Exception e) {
                        System.err.println("Error processing pom file " + pomFile + ": " + e.toString());
                    }
                }
                for (Path gradleFile : gradleFiles) {
                    try {
                        BuildAnalysis.POMAttrs attrs = getGradleAttrs(repoURI, gradleFile);

                        final SourceUnit unit = new SourceUnit();
                        unit.Type = "JavaArtifact";
                        unit.Name = attrs.groupID + "/" + attrs.artifactID;
                        unit.Dir = gradleFile.getParent().toString();
                        unit.Data.put("GradleFile", gradleFile.toString());
                        unit.Data.put("Description", attrs.description);

                        // TODO: Java source files can be other places besides ‘./src’
                        unit.Files = scanFiles(gradleFile.getParent().resolve("src"));

                        // We need consistent output ordering for testing purposes.
                        unit.Files.sort((String a, String b) -> a.compareTo(b));

                        // This will list all dependencies, not just direct ones.
                        unit.Dependencies = new ArrayList<>(getGradleDependencies(gradleFile));
                        units.add(unit);
                    } catch (Exception e) {
                        System.err.println("Error processing gradle file " + gradleFile + ": " + e.toString());
                    }

                }
            }

            Gson gson = new GsonBuilder().serializeNulls().create();
            System.out.println(gson.toJson(units));
        } catch (Exception e) {
            System.err.println("Uncaught error: " + e.toString());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
