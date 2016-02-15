package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Extracts build meta information from source unit build file (pom.xml or .gradle)
 */
public class BuildAnalysis {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildAnalysis.class);

    public static final String DEFAULT_GROUP_ID = "default-group";

    /**
     * POM attributes, holds group ID, artifact ID, and description
     */
    public static class POMAttrs {
        public String groupID = DEFAULT_GROUP_ID;
        public String artifactID = StringUtils.EMPTY;
        public String description = StringUtils.EMPTY;

        public POMAttrs() {
        }

        public POMAttrs(String g, String a, String d) {
            groupID = g;
            artifactID = a;
            description = d;
        }

        public static String groupId(String groupId) {
            return StringUtils.defaultIfEmpty(groupId, DEFAULT_GROUP_ID);
        }
    }

    /**
     * Project's dependency (reference to another sub-project or module that produces artifact A in group G by build file B)
     */
    public static class ProjectDependency {
        public String groupID;
        public String artifactID;
        public String buildFile;

        /**
         * Constructs new project dependency
         *
         * @param groupID    group ID
         * @param artifactID artifact ID
         * @param buildFile  sub-project's or module's build file used to build artifact, may ne null
         */
        public ProjectDependency(String groupID, String artifactID, String buildFile) {
            this.groupID = groupID;
            this.artifactID = artifactID;
            if (buildFile == null) {
                buildFile = StringUtils.EMPTY;
            }
            this.buildFile = buildFile;
        }

        @Override
        public int hashCode() {
            int result = groupID.hashCode() * 31;
            result = result * 31 + artifactID.hashCode();
            result = result * 31 + buildFile.hashCode();
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof ProjectDependency)) {
                return false;
            }
            ProjectDependency projectDependency = (ProjectDependency) o;
            return groupID.equals(projectDependency.groupID) &&
                    artifactID.equals(projectDependency.artifactID) &&
                    buildFile.equals(projectDependency.buildFile);
        }

    }

    /**
     * Raw information about source unit, we later transforming it into SourceUnit objects
     */
    public static class BuildInfo {
        /**
         * Artifact version
         */
        public String version = StringUtils.EMPTY;
        /**
         * POM attributes
         */
        public POMAttrs attrs;
        /**
         * List of dependencies (from external artifacts)
         */
        public Collection<RawDependency> dependencies;

        /**
         * List of source files
         */
        public Collection<String> sources;

        /**
         * List of source directories
         */
        public Collection<String[]> sourceDirs; // contains triplets: source unit name, source unit version, directory

        /**
         * Classpath used to compile module
         */
        public Collection<String> classPath;

        /**
         * Bootstrap classpath used to compile module
         */
        public Collection<String> bootClassPath;

        /**
         * Source code version (language level)
         */
        public String sourceVersion = Project.DEFAULT_SOURCE_CODE_VERSION;
        /**
         * Source code encoding
         */
        public String sourceEncoding;
        /**
         * Module or sub-project directory
         */
        public String projectDir;
        /**
         * Root project directory
         */
        public String rootDir;
        /**
         * Location of build file used to build project
         */
        public String buildFile;
        /**
         * Project dependencies (references to another sub-projects or modules in the same repo that produce artifacts
         * current module depends on)
         */
        public Collection<ProjectDependency> projectDependencies;
        /**
         * Android SDK version
         */
        public String androidSdk;


        public BuildInfo() {
            attrs = new POMAttrs();
            dependencies = new HashSet<>();
            sources = new HashSet<>();
            sourceDirs = new ArrayList<>();
            classPath = new HashSet<>();
            bootClassPath = new HashSet<>();
            projectDependencies = new HashSet<>();
        }

        public String getName() {
            return attrs.groupID + '/' + attrs.artifactID;
        }
    }

    /**
     * Extracts meta information from Gradle file by running gradle command and passing special init script to it
     */
    public static class Gradle {

        /**
         * Gradle tasks to collect meta information
         */
        private static final String TASK_CODE_RESOURCE = "/metainfo.gradle";

        private static final String GRADLE_CMD_WINDOWS = "gradle.bat";
        private static final String GRADLE_CMD_OTHER = "gradle";

        private static final String REPO_DIR = ".gradle-srclib";

        /**
         * Collects meta information from a gradle build file
         *
         * @param wrapper gradle command (gradlew, gradlew.bat, gradle, gradle.bat)
         * @param build   path to build file or directory
         * @return list of build info objects extracted from Gradle file. Returns empty list if no source units were
         * found or gradle command failed
         * @throws IOException
         */
        public static BuildInfo[] collectMetaInformation(Path wrapper, Path build) throws IOException {
            Path modifiedGradleScriptFile = Files.createTempFile("srclib-collect-meta", "gradle");
            Path gradleCacheDir = Files.createTempDirectory("gradle-cache");

            try {
                InputStream inputStream = Gradle.class.getResourceAsStream(TASK_CODE_RESOURCE);
                OutputStream outputStream = new FileOutputStream(modifiedGradleScriptFile.toString(), false);
                try {
                    IOUtils.copy(inputStream, outputStream);
                } finally {
                    IOUtils.closeQuietly(inputStream);
                    IOUtils.closeQuietly(outputStream);
                }

                String wrapperPath = "INTERNAL_ERROR";
                if (wrapper != null) {
                    wrapperPath = wrapper.toAbsolutePath().toString();
                }

                List<String> gradleArgs = new ArrayList<>();
                gradleArgs.add("--gradle-user-home");
                gradleArgs.add(getGradleUserHome());
                gradleArgs.add("-I");
                gradleArgs.add(modifiedGradleScriptFile.toString());
                // TODO (alexsaveliev) restore special handling of Android Support framework
                // if (!GradleProject.isAndroidSupport(unit)) {
                // alexsaveliev: Android Support framework comes with gradle wrapper that defines own project-cache-dir
                gradleArgs.add("--project-cache-dir");
                gradleArgs.add(gradleCacheDir.toString());
                //}
                // disabling parallel builds
                gradleArgs.add("-Dorg.gradle.parallel=false");
                gradleArgs.add("srclibCollectMetaInformation");

                if (SystemUtils.IS_OS_WINDOWS) {
                    if (wrapper == null) {
                        gradleArgs.add(0, GRADLE_CMD_WINDOWS);
                    } else {
                        gradleArgs.add(0, wrapperPath);
                    }
                } else {
                    if (wrapper == null) {
                        gradleArgs.add(0, GRADLE_CMD_OTHER);
                    } else {
                        gradleArgs.add(0, wrapperPath);
                        gradleArgs.add(0, "bash");
                    }
                }

                Path workDir = build.toAbsolutePath().getParent();
                ProcessBuilder pb = new ProcessBuilder(gradleArgs);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Running {} using working directory {}",
                            StringUtils.join(gradleArgs, ' '),
                            workDir.normalize());
                }

                pb.directory(new File(workDir.toString()));
                pb.redirectErrorStream(true);
                BufferedReader in = null;
                Collection<BuildInfo> results = new ArrayList<>();
                BuildInfo info = null;

                try {
                    Process process = pb.start();
                    in = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    StringBuilder output = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        if ("BUILD FAILED".equals(line)) {
                            LOGGER.error("Failed to process {} - gradle build failed. Output was: {}", build, output);
                            results.clear();
                            break;
                        }
                        String meta[] = parseMeta(line);
                        if (meta == null) {
                            LOGGER.debug("gradle: {}", line);
                            continue;
                        }
                        String prefix = meta[0];
                        String payload = meta[1];
                        switch (prefix) {
                            case "SRCLIB-ARTIFACT":
                                info = new BuildInfo();
                                results.add(info);
                                info.attrs.artifactID = payload;
                                break;
                            case "SRCLIB-GROUP":
                                if (info == null) {
                                    continue;
                                }
                                info.attrs.groupID = POMAttrs.groupId(payload);
                                break;
                            case "SRCLIB-DEPENDENCY":
                                if (info == null) {
                                    continue;
                                }
                                String[] parts = payload.split(":", 5);
                                info.dependencies.add(new RawDependency(
                                        POMAttrs.groupId(parts[1]), // GroupID
                                        parts[2], // ArtifactID
                                        parts[3], // Version
                                        parts[0], // Scope
                                        parts.length > 4 ? parts[4] : null // file
                                ));
                                break;
                            case "SRCLIB-DESCRIPTION":
                                if (info == null) {
                                    continue;
                                }
                                info.attrs.description = payload;
                                break;
                            case "SRCLIB-VERSION":
                                if (info == null) {
                                    continue;
                                }
                                info.version = payload;
                                break;
                            case "SRCLIB-CLASSPATH":
                                if (info == null) {
                                    continue;
                                }
                                for (String path : payload.split(SystemUtils.PATH_SEPARATOR)) {
                                    if (!StringUtils.isEmpty(path)) {
                                        info.classPath.add(path);
                                    }
                                }
                                break;
                            case "SRCLIB-BOOTCLASSPATH":
                                if (info == null) {
                                    continue;
                                }
                                for (String path : payload.split(SystemUtils.PATH_SEPARATOR)) {
                                    if (!StringUtils.isEmpty(path)) {
                                        info.bootClassPath.add(path);
                                    }
                                }
                                break;
                            case "SRCLIB-SOURCEFILE":
                                if (info == null) {
                                    continue;
                                }
                                File file = PathUtil.CWD.resolve(payload).toFile();
                                if (file.isFile()) {
                                    info.sources.add(file.getAbsolutePath());
                                }
                                break;
                            case "SRCLIB-SOURCEDIR":
                                if (info == null) {
                                    continue;
                                }
                                String tokens[] = payload.split(":", 4);
                                String unitName = POMAttrs.groupId(tokens[0]) + '/' + tokens[1];
                                info.sourceDirs.add(new String[]{unitName, tokens[2], tokens[3]});
                                break;
                            case "SRCLIB-SOURCEVERSION":
                                if (info == null) {
                                    continue;
                                }
                                if (info.sourceVersion == null || info.sourceVersion.compareTo(payload) < 0) {
                                    info.sourceVersion = payload;
                                }
                                break;
                            case "SRCLIB-PROJECTDIR":
                                if (info == null) {
                                    continue;
                                }
                                info.projectDir = payload;
                                break;
                            case "SRCLIB-ROOTDIR":
                                if (info == null) {
                                    continue;
                                }
                                info.rootDir = payload;
                                break;
                            case "SRCLIB-ENCODING":
                                if (info == null) {
                                    continue;
                                }
                                info.sourceEncoding = payload;
                                break;
                            case "SRCLIB-PROJECTDEPENDENCY":
                                if (info == null) {
                                    continue;
                                }
                                String depTokens[] = payload.split(":", 3);
                                info.projectDependencies.add(new ProjectDependency(POMAttrs.groupId(depTokens[0]),
                                        depTokens[1],
                                        depTokens[2]));
                                break;
                            case "SRCLIB-GRADLEFILE":
                                if (info == null) {
                                    continue;
                                }
                                info.buildFile = payload;
                                break;
                            case "SRCLIB-ANDROID-SDK":
                                if (info == null) {
                                    continue;
                                }
                                info.androidSdk = payload;
                                break;
                            case "SRCLIB-WARNING":
                                LOGGER.warn("gradle: {}", payload);
                                break;
                            default:
                                LOGGER.debug("gradle: {}", line);
                                output.append(line).append(IOUtils.LINE_SEPARATOR);
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(in);
                }

                return results.toArray(new BuildInfo[results.size()]);
            } finally {
                FileUtils.deleteDirectory(gradleCacheDir.toString());
                Files.deleteIfExists(modifiedGradleScriptFile);
            }
        }

        /**
         * @return Gradle user home to be used.
         * ~/.gradle-srclib
         */
        private static String getGradleUserHome() {
            return new File(PathUtil.CWD.toFile(), REPO_DIR).getAbsolutePath();
        }

        /**
         * Parses metadata line, expected format is PREFIX-SPACE-CONTENT
         *
         * @param line line to parse
         * @return two-elements array, where first item is a prefix and second item is content
         */
        private static String[] parseMeta(String line) {
            int idx = line.indexOf(' ');
            if (-1 == idx)
                return null;
            return new String[]{line.substring(0, idx), line.substring(idx + 1).trim()};
        }

    }
}
