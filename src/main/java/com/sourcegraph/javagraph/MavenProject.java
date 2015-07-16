package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.*;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.DirectoryScanner;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MavenProject implements Project {

    private static final String REPO_DIR = ".m2-srclib";

    private Path pomFile;

    public MavenProject(SourceUnit unit) {
        this.pomFile = FileSystems.getDefault().getPath((String) unit.Data.get("POMFile"));
    }

    public MavenProject(Path pomFile) {
        this.pomFile = pomFile;
    }

    private org.apache.maven.project.MavenProject mavenProject;

    public org.apache.maven.project.MavenProject getMavenProject() throws ModelBuildingException {
        if (mavenProject == null) {

            DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
            ModelBuildingRequest request = new DefaultModelBuildingRequest();
            request.setSystemProperties(System.getProperties());
            request.setPomFile(pomFile.toFile());
            request.setModelResolver(new MavenModelResolver(pomFile.getParent()));
            ModelBuildingResult result = factory.newInstance().build(request);
            mavenProject = new org.apache.maven.project.MavenProject(result.getEffectiveModel());
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

        String repoDir = getRepoDir();

        LocalRepository localRepo = new LocalRepository(repoDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private List<RemoteRepository> newRepositories()
            throws ModelBuildingException {
        List<RemoteRepository> repositories = new ArrayList<>();
        List<Repository> repos = getMavenProject().getRepositories();
        repositories.addAll(repos.stream().map(ArtifactDescriptorUtils::toRemoteRepository).
                collect(Collectors.toList()));
        return repositories;
    }
    private transient Set<Artifact> mavenDependencyArtifacts;

    protected Set<Artifact> resolveMavenDependencyArtifacts() throws ModelBuildingException, IOException {
        if (mavenDependencyArtifacts == null) {
            mavenDependencyArtifacts = new HashSet<>();

            RepositorySystem system = newRepositorySystem();
            RepositorySystemSession session = newRepositorySystemSession(system);

            List<Dependency> deps = getMavenProject().getDependencies();
            List<RemoteRepository> repos = newRepositories();

            for (Dependency d : deps) {
                System.err.println("Maven: resolving dependency " + d.toString());

                Artifact artifact = new DefaultArtifact(d.getGroupId(),
                        d.getArtifactId(),
                        d.getClassifier(),
                        "jar",
                        d.getVersion());
                try {
                    artifact = resolveArtifactVersion(artifact, system, session, repos);
                } catch (VersionRangeResolutionException e) {
                    System.err.println("Failed to resolve version for artifact " + artifact + ": " + e.toString());
                    continue;
                }

                if (d.getSystemPath() == null) {
                    artifact = resolveArtifact(artifact, repos, system, session);
                } else {
                    File file = new File(d.getSystemPath());
                    if (file.exists()) {
                        artifact.setFile(file);
                    } else {
                        System.err.println("Dependency " + artifact + " failed to resolve: no such file " + file.getPath());
                        artifact = null;
                    }
                }
                if (artifact != null) {
                    System.err.println("Dependency " + artifact + " resolved:  " + artifact.getFile());
                    mavenDependencyArtifacts.add(artifact);

                    // resolving artifact dependencies as well
                    try {
                        resolveArtifactDependencies(artifact, system, session, mavenDependencyArtifacts);
                    } catch (DependencyCollectionException | DependencyResolutionException e) {
                        System.err.println("Failed to resolve artifact sub-dependencies for " + artifact + ": " +
                                e.toString());
                    }
                }
            }
        }

        return mavenDependencyArtifacts;
    }

    @Override
    public Set<RawDependency> listDeps() throws IOException, ModelBuildingException {
        Set<RawDependency> deps = new HashSet<>();
        List<Dependency> mavenDeps = getMavenProject().getDependencies();
        for (Dependency d : mavenDeps) {
            deps.add(new RawDependency(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getScope()));
        }
        return deps;
    }

    @Override
    public List<String> getClassPath() throws ModelBuildingException, IOException, ArtifactResolutionException {
        Set<Artifact> artifacts = resolveMavenDependencyArtifacts();
        return artifacts.stream().map(a -> a.getFile().getAbsolutePath()).collect(Collectors.toList());
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws IOException, ModelBuildingException, ArtifactResolutionException {
        resolveMavenDependencyArtifacts();
        for (org.eclipse.aether.artifact.Artifact a : mavenDependencyArtifacts) {
            if (a.getFile().toPath().equals(jarFile)) {
                return new RawDependency(a.getGroupId(), a.getArtifactId(), a.getVersion(), StringUtils.EMPTY);
            }
        }
        return null;

    }

    private BuildAnalysis.POMAttrs getPOMAttrs() throws IOException, ModelBuildingException {
        org.apache.maven.project.MavenProject p = getMavenProject();
        String groupId = p.getGroupId() == null ? p.getParent().getGroupId() : p.getGroupId();
        return new BuildAnalysis.POMAttrs(groupId, p.getArtifactId(), p.getDescription());
    }

    private static SourceUnit createSourceUnit(Path pomFile) throws IOException, ModelBuildingException {
        MavenProject proj = new MavenProject(pomFile);
        final SourceUnit unit = new SourceUnit();

        // Add POMFile so we can open the corresponding Maven project.
        unit.Data.put("POMFile", PathUtil.normalize(pomFile.toString()));

        BuildAnalysis.POMAttrs attrs = proj.getPOMAttrs();
        unit.Type = "JavaArtifact";
        unit.Name = attrs.groupID + "/" + attrs.artifactID;
        unit.Dir = PathUtil.normalize(pomFile.getParent().toString());
        unit.Data.put("Description", attrs.description);

        Set<String> files = new HashSet<>();
        List<String> sourceRoots = proj.getMavenProject().getCompileSourceRoots();
        for (int i = 0; i < sourceRoots.size(); i++) {
            sourceRoots.set(i, Paths.get(sourceRoots.get(i)).toAbsolutePath().normalize().toString());
        }

        Path root = pomFile.getParent().toAbsolutePath().normalize();

        getSourceFiles(files, sourceRoots, root);
        String sourceRoot = proj.getMavenProject().getBuild().getSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/main";
        }
        sourceRoot = root.resolve(sourceRoot).toAbsolutePath().normalize().toString();
        if (!sourceRoots.contains(sourceRoot)) {
            getSourceFiles(files, Collections.singletonList(sourceRoot), root);
        }
        List<String> testSourceRoots = proj.getMavenProject().getTestCompileSourceRoots();
        for (int i = 0; i < testSourceRoots.size(); i++) {
            testSourceRoots.set(i, Paths.get(testSourceRoots.get(i)).toAbsolutePath().normalize().toString());
        }
        getSourceFiles(files, testSourceRoots, root);
        sourceRoot = proj.getMavenProject().getBuild().getTestSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/test";
        }
        sourceRoot = root.resolve(sourceRoot).toAbsolutePath().normalize().toString();
        if (!sourceRoots.contains(sourceRoot) && !testSourceRoots.contains(sourceRoot)) {
            getSourceFiles(files, Collections.singletonList(sourceRoot), root);
        }
        unit.Files = new LinkedList<>(files);
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

    /**
     * Retrieves all source files in Maven project
     * @param files set to fill with the data
     * @param sourceRoots list of source roots to search in, i.e. compile source roots, test compile source roots
     * @param basePath base path to produce relative entries,
     *                 i.e. basePath = /foo/ and current path = /foo/bar gives "bar"
     */
    private static void getSourceFiles(Set<String> files,
                                       List<String> sourceRoots,
                                       Path basePath) {
        for (String sourceRoot: sourceRoots) {
            if (sourceRoot == null) {
                continue;
            }
            Path path = basePath.resolve(sourceRoot);
            if (!path.toFile().exists() || !path.toFile().isDirectory()) {
                return;
            }
            final DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setIncludes(new String[]{"**/*.java"});
            directoryScanner.setExcludes(null);
            directoryScanner.setBasedir(sourceRoot);
            directoryScanner.scan();
            for (String fileName : directoryScanner.getIncludedFiles()) {
                files.add(PathUtil.normalize(basePath.relativize(path.resolve(fileName)).toString()));
            }
        }
    }

    protected static String getRepoDir() {
        // TODO(sqs): If running in Docker, use a directory not inside the repo if in Docker since the Docker source volume is readonly.
        return REPO_DIR;
    }

    private Artifact resolveArtifact(Artifact source,
                                     List<RemoteRepository> repos,
                                     RepositorySystem system,
                                     RepositorySystemSession session) {
        try {
            ArtifactRequest artifactRequest = new ArtifactRequest();
            artifactRequest.setArtifact(source);
            artifactRequest.setRepositories(repos);
            return system.resolveArtifact(session, artifactRequest).getArtifact();
        } catch (ArtifactResolutionException e) {
            System.err.println("Dependency " + source + " failed to resolve: " + e.toString());
            return null;
        }
    }

    /**
     * Resolves all artifact dependencies for a given artifact
     * @param artifact artifact to resolve dependencies for
     * @param system repository system
     * @param session repository system session
     * @param targets set to fill with resolved dependencies
     * @throws ModelBuildingException
     * @throws DependencyCollectionException
     * @throws DependencyResolutionException
     */
    private void resolveArtifactDependencies(Artifact artifact,
                                             RepositorySystem system,
                                             RepositorySystemSession session,
                                             Set<Artifact> targets) throws
            ModelBuildingException, DependencyCollectionException, DependencyResolutionException {
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(artifact, "compile"));
        List<RemoteRepository> repoz = new ArrayList<>();
        for (Repository repo : getMavenProject().getRepositories()) {
            repoz.add(ArtifactDescriptorUtils.toRemoteRepository(repo));
        }
        collectRequest.setRepositories(repoz);

        DependencyNode node = system.collectDependencies(session, collectRequest).getRoot();
        DependencyRequest projectDependencyRequest = new DependencyRequest(node, null);

        system.resolveDependencies(session, projectDependencyRequest);

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);

        targets.addAll(nlg.getDependencies(true).stream().map(org.eclipse.aether.graph.Dependency::getArtifact).
                collect(Collectors.toList()));
    }

    /**
     * Resolve artifact version
     * @param artifact artifact to resolve version for
     * @param system repository system
     * @param session repository system session
     * @param repos repositories to use
     * @return resolved artifact
     * @throws VersionRangeResolutionException
     */
    private Artifact resolveArtifactVersion(Artifact artifact,
                                            RepositorySystem system,
                                            RepositorySystemSession session,
                                            List<RemoteRepository> repos) throws VersionRangeResolutionException {
        VersionRangeResult versionResult;
        VersionRangeRequest versionRequest = new VersionRangeRequest();
        versionRequest.setArtifact(artifact);
        versionRequest.setRepositories(repos);
        versionResult = system.resolveVersionRange(session, versionRequest);
        String resolvedVersion = versionResult.getHighestVersion().toString();
        if (!resolvedVersion.equals(artifact.getVersion())) {
            System.err.println("Resolved version for artifact " + artifact + ": " + resolvedVersion);
            artifact = artifact.setVersion(versionResult.getHighestVersion().toString());
        }
        return artifact;
    }

    private class MavenModelResolver implements ModelResolver {

        private Path root;

        MavenModelResolver(Path root) {
            this.root = root;
        }

        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version) throws UnresolvableModelException {
            return null;
        }

        @Override
        public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            return new FileModelSource(new File(root.toFile(), parent.getRelativePath()));
        }

        @Override
        public void addRepository(Repository repository) throws InvalidRepositoryException {
        }

        @Override
        public void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {
        }

        @Override
        public ModelResolver newCopy() {
            return new MavenModelResolver(root);
        }
    }

}
