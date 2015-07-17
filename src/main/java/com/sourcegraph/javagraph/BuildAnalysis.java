package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Stream;

public class BuildAnalysis {

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

    public static class BuildInfo {
        public String version = StringUtils.EMPTY;
        public POMAttrs attrs;
        public HashSet<RawDependency> dependencies;
        public HashSet<String> sources;
        public HashSet<String> classPath;

        public BuildInfo() {
            attrs = new POMAttrs();
            dependencies = new HashSet<>();
            sources = new HashSet<>();
            classPath = new HashSet<>();
        }
    }

    public static class Gradle {

        /**
         * Gradle tasks to collect meta information
         */
        private static final String TASK_CODE_RESOURCE = "/metainfo.gradle";

        private static final String GRADLE_CMD_WINDOWS = "gradle.bat";
        private static final String GRADLE_CMD_OTHER = "gradle";

        public static BuildInfo collectMetaInformation(Path wrapper, Path build) throws IOException {
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

                String[] gradleArgs = new String[]{"-I", modifiedGradleScriptFile.toString(), "--project-cache-dir",
                        gradleCacheDir.toString(), "srclibCollectMetaInformation"};
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

                pb.directory(new File(workDir.toString()));
                BufferedReader in = null;
                BuildInfo result = new BuildInfo();
                result.attrs.artifactID = workDir.getFileName().toString();

                try {
                    Process process = pb.start();
                    in = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    String line;
                    while ((line = in.readLine()) != null) {
                        String meta[] = parseMeta(line);
                        if (meta == null) {
                            continue;
                        }
                        String prefix = meta[0];
                        String payload = meta[1];
                        switch (prefix) {
                            case "GROUP":
                                result.attrs.groupID = payload;
                                break;
                            case "DEPENDENCY":
                                String[] parts = payload.split(":");
                                result.dependencies.add(new RawDependency(
                                        parts[1], // GroupID
                                        parts[2], // ArtifactID
                                        parts[3], // Version
                                        parts[0] // Scope
                                ));
                                break;
                            case "ARTIFACT":
                                result.attrs.artifactID = payload;
                                break;
                            case "DESCRIPTION":
                                result.attrs.description = payload;
                                break;
                            case "VERSION":
                                result.version = payload;
                                break;
                            case "CLASSPATH":
                                for (String path: payload.split(SystemUtils.PATH_SEPARATOR)) {
                                    if (!StringUtils.isEmpty(path))
                                    result.classPath.add(path);
                                }
                                break;
                            case "SOURCEFILE":
                                result.sources.add(payload);
                                break;
                        }
                    }
                } finally {
                    IOUtils.closeQuietly(in);
                }

                return result;
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
            line = line.trim();
            int idx = line.indexOf(' ');
            if (-1 == idx)
                return null;
            return new String[] {line.substring(0, idx), line.substring(idx + 1).trim()};
        }

    }
}
