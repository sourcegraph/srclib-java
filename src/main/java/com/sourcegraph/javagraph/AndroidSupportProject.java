package com.sourcegraph.javagraph;

import org.apache.maven.model.building.ModelBuildingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Set of rules to compile Android's base framework (https://android.googlesource.com/platform/frameworks/support)
 * - libart as bootstrap classpath
 */
class AndroidSupportProject implements Project {

    static final String MARKER = "AndroidSupport";

    AndroidSupportProject(SourceUnit unit) {
    }

    /**
     * @return bootclasspath to compile Android base framework (should include libcore classes)
     */
    @Override
    public List<String> getBootClassPath() {
        return getLibraries(new String[]{
                "../../out/target/common/obj/JAVA_LIBRARIES/core-all_intermediates/classes.jar",
                "sdk/android-sdk.jar" // this entry is for testing purposes only, see sgtest/java-android-support-framework
        });
    }

    /**
     *
     * @return classpath to use (include framework's output, junit)
     */
    @Override
    public List<String> getClassPath() {
        return getLibraries(new String[]{
                "../../out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/classes.jar"
        });
    }

    /**
     * @return source directories that contain files generated from .logtags and
     * directories that contain R.java and Manifest.java
     */
    @Override
    public List<String> getSourcePath() throws IOException {
        List<String> sourcePath = new ArrayList<>();
        // include java directories generated from .logtags and AIDL and the ones containing R.java
        File intermediate = new File("../../out/target/common/obj/JAVA_LIBRARIES");
        if (intermediate.isDirectory()) {
            Files.walkFileTree(intermediate.toPath(), new GeneratedDirectoriesCollector(sourcePath));
        }
        return sourcePath;
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
        return null;
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
        unit.Files = new ArrayList<>();
        Files.walkFileTree(PathUtil.CWD, new SourceFilesCollector(unit.Files));
        unit.Data.Type = MARKER;
        return unit;
    }

    public static boolean is(SourceUnit unit) {
        return MARKER.equals(unit.Data.Type);
    }

    /**
     * @param files files to search for
     * @return list of existing files resolved relative to current working directory
     */
    private List<String> getLibraries(String files[]) {
        List<String> ret = Arrays.stream(files).filter(s -> new File(s).isFile()).collect(Collectors.toList());
        return ret.isEmpty() ? null : ret;
    }

    /**
     * Walks file tree and collects source directories which contain generated java files if there are any
     */
    private static final class GeneratedDirectoriesCollector extends SimpleFileVisitor<Path> {

        private Collection<String> dirs;

        GeneratedDirectoriesCollector(Collection<String> dirs) {
            this.dirs = dirs;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String name = dir.getFileName().toString();
            if (name.equals("src")) {
                // special case: src/java
                if (!new File(dir.toFile(), "java").isDirectory()) {
                    dirs.add(dir.toAbsolutePath().normalize().toString());
                    return FileVisitResult.SKIP_SUBTREE;
                }
            } else if (name.equals("java")) {
                dirs.add(dir.toAbsolutePath().normalize().toString());
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Walks file tree and collects source files for Android Support.
     * Excludes tests, "customtabs", "previewsdk", and "percent" folders
     */
    private static final class SourceFilesCollector extends SimpleFileVisitor<Path> {

        private Collection<String> files;

        SourceFilesCollector(Collection<String> files) {
            this.files = files;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String name = dir.getFileName().toString();
            if (name.equals("test") ||
                    name.equals("tests") ||
                    name.equals("percent") ||
                    name.equals("jvm-tests") ||
                    name.equals("customtabs") ||
                    name.equals("androidTest") ||
                    name.equals("previewsdk") ||
                    name.equals("static") ||
                    name.equals("animated")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            String filename = file.toString();
            if (filename.endsWith(".java")) {
                files.add(PathUtil.relativizeCwd(file));
            }
            return FileVisitResult.CONTINUE;
        }
    }

}
