package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.building.ModelBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class AndroidSDKProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidSDKProject.class);

    public AndroidSDKProject(SourceUnit unit) {
    }

    @Override
    public List<String> getBootClassPath() throws Exception {
        return null;
    }

    @Override
    public List<String> getClassPath() throws Exception {
        return null;
    }

    @Override
    public List<String> getSourcePath() throws Exception {
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
        return null;
    }

    public static SourceUnit createSourceUnit(String subdir) throws Exception {


        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = "AndroidSDK";
        unit.Dir = subdir;
        unit.Files = getSourceFiles(Paths.get(subdir));
        unit.Data.put("AndroidSDKSubdir", subdir);
        return unit;
    }

    private static List<String> getSourceFiles(Path root) throws IOException {

        if (!Files.exists(root)) {
            LOGGER.warn("{} does not exist, skipping", root);
            return Collections.emptyList();
        }

        processAidlFiles(root);

        List<String> files = new LinkedList<>();
        Files.walkFileTree(root, new AndroidSdkFileVisitor(files, ".java"));
        return files;
    }

    private static String getAidlCommand() {
        File buildToolsDir = getLatestBuildToolsDir();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Using Android SDK build tools {}", buildToolsDir);
        }
        if (buildToolsDir == null) {
            return null;
        }
        File aidl;
        if (SystemUtils.IS_OS_WINDOWS) {
            aidl = new File(buildToolsDir, "aidl.exe");
        } else {
            aidl = new File(buildToolsDir, "aidl");
        }
        if (aidl.isFile() && aidl.canExecute()) {
            return aidl.getAbsolutePath();
        }
        return null;
    }

    private static File getLatestBuildToolsDir() {
        File sdkHome = getAndroidSdkHome();
        if (sdkHome == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Android SDK home is not found");
            }
            return null;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Using Android SDK home {}", sdkHome);
        }
        File buildTools = new File(sdkHome, "build-tools");
        if (!buildTools.isDirectory()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Android SDK build tools {} does not exist or not a directory", buildTools);
            }
            return null;

        }
        File tools[] = buildTools.listFiles(File::isDirectory);
        Comparator<File> comparator = Comparator.comparing(File::toString);
        Arrays.sort(tools, comparator.reversed());
        if (tools.length > 0) {
            return tools[0];
        }
        return null;
    }

    private static File getAndroidSdkHome() {
        String env = System.getenv("ANDROID_SDK_HOME");
        if (env != null) {
            File home = new File(env);
            if (home.isDirectory()) {
                return home;
            }
        }
        env = System.getenv("ANDROID_HOME");
        if (env != null) {
            File home = new File(env);
            if (home.isDirectory()) {
                return home;
            }
        }
        return null;
    }

    private static void processAidlFiles(Path root) throws IOException {
        String aidlCommand = getAidlCommand();
        if (aidlCommand == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("aidl command is not found");
            }
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Using aidl command {}", aidlCommand);
        }

        List<String> aidlFiles = new LinkedList<>();
        Files.walkFileTree(root, new AndroidSdkFileVisitor(aidlFiles, ".aidl"));
        // collect include locations
        Collection<String> includes = new HashSet<>();
        for (String aidlFile : aidlFiles) {
            includes.add("-I" + getAidlWorkingDir(Paths.get(aidlFile)).getAbsoluteFile().toString());
        }

        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(aidlCommand);
        cmdArgs.addAll(includes);

        for (String aidlFile : aidlFiles) {
            processAidlFile(cmdArgs, aidlFile);
        }
    }

    private static void processAidlFile(List<String> cmdArgs,
                                        String aidlFile) {
        File source = new File(aidlFile);
        File target = new File(source.getParentFile(),
                source.getName().substring(0, source.getName().lastIndexOf(".")) + ".java");
        if (target.exists()) {
            return;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Processing AIDL file {}", source);
        }
        ProcessBuilder pb = new ProcessBuilder();
        File workingDir = getAidlWorkingDir(source.toPath()).getAbsoluteFile();
        List<String> args = new ArrayList<>(cmdArgs);
        args.add(getAidlArgument(source.toPath()));
        pb.command(args);
        pb.redirectErrorStream(true);
        pb.directory(workingDir);

        try {
            Process process = pb.start();
            int status = process.waitFor();
            if (status != 0) {
                try (InputStream is = process.getInputStream()) {
                    String message = IOUtils.toString(is);
                    LOGGER.warn("Unable to process AIDL file {} - exit status {}, output was: {}", source, status, message);
                }
            }
        } catch (IOException | InterruptedException ex) {
            LOGGER.warn("Unable to process AIDL file {}", source, ex);
        }
    }

    private static File getAidlWorkingDir(Path p) {
        // climbing up to "java" level
        int count = p.getNameCount();
        for (int i = count - 1; i >= 0; i--) {
            if (p.getName(i).toString().equals("java")) {
                return p.subpath(0, i + 1).toFile();
            }
        }
        return p.getParent().toFile();
    }

    private static String getAidlArgument(Path p) {
        // climbing up to "java" level
        int count = p.getNameCount();
        for (int i = count - 1; i >= 0; i--) {
            if (p.getName(i).toString().equals("java")) {
                return p.subpath(i + 1, count).toString();
            }
        }
        return p.getFileName().toString();
    }


    private static final class AndroidSdkFileVisitor extends SimpleFileVisitor<Path> {

        private Collection<String> files;
        private String extension;

        AndroidSdkFileVisitor(Collection<String> files, String extension) {
            this.files = files;
            this.extension = extension;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (dir.endsWith("src/test") ||
                    dir.endsWith("testrunner-src") ||
                    dir.endsWith("tests") ||
                    dir.endsWith("layoutlib/bridge")) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String filename = file.toString();
            if (filename.endsWith(extension)) {
                filename = PathUtil.normalize(filename);
                if (filename.startsWith("./"))
                    filename = filename.substring(2);
                files.add(filename);
            }
            return FileVisitResult.CONTINUE;
        }
    }
}
