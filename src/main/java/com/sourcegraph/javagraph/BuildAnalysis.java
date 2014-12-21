package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

public class BuildAnalysis {

    public static class POMAttrs {
        public String groupID = "default-group";
        public String artifactID = "";
        public String description = "";

        public POMAttrs() {
        }

        public POMAttrs(String g, String a, String d) {
            groupID = g;
            artifactID = a;
            description = d;
        }
    }

    ;

    public static class BuildInfo {
        public String classPath = "";
        public String version = "";
        public POMAttrs attrs;
        public HashSet<RawDependency> dependencies;

        public BuildInfo() {
            attrs = new POMAttrs();
            dependencies = new HashSet<RawDependency>();
        }

        public BuildInfo(POMAttrs a, String cp, String v, HashSet<RawDependency> deps) {
            attrs = a;
            classPath = cp;
            version = v;
            dependencies = deps;
        }
    }

    ;

    public static class Gradle {

        static String taskCode = "" + "allprojects {" + " task srclibCollectMetaInformation << {\n"
                + "  String classpath = ''\n" + "  if (project.plugins.hasPlugin('java')) {\n"
                + "   classpath = configurations.runtime.asPath\n" + "  }\n" + "\n"
                + "  String desc = project.description\n" + "  if (desc == null) { desc = \"\" }\n" + "\n"
                + "  println \"DESCRIPTION $desc\"\n" + "  println \"GROUP $project.group\"\n"
                + "  println \"VERSION $project.version\"\n" + "  println \"ARTIFACT $project.name\"\n"
                + "  println \"CLASSPATH $classpath\"\n" + "\n" + "  try {\n"
                + "   project.configurations.each { conf ->\n"
                + "    conf.resolvedConfiguration.getResolvedArtifacts().each {\n"
                + "     String group = it.moduleVersion.id.group\n" + "     String name = it.moduleVersion.id.name\n"
                + "     String version = it.moduleVersion.id.version\n" + "     String file = it.file\n"
                + "     println \"DEPENDENCY $conf.name:$group:$name:$version:$file\"\n" + "    }\n" + "   }\n"
                + "  }\n" + "  catch (Exception e) {}\n" + " }\n" + "}\n";

        private static String homedir = System.getProperty("user.home");

        public static String extractPayloadFromPrefixedLine(String prefix, String line) {
            int idx = line.indexOf(prefix);
            if (-1 == idx)
                return null;
            int offset = idx + prefix.length();
            return line.substring(offset).trim();
        }

        public static BuildInfo collectMetaInformation(Path wrapper, Path build) throws IOException {
            Path modifiedGradleScriptFile = Files.createTempFile("srclib-collect-meta", "gradle");
            Path gradleCacheDir = Files.createTempDirectory("gradle-cache");

            try {
                FileWriter fw = new FileWriter(modifiedGradleScriptFile.toString(), false);

                try {
                    fw.write(taskCode);
                } finally {
                    fw.close();
                }

                String[] prefix = {"DESCRIPTION", "GROUP", "VERSION", "ARTIFACT", "CLASSPATH", "DEPENDENCY"};

                String wrapperPath = "INTERNAL_ERROR";
                if (wrapper != null) {
                    wrapperPath = wrapper.toAbsolutePath().toString();
                }

                String[] gradlewArgs = {"bash", wrapperPath, "-I", modifiedGradleScriptFile.toString(),
                        "--project-cache-dir", gradleCacheDir.toString(), "srclibCollectMetaInformation"};

                String[] gradleArgs = {"gradle", "-I", modifiedGradleScriptFile.toString(), "--project-cache-dir",
                        gradleCacheDir.toString(), "srclibCollectMetaInformation"};

                String[] cmd = (wrapper == null) ? gradleArgs : gradlewArgs;
                Path workDir = build.toAbsolutePath().getParent();

                if (wrapper != null) {
                    System.err.println("Using gradle wrapper script:" + wrapper.toString());
                }

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.directory(new File(workDir.toString()));
                BufferedReader in = null;
                BuildInfo result = new BuildInfo();
                result.attrs.artifactID = workDir.normalize().toString();

                try {
                    Process process = pb.start();
                    in = new BufferedReader(new InputStreamReader(process.getInputStream()));

                    IOUtils.copy(process.getErrorStream(), System.err);

                    String line = null;
                    while ((line = in.readLine()) != null) {

                        String groupPayload = extractPayloadFromPrefixedLine("GROUP", line);
                        String artifactPayload = extractPayloadFromPrefixedLine("ARTIFACT", line);
                        String descriptionPayload = extractPayloadFromPrefixedLine("DESCRIPTION", line);
                        String versionPayload = extractPayloadFromPrefixedLine("VERSION", line);
                        String classPathPayload = extractPayloadFromPrefixedLine("CLASSPATH", line);
                        String dependencyPayload = extractPayloadFromPrefixedLine("DEPENDENCY", line);

                        if (null != groupPayload)
                            result.attrs.groupID = groupPayload;
                        if (null != artifactPayload)
                            result.attrs.artifactID = artifactPayload;
                        if (null != descriptionPayload)
                            result.attrs.description = descriptionPayload;
                        if (null != versionPayload)
                            result.version = versionPayload;
                        if (null != classPathPayload)
                            result.classPath = classPathPayload;
                        if (null != dependencyPayload) {
                            String[] parts = dependencyPayload.split(":");
                            result.dependencies.add(new RawDependency(
                                    parts[1], // GroupID
                                    parts[2], // ArtifactID
                                    parts[3], // Version
                                    parts[0] // Scope
                            ));
                        }
                    }
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }

                return result;
            } finally {
                FileUtils.deleteDirectory(gradleCacheDir.toString());
                Files.deleteIfExists(modifiedGradleScriptFile);
            }
        }
    }
}
