package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class MavenProject implements Project {

    public static String[] buildClasspathArgs = {"mvn", "dependency:build-classpath", "-Dmdep.outputFile=/dev/stderr"};
    private SourceUnit unit;

    private Path pomFile;

    public MavenProject(SourceUnit unit) {
        this.unit = unit;
        this.pomFile = FileSystems.getDefault().getPath((String) unit.Data.get("POMFile"));
    }

    public MavenProject(Path pomFile) {
        this.pomFile = pomFile;
    }

    private org.apache.maven.project.MavenProject mavenProject;

    public static String getMavenClassPath(Path pomFile) {
        ProcessBuilder pb = new ProcessBuilder(buildClasspathArgs);
        pb.directory(pomFile.getParent().toFile());

        try {
            Process process = pb.start();
            IOUtils.copy(process.getInputStream(), System.err);
            return IOUtils.toString(process.getErrorStream());
        } catch (Exception e1) {
            e1.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    public org.apache.maven.project.MavenProject getMavenProject() throws IOException, XmlPullParserException {
        if (mavenProject == null) {
            MavenXpp3Reader mavenReader = new MavenXpp3Reader();
            try (BufferedReader reader = Files.newBufferedReader(pomFile, StandardCharsets.UTF_8)) {
                Model model = mavenReader.read(reader);
                model.setPomFile(pomFile.toFile());
                mavenProject = new org.apache.maven.project.MavenProject(model);
            }
        }
        return mavenProject;
    }

    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                exception.printStackTrace();
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system) throws IOException {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        // Use a directory not inside the repo if in Docker since the Docker source volume is readonly.
        String repoDir = System.getenv("IN_DOCKER_CONTAINER") != null ? Files.createTempDirectory("srclib-java-m2").toString() : "target/local-repo";

        LocalRepository localRepo = new LocalRepository(repoDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
        // TODO(sqs): get remote repositories specified in pom.xml
        return new ArrayList<RemoteRepository>(Arrays.asList(newCentralRepository()));
    }

    private static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "http://central.maven.org/maven2/").build();
    }

    private transient Set<Artifact> mavenDependencyArtifacts;

    private Set<Artifact> resolveMavenDependencyArtifacts() throws IOException, XmlPullParserException {
        if (mavenDependencyArtifacts == null) {
            mavenDependencyArtifacts = new HashSet<>();

            RepositorySystem system = newRepositorySystem();
            RepositorySystemSession session = newRepositorySystemSession(system);

//        {
//            Artifact artifact = new DefaultArtifact("org.eclipse.aether:aether-util:1.0.0.v20140518");
//            ArtifactRequest artifactRequest = new ArtifactRequest();
//            artifactRequest.setArtifact(artifact);
//            artifactRequest.setRepositories(newRepositories(system, session));
//            ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);
//            artifact = artifactResult.getArtifact();
//            System.out.println(artifact + " resolved to  " + artifact.getFile());
//        }

            List<Dependency> deps = getMavenProject().getDependencies();
            for (Dependency d : deps) {
                System.err.println("Maven: resolving dependency " + d.toString());

                Artifact artifact = new DefaultArtifact(d.getGroupId(), d.getArtifactId(), d.getClassifier(), "jar", d.getVersion());
                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(artifact);
                artifactRequest.setRepositories(newRepositories(system, session));

                ArtifactResult artifactResult = null;
                try {
                    artifactResult = system.resolveArtifact(session, artifactRequest);
                } catch (ArtifactResolutionException e) {
                    System.err.println("Dependency " + artifact + " failed to resolve: " + e.toString());
                    continue;
                }

                artifact = artifactResult.getArtifact();
                System.err.println("Dependency " + artifact + " resolved:  " + artifact.getFile());

                mavenDependencyArtifacts.add(artifact);
            }
        }

        return mavenDependencyArtifacts;
    }

    @Override
    public Set<RawDependency> listDeps() throws IOException, XmlPullParserException {
        Set<RawDependency> deps = new HashSet<>();
        List<Dependency> mavenDeps = getMavenProject().getDependencies();
        for (Dependency d : mavenDeps) {
            deps.add(new RawDependency(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getScope()));
        }
        return deps;
    }

    @Override
    public List<String> getClassPath() throws XmlPullParserException, IOException, ArtifactResolutionException {
        Set<Artifact> artifacts = resolveMavenDependencyArtifacts();
        List<String> entries = new ArrayList<>();
        for (Artifact a : artifacts) {
            entries.add(a.getFile().getAbsolutePath());
        }
        return entries;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws IOException, XmlPullParserException, ArtifactResolutionException {
        resolveMavenDependencyArtifacts();
        for (org.eclipse.aether.artifact.Artifact a : mavenDependencyArtifacts) {
            if (a.getFile().toPath().equals(jarFile)) {
                return new RawDependency(a.getGroupId(), a.getArtifactId(), a.getVersion(), "");
            }
        }
        return null;

//        MavenProject proj = openMavenProject();
//        Set<Artifact> arts = proj.getDependencyArtifacts();
//        for (Artifact a : arts) {
//            if (a.getFile().toPath().equals(jarFile)) {
//                return new RawDependency(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getScope());
//            }
//        }
//        return null;
    }

    private transient Map<Path, RawDependency> jarPathToDep;

    private Map<Path, RawDependency> resolveMavenDependencyArtifactsCommand() throws IOException {
        if (jarPathToDep != null) {
            return jarPathToDep;
        }
        jarPathToDep = new HashMap<>();

        String homedir = System.getProperty("user.home");
        String[] mavenArgs = {"mvn", "dependency:resolveOrigin", "-DoutputAbsoluteArtifactFilename=true", "-DoutputFile=/dev/stderr"};

        ProcessBuilder pb = new ProcessBuilder(mavenArgs);
        pb.directory(new File(pomFile.getParent().toString()));
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

    public RawDependency resolveJARToPOMDependencyCommand(Path jarFile) throws IOException, XmlPullParserException, ArtifactResolutionException {
        Map<Path, RawDependency> path2dep = resolveMavenDependencyArtifactsCommand();
        return path2dep.get(jarFile);
    }

    private BuildAnalysis.POMAttrs getPOMAttrs() throws IOException, FileNotFoundException, XmlPullParserException {
        org.apache.maven.project.MavenProject p = getMavenProject();
        String groupId = p.getGroupId() == null ? p.getParent().getGroupId() : p.getGroupId();
        return new BuildAnalysis.POMAttrs(groupId, p.getArtifactId(), p.getDescription());
    }

    private static SourceUnit createSourceUnit(Path pomFile) throws IOException, XmlPullParserException {
        MavenProject proj = new MavenProject(pomFile);
        final SourceUnit unit = new SourceUnit();

        // Add POMFile so we can open the corresponding Maven project.
        unit.Data.put("POMFile", pomFile.toString());

        BuildAnalysis.POMAttrs attrs = proj.getPOMAttrs();
        unit.Type = "JavaArtifact";
        unit.Name = attrs.groupID + "/" + attrs.artifactID;
        unit.Dir = pomFile.getParent().toString();
        unit.Data.put("Description", attrs.description);

        // TODO: Java source files can be other places './src'
        unit.Files = ScanUtil.findAllJavaFiles(pomFile.getParent().resolve("src"));
        unit.sortFiles();

        unit.Dependencies = new ArrayList<>(proj.listDeps());

        return unit;
    }

    public static Collection<SourceUnit> findAllSourceUnits() throws IOException {
        HashSet<Path> pomFiles = ScanUtil.findMatchingFiles("pom.xml");
        List<SourceUnit> units = new ArrayList<>();
        for (Path pomFile : pomFiles) {
            try {
                SourceUnit unit = createSourceUnit(pomFile);
                units.add(unit);
            } catch (Exception e) {
                System.err.println("Error processing pom.xml file " + pomFile + ": " + e.toString());
            }
        }
        return units;
    }
}
