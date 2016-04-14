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
class AndroidCoreProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidCoreProject.class);

    private static final String MARKER = "AndroidCore";

    AndroidCoreProject(SourceUnit unit) {
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
        File icuDir = new File("../external/icu/android_icu4j/src/main/java");
        if (icuDir.isDirectory()) {
            return Collections.singletonList(icuDir.getPath());
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
     * @throws IOException
     */
    static SourceUnit createSourceUnit() throws IOException {
        final SourceUnit unit = new SourceUnit();
        unit.Type = SourceUnit.DEFAULT_TYPE;
        unit.Name = MARKER;
        unit.Dir = ".";

        List<String> files = new LinkedList<>();

        getSourceFilesAndDirectories(PathUtil.CWD.resolve(unit.Dir), files);
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
     * @return list of files collected
     * @throws IOException
     */
    private static List<String> getSourceFilesAndDirectories(Path root, List<String> files)
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
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filename = file.toString();
                    if (filename.endsWith(".java")) {
                        filename = PathUtil.normalize(filename);
                        if (filename.startsWith("./"))
                            filename = filename.substring(2);
                        files.add(PathUtil.relativizeCwd(filename));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            LOGGER.warn("{} does not exist, skipping", root);
        }
        return files;
    }

}
