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
import java.util.*;
import java.util.stream.Stream;

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
        public String sourceVersion = Project.DEFAULT_SOURCE_CODE_VERSION;
        public String sourceEncoding;
        public String projectDir;
        public String rootDir;
        public String buildFile;
        public Collection<ProjectDependency> projectDependencies;


        public BuildInfo() {
            attrs = new POMAttrs();
            dependencies = new HashSet<>();
            sources = new HashSet<>();
            sourceDirs = new ArrayList<>();
            classPath = new HashSet<>();
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

                String[] gradleArgs = new String[]{
                        "--gradle-user-home", new File(SystemUtils.getUserDir(), REPO_DIR).getAbsolutePath(),
                        "-I", modifiedGradleScriptFile.toString(),
                        "--project-cache-dir", gradleCacheDir.toString(),
                        "srclibCollectMetaInformation"};
                String gradleCmd[];

                if (SystemUtils.IS_OS_WINDOWS) {
                    if (wrapper == null) {
                        gradleCmd = new String[] {GRADLE_CMD_WINDOWS};
                    } else {
                        gradleCmd = new String[] {wrapperPath};
                    }
                } else {
                    if (wrapper == null) {
                        gradleCmd = new String[] {GRADLE_CMD_OTHER};
                    } else {
                        gradleCmd = new String[] {"bash", wrapperPath};
                    }
                }

                String[] cmd = Stream.concat(Arrays.stream(gradleCmd), Arrays.stream(gradleArgs))
                        .toArray(String[]::new);

                Path workDir = build.toAbsolutePath().getParent();
                ProcessBuilder pb = new ProcessBuilder(cmd);

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Running {} using working directory {}",
                            StringUtils.join(cmd, ' '),
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
                            case "ARTIFACT":
                                info = new BuildInfo();
                                results.add(info);
                                info.attrs.artifactID = payload;
                                break;
                            case "GROUP":
                                if (info == null) {
                                    continue;
                                }
                                if (!StringUtils.isEmpty(payload)) {
                                    info.attrs.groupID = payload;
                                }
                                break;
                            case "DEPENDENCY":
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
                            case "DESCRIPTION":
                                if (info == null) {
                                    continue;
                                }
                                info.attrs.description = payload;
                                break;
                            case "VERSION":
                                if (info == null) {
                                    continue;
                                }
                                info.version = payload;
                                break;
                            case "CLASSPATH":
                                if (info == null) {
                                    continue;
                                }
                                for (String path : payload.split(SystemUtils.PATH_SEPARATOR)) {
                                    if (!StringUtils.isEmpty(path)) {
                                        info.classPath.add(path);
                                    }
                                }
                                break;
                            case "SOURCEFILE":
                                if (info == null) {
                                    continue;
                                }
                                info.sources.add(payload);
                                break;
                            case "SOURCEDIR":
                                if (info == null) {
                                    continue;
                                }
                                String tokens[] = payload.split(":", 4);
                                String unitName = tokens[0] + '/' + tokens[1];
                                info.sourceDirs.add(new String[] {unitName, tokens[2], tokens[3]});
                                break;
                            case "SOURCEVERSION":
                                if (info == null) {
                                    continue;
                                }
                                if (info.sourceVersion == null || info.sourceVersion.compareTo(payload) < 0) {
                                    info.sourceVersion = payload;
                                }
                                break;
                            case "PROJECTDIR":
                                if (info == null) {
                                    continue;
                                }
                                info.projectDir = payload;
                                break;
                            case "ROOTDIR":
                                if (info == null) {
                                    continue;
                                }
                                info.rootDir = payload;
                                break;
                            case "ENCODING":
                                if (info == null) {
                                    continue;
                                }
                                info.sourceEncoding = payload;
                                break;
                            case "PROJECTDEPENDENCY":
                                if (info == null) {
                                    continue;
                                }
                                String depTokens[] = payload.split(":", 3);
                                info.projectDependencies.add(new ProjectDependency(depTokens[0],
                                        depTokens[1],
                                        depTokens[2]));
                                break;
                            case "GRADLEFILE":
                                if (info == null) {
                                    continue;
                                }
                                info.buildFile = payload;
                                break;
                            case "WARNING":
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
