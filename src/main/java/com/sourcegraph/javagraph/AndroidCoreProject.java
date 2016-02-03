package com.sourcegraph.javagraph;

import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Set of rules to compile Android's libcore (https://android.googlesource.com/platform/libcore/)
 * - no bootstrap classpath
 * - empty classpath
 * - include ICU4J classes if found into source path
 */
public class AndroidCoreProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidCoreProject.class);

    private static final String MARKER = "AndroidCore";

    public AndroidCoreProject(SourceUnit unit) {
    }

    /**
     * @return empty list because we are compiling JDK
     */
    @Override
    public List<String> getBootClassPath() {
        return Collections.emptyList();
    }

    /**
     * @return empty classpath
     */
    @Override
    public List<String> getClassPath() {
        return null;
    }

    /**
     * @return ICU4J directories if found, because libcore depends on ICU4J
     */
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
    public RawDependency getDepForJAR(Path jarFile) {
        return null;
    }

    /**
     * @return 1.8
     */
    @Override
    public String getSourceCodeVersion() {
        return DEFAULT_SOURCE_CODE_VERSION;
    }

    /**
     * @return "UTF-8"
     */
    @Override
    public String getSourceCodeEncoding() {
        return Charsets.UTF_8.name();
    }

    /**
     * Creates source unit from a given directory
     * @return source unit
     * @throws Exception
     */
    public static SourceUnit createSourceUnit() throws Exception {
        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = MARKER;
        unit.Dir = ".";

        List<String> files = new LinkedList<>();
        List<String> directories = new LinkedList<>();

        getSourceFilesAndDirectories(PathUtil.CWD.resolve(unit.Dir), files, directories);
        unit.Files = files;

        unit.Data.put(SourceUnit.TYPE, MARKER);
        return unit;
    }

    public static boolean is(SourceUnit unit) {
        return MARKER.equals(unit.Data.get(SourceUnit.TYPE));
    }

    /**
     * Walks file tree starting from specific root and collects java files and source directories of libcore,
     * excluding useless ones
     * @param root location to start walking filetree
     * @param files holder to keep found files
     * @param directories holder to keep found directories
     * @return list of files collected
     * @throws IOException
     */
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

    /**
     * Walks file tree and collects ICU4J source directories if there are any
     */
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
