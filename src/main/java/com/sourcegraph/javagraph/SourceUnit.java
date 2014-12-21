package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.sonatype.aether.resolution.ArtifactDescriptorException;
import org.sonatype.aether.resolution.DependencyResolutionException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

/**
 * SourceUnit represents a source unit expected by srclib. A source unit is a
 * build-system- and language-independent abstraction of a Maven repository or
 * Gradle project. This class also includes static helpers for special case
 * source units like the Java SDK.
 */
public class SourceUnit {

    public static String StdLibRepoURI = "hg.openjdk.java.net/jdk8/jdk8/jdk";
    public static String StdLibTestRepoURI = "github.com/sgtest/java-jdk-sample";
    public static String AndroidSdkURI = "android.googlesource.com/platform/frameworks/base";
    String Name;
    String Type;
    String Repo;
    List<String> Files = new LinkedList<>();
    String Dir;
    List<RawDependency> Dependencies = new LinkedList<>();

    // TODO(rameshvarun): Globs entry
    Map<String, Object> Data = new HashMap<>();
    Map<String, String> Ops = new HashMap<>();

    public SourceUnit() {
        Ops.put("graph", null);
        Ops.put("depresolve", null);
    }

    // TODO(rameshvarun): Info field

    public static boolean isStdLib(String repo) {
        return repo.equals(StdLibRepoURI) || repo.equals(StdLibTestRepoURI) || repo.equals(AndroidSdkURI);
    }

    /**
     * Memoized; access via openMavenProject.
     */
    private transient MavenProject mavenProject;

    private MavenProject openMavenProject() throws IOException, XmlPullParserException {
        if (mavenProject != null) {
            return mavenProject;
        }

        Path pomFile = getPOMFilePath();
        if (pomFile == null) return null;

        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        BufferedReader reader = null;
        try {
            reader = java.nio.file.Files.newBufferedReader(pomFile, StandardCharsets.UTF_8);
            Model model = mavenReader.read(reader);
            model.setPomFile(pomFile.toFile());
            mavenProject = new MavenProject(model);
        } finally {
            reader.close();
        }
        return mavenProject;
    }

    public BuildAnalysis.POMAttrs getPOMAttrs() throws IOException, FileNotFoundException, XmlPullParserException {
        MavenProject proj = openMavenProject();
        String groupId = proj.getGroupId() == null ? proj.getParent().getGroupId() : proj.getGroupId();
        return new BuildAnalysis.POMAttrs(groupId, proj.getArtifactId(), proj.getDescription());
    }

    public HashSet<RawDependency> getRawPOMDependencies() throws IOException, XmlPullParserException {
        HashSet<RawDependency> results = new HashSet<>();
        MavenProject proj = openMavenProject();
        List<Dependency> deps = proj.getDependencies();
        for (Dependency d : deps) {
            results.add(new RawDependency(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getScope()));
        }
        return results;
    }

    private transient Map<Path, RawDependency> jarPathToDep;

    private Map<Path, RawDependency> resolveMavenDependencyArtifacts() throws IOException {
        if (jarPathToDep != null) {
            return jarPathToDep;
        }
        jarPathToDep=new HashMap<>();

        String homedir = System.getProperty("user.home");
        String[] mavenArgs = {"mvn", "dependency:resolve", "-DoutputAbsoluteArtifactFilename=true", "-DoutputFile=/dev/stderr"};

        ProcessBuilder pb = new ProcessBuilder(mavenArgs);
        pb.directory(new File(getPOMFilePath().getParent().toString()));
        BufferedReader in = null;
        try {
            Process process = pb.start();
            in = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            IOUtils.copy(process.getInputStream(), System.err);

            String line;
            while ((line = in.readLine()) != null) {
                if (!line.startsWith("   "))
                    continue;
                if (line.trim().equals("none"))
                    continue;

                String[] parts = line.trim().split(":");

                RawDependency dep = new RawDependency(parts[0], // GroupID
                        parts[1], // ArtifactID
                        parts[parts.length - 3], // Version
                        parts[parts.length - 2] // Scope
                );

                // was: ScanCommand.swapPrefix(parts[parts.length - 1], homedir, "~")
                Path jarFile = FileSystems.getDefault().getPath(parts[parts.length - 1]);
                System.err.println("JAR FILE IS: " + jarFile.toString());
                jarPathToDep.put(jarFile, dep);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return jarPathToDep;
    }

    public RawDependency resolveJARToPOMDependency(Path jarFile) throws IOException, XmlPullParserException, ArtifactDescriptorException, DependencyResolutionException {
        Map<Path, RawDependency> path2dep = resolveMavenDependencyArtifacts();
        return path2dep.get(jarFile);
    }

    // TODO(rameshvarun): Config list

    public boolean isStdLib() {
        return Repo != null && isStdLib(Repo);
    }

    public Path getPOMFilePath() {
        String pomFile = (String) Data.get("POMFile");
        if (pomFile == null) {
            return null;
        }
        return FileSystems.getDefault().getPath(pomFile);
    }

}
