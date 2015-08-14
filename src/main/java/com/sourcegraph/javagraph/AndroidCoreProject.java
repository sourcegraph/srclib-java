package com.sourcegraph.javagraph;

import org.apache.commons.io.Charsets;
import org.apache.maven.model.building.ModelBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class AndroidCoreProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidCoreProject.class);

    public AndroidCoreProject(SourceUnit unit) {
    }

    @Override
    public List<String> getBootClassPath() throws Exception {
        return Collections.emptyList();
    }

    @Override
    public List<String> getClassPath() throws Exception {
        return null;
    }

    @Override
    public List<String> getSourcePath() throws Exception {
        // if there is ICU available, let's use it
        File icuDir = new File("../external/icu/icu4j/main/classes");
        if (icuDir.isDirectory()) {
            List<String> icuSrcDirs = new ArrayList<>();
            Files.walkFileTree(icuDir.toPath(), new ICUDirectoriesCollector(icuSrcDirs));
            return icuSrcDirs;
        }
        return null;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        return null;
    }

    @Override
    public String getSourceCodeVersion() throws Exception {
        return DEFAULT_SOURCE_CODE_VERSION;
    }

    @Override
    public String getSourceCodeEncoding() throws ModelBuildingException, IOException {
        return Charsets.UTF_8.name();
    }

    public static SourceUnit createSourceUnit(String subdir) throws Exception {
        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = "AndroidCore";
        unit.Dir = subdir;
        List<String> directories = new ArrayList<>();
        getSourceFilesAndDirectories(Paths.get(subdir), unit.Files, directories);
        unit.Data.put("AndroidCoreSubdir", subdir);
        return unit;
    }

    static List<String> getSourceFilesAndDirectories(Path root, List<String> files, List<String> directories)
            throws IOException {

        if (Files.exists(root)) {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.endsWith("src/test") ||
                            dir.endsWith("benchmarks") ||
                            dir.endsWith("tzdata")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    directories.add(PathUtil.normalize(dir.toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filename = file.toString();
                    if (filename.endsWith(".java")) {
                        filename = PathUtil.normalize(filename);
                        if (filename.startsWith("./"))
                            filename = filename.substring(2);
                        files.add(filename);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            LOGGER.warn("{} does not exist, skipping", root);
        }
        return files;
    }

    private static final class ICUDirectoriesCollector extends SimpleFileVisitor<Path> {

        private Collection<String> dirs;

        ICUDirectoriesCollector(Collection<String> dirs) {
            this.dirs = dirs;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (dir.getFileName().toString().equals("src")) {
                dirs.add(dir.toAbsolutePath().normalize().toString());
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
