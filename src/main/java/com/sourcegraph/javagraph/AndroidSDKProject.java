package com.sourcegraph.javagraph;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.building.ModelBuildingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Set of rules to compile Android's base framework (https://android.googlesource.com/platform/frameworks/base)
 * - libart as bootstrap classpath
 * - include dependencies into classpath (conscrypt, okhttp, ext, bouncycastle, junit)
 * - include source directories generated from .logtags if found into source path
 * - include source directories that contain R.java and Manifest.java into source path, if found
 */
public class AndroidSDKProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidSDKProject.class);

    private static final String MARKER = "AndroidSDK";

    public AndroidSDKProject(SourceUnit unit) {
    }

    /**
     * @return libart
     */
    @Override
    public List<String> getBootClassPath() {
        return getLibraries(new String[]{
                "../../out/target/common/obj/JAVA_LIBRARIES/core-libart_intermediates/classes.jar"
        });
    }

    /**
     *
     * @return conscrypt, okhttp, ext, bouncycastle, junit
     */
    @Override
    public List<String> getClassPath() {
        // including
        return getLibraries(new String[]{
                "../../out/target/common/obj/JAVA_LIBRARIES/conscrypt_intermediates/classes.jar",
                "../../out/target/common/obj/JAVA_LIBRARIES/okhttp_intermediates/classes.jar",
                "../../out/target/common/obj/JAVA_LIBRARIES/ext_intermediates/classes.jar",
                "../../out/target/common/obj/JAVA_LIBRARIES/bouncycastle_intermediates/classes.jar",
                "../../out/target/common/obj/JAVA_LIBRARIES/core-junit_intermediates/classes.jar"
        });
    }

    /**
     * @return source directories that contain files generated from .logtags and
     * directories that contain R.java and Manifest.java
     */
    @Override
    public List<String> getSourcePath() throws IOException {
        List<String> sourcePath = new ArrayList<>();
        // needed to include java directories generated from .logtags
        File intermediate = new File("../../out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/src/core/java");
        if (intermediate.isDirectory()) {
            sourcePath.add(intermediate.toString());
        } else {
            LOGGER.warn("Directory that contains directories generated from .logtags does not exist, there might be some errors while graphing");
        }

        // Adding directories that contain auto-generated Manifest.java and R.java
        File rRoot = new File("../../out/target/common/R");
        if (rRoot.isDirectory()) {
            sourcePath.add(rRoot.toString());
        } else {
            LOGGER.warn("Directory that contains generated R.java and Manifest.java files does not exist, there might be some errors while graphing");
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
     * @throws Exception
     */
    public static SourceUnit createSourceUnit() throws Exception {


        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = MARKER;
        unit.Dir = ".";
        unit.Files = getSourceFiles(PathUtil.CWD.resolve("."));
        unit.Data.put(SourceUnit.TYPE, MARKER);
        return unit;
    }

    public static boolean is(SourceUnit unit) {
        return MARKER.equals(unit.Data.get(SourceUnit.TYPE));
    }

    /**
     * Collects source files.
     * If needed, generates .java files from .aidl files
     * @param root root directory
     * @return list of source files found
     * @throws IOException
     */
    private static List<String> getSourceFiles(Path root) throws IOException {

        if (!Files.exists(root)) {
            LOGGER.warn("{} does not exist, skipping", root);
            return Collections.emptyList();
        }

        processAidlFiles();
        return collectFiles("java");
    }

    /**
     * @return location of aidl command  to process .aidl files (aidl, aidl.exe)
     */
    private static String getAidlCommand() {
        File buildToolsDir = getLatestBuildToolsDir();
        LOGGER.debug("Using Android SDK build tools {}", buildToolsDir);
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

    /**
     * @return location of latest build tools directory
     */
    private static File getLatestBuildToolsDir() {
        // first let's check for a prebuilt tools dir
        if (SystemUtils.IS_OS_MAC_OSX) {
            File toolsDir = new File("../../prebuilts/sdk/tools/darwin");
            if (toolsDir.isDirectory()) {
                return toolsDir;
            }
        } else if (SystemUtils.IS_OS_UNIX) {
            File toolsDir = new File("../../prebuilts/sdk/tools/linux");
            if (toolsDir.isDirectory()) {
                return toolsDir;
            }
        }
        File sdkHome = getAndroidSdkHome();
        if (sdkHome == null) {
            LOGGER.debug("Android SDK home is not found");
            return null;
        }
        LOGGER.debug("Using Android SDK home {}", sdkHome);
        File buildTools = new File(sdkHome, "build-tools");
        if (!buildTools.isDirectory()) {
            LOGGER.debug("Android SDK build tools {} does not exist or not a directory", buildTools);
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

    /**
     * @return Android SDK home directory
     */
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

    /**
     * Generates .java files from .aidl files if needed
     * @throws IOException
     */
    private static void processAidlFiles() throws IOException {
        String aidlCommand = getAidlCommand();
        if (aidlCommand == null) {
            LOGGER.debug("aidl command is not found");
            return;
        }
        LOGGER.debug("Using aidl command {}", aidlCommand);

        List<String> aidlFiles = collectFiles("aidl");
        // collect include locations
        Collection<String> includes = aidlFiles.stream().
                map(aidlFile -> "-I" + getAidlWorkingDir(PathUtil.CWD.resolve(aidlFile)).
                        getAbsoluteFile().toString()).
                collect(Collectors.toSet());

        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.add(aidlCommand);
        cmdArgs.addAll(includes);

        for (String aidlFile : aidlFiles) {
            processAidlFile(cmdArgs, aidlFile);
        }
    }

    /**
     * Process single .aidl file
     * @param cmdArgs command line arguments to pass to aidl program
     * @param aidlFile file to process
     */
    private static void processAidlFile(List<String> cmdArgs,
                                        String aidlFile) {
        File source = new File(aidlFile);
        File target = new File(source.getParentFile(),
                source.getName().substring(0, source.getName().lastIndexOf(".")) + ".java");
        if (target.exists()) {
            return;
        }
        LOGGER.debug("Processing AIDL file {}", source);
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
            LOGGER.warn("Unable to process AIDL file {} by running command {} in working directory {}",
                    source,
                    pb.command(),
                    pb.directory(),
                    ex);
        }
    }

    /**
     * @param p path to .aidl file
     * @return working directory to process given aidl file, usually all components down to "java".
     * For example, if .aidl file is located in /foo/bar/baz/java/com/sourcegraph/file.aidl then working directory
     * should be /foo/bar/baz/java/
     */
    private static File getAidlWorkingDir(Path p) {
        // climbing up to "java" level
        int count = p.getNameCount();
        for (int i = count - 1; i >= 0; i--) {
            if (p.getName(i).toString().equals("java")) {
                return p.getRoot().resolve(p.subpath(0, i + 1)).toAbsolutePath().toFile();
            }
        }
        return p.getParent().toFile();
    }

    /**
     * @param p path to .aidl file
     * @return path to .aidl file relative to working directory, usually all components after "java".
     * For example, if .aidl file is located in /foo/bar/baz/java/com/sourcegraph/file.aidl then path
     * should be com/sourcegraph/file.aidl
     */
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

    /**
     * @param files files to search for
     * @return list of existing files resolved relative to current working directory
     */
    private List<String> getLibraries(String files[]) {
        return Arrays.stream(files).filter(s -> {
            if (new File(s).isFile()) {
                return true;
            }
            LOGGER.warn("Library {} does not exist, there might be some errors while graphing", s);
            return false;
        }).collect(Collectors.toList());
    }

    /**
     * Collects files in current working directory
     * @param extension extension to check for, for example "java"
     * @return list of found files in current working directory (including only needed to build framework project)
     */
    private static List<String> collectFiles(String extension) {
        List<String> files = new ArrayList<>();
        collectFiles(files, "core/java", extension);
        collectFiles(files, "drm/java", extension);
        collectFiles(files, "graphics/java", extension);
        collectFiles(files, "keystore/java", extension);
        collectFiles(files, "location/java", extension);
        collectFiles(files, "media/java", extension);
        collectFiles(files, "opengl/java", extension);
        collectFiles(files, "rs/java", extension);
        collectFiles(files, "sax/java", extension);
        collectFiles(files, "telecomm/java", extension);
        collectFiles(files, "telephony/java", extension);
        collectFiles(files, "wifi/java", extension);
        collectFiles(files, "packages/services/PacProcessor", extension);
        return files;
    }

    /**
     * Collects files in subdirectory of current working directory
     * @param files list to fill with found files
     * @param directory sub-path in current working directory to search files in
     * @param extension extension to check for, for example "java"
     */

    private static void collectFiles(Collection<String> files, String directory, String extension) {
        File root = new File(directory);
        if (root.isDirectory()) {
            files.addAll(FileUtils.listFiles(root, new String[]{extension}, true).
                    stream().
                    map(File::getAbsolutePath).
                    collect(Collectors.toList()));
        }
    }

}
