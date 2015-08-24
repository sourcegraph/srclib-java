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

public class BuildAnalysis {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildAnalysis.class);

    public static class POMAttrs {
        public String groupID = "default-group";
        public String artifactID = StringUtils.EMPTY;
        public String description = StringUtils.EMPTY;

        public POMAttrs() {
        }

        public POMAttrs(String g, String a, String d) {
            groupID = g;
            artifactID = a;
            description = d;
        }
    }

    public static class ProjectDependency {
        public String groupID;
        public String artifactID;
        public String buildFile;

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

    public static class BuildInfo {
        public String version = StringUtils.EMPTY;
        public POMAttrs attrs;
        public Collection<RawDependency> dependencies;
        public Collection<String> sources;
        public Collection<String[]> sourceDirs; // contains triplets: source unit name, source unit version, directory
        public Collection<String> classPath;
        public Collection<String> bootClassPath;
        public String sourceVersion = Project.DEFAULT_SOURCE_CODE_VERSION;
        public String sourceEncoding;
        public String projectDir;
        public String rootDir;
        public String buildFile;
        public Collection<ProjectDependency> projectDependencies;
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

    public static class Gradle {

        /**
         * Gradle tasks to collect meta information
         */
        private static final String TASK_CODE_RESOURCE = "/metainfo.gradle";

        private static final String GRADLE_CMD_WINDOWS = "gradle.bat";
        private static final String GRADLE_CMD_OTHER = "gradle";

        private static final String REPO_DIR = ".gradle-srclib";

        public static BuildInfo[] collectMetaInformation(String repoUri, Path wrapper, Path build) throws IOException {
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
                gradleArgs.add(new File(SystemUtils.getUserDir(), REPO_DIR).getAbsolutePath());
                gradleArgs.add("-I");
                gradleArgs.add(modifiedGradleScriptFile.toString());
                if (!ScanCommand.ANDROID_SUPPORT_FRAMEWORK_REPO.equals(repoUri)) {
                    // alexsaveliev: Android Support framework comes with gradle wrapper that defines own project-cache-dir
                    gradleArgs.add("--project-cache-dir");
                    gradleArgs.add(gradleCacheDir.toString());
                }
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
                    while ((line = in.readLine()) != null) {
                        if ("BUILD FAILED".equals(line)) {
                            LOGGER.error("Failed to process {} - gradle build failed", build);
                            results.clear();
                            break;
                        }
                        String meta[] = parseMeta(line);
                        if (meta == null) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("gradle: {}", line);
                            }
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
                                if (!StringUtils.isEmpty(payload)) {
                                    info.attrs.groupID = payload;
                                }
                                break;
                            case "SRCLIB-DEPENDENCY":
                                if (info == null) {
                                    continue;
                                }
                                String[] parts = payload.split(":", 5);
                                info.dependencies.add(new RawDependency(
                                        parts[1], // GroupID
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
                                File file = new File(payload);
                                if (file.isFile()) {
                                    info.sources.add(file.getAbsolutePath());
                                }
                                break;
                            case "SRCLIB-SOURCEDIR":
                                if (info == null) {
                                    continue;
                                }
                                String tokens[] = payload.split(":", 4);
                                String unitName = tokens[0] + '/' + tokens[1];
                                info.sourceDirs.add(new String[] {unitName, tokens[2], tokens[3]});
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
                                info.projectDependencies.add(new ProjectDependency(depTokens[0],
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
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("gradle: {}", line);
                                }
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
         * Parses metadata line, expected format is PREFIX-SPACE-CONTENT
         * @param line line to parse
         * @return two-elements array, where first item is a prefix and second item is content
         */
        private static String[] parseMeta(String line) {
            int idx = line.indexOf(' ');
            if (-1 == idx)
                return null;
            return new String[] {line.substring(0, idx), line.substring(idx + 1).trim()};
        }

    }
}
