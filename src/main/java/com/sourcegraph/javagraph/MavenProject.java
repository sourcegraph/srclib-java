package com.sourcegraph.javagraph;

import com.google.common.collect.Iterators;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.*;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.impl.RemoteRepositoryManager;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class MavenProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenProject.class);

    private static final String REPO_DIR = ".m2-srclib";

    private Path pomFile;

    private RepositorySystem repositorySystem;
    private RepositorySystemSession repositorySystemSession;

    public MavenProject(SourceUnit unit) {
        this.pomFile = FileSystems.getDefault().getPath((String) unit.Data.get("POMFile")).toAbsolutePath();
        initRepositorySystem();
    }

    public MavenProject(Path pomFile) {
        this.pomFile = pomFile;
        initRepositorySystem();
    }

    private void initRepositorySystem() {
        repositorySystem = newRepositorySystem();
        repositorySystemSession = newRepositorySystemSession(repositorySystem);
    }

    private org.apache.maven.project.MavenProject mavenProject;

    public org.apache.maven.project.MavenProject getMavenProject() throws ModelBuildingException {
        if (mavenProject == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Building Maven project structure from {}", pomFile);
            }
            DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
            ModelBuildingRequest request = new DefaultModelBuildingRequest();
            request.setSystemProperties(System.getProperties());
            request.setPomFile(pomFile.toFile());
            request.setModelResolver(new MavenModelResolver(new DefaultRemoteRepositoryManager(),
                    repositorySystem,
                    repositorySystemSession));
            ModelBuildingResult result = factory.newInstance().build(request);
            mavenProject = new org.apache.maven.project.MavenProject(result.getEffectiveModel());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Maven project structure is built", pomFile);
            }
        }
        return mavenProject;
    }

    private RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                LOGGER.error("Failed co create service {} using implementation {}", type, impl, exception);
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    private RepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String repoDir = getRepoDir();

        LocalRepository localRepo = new LocalRepository(repoDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    private transient Set<Artifact> mavenDependencyArtifacts;

    protected Set<Artifact> resolveMavenDependencyArtifacts() throws ModelBuildingException, IOException {

        if (mavenDependencyArtifacts == null) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Resolving Maven dependency artifacts");
            }
            mavenDependencyArtifacts = new HashSet<>();
            resolveArtifactDependencies(getMavenProject().getDependencies(), mavenDependencyArtifacts);
        }

        return mavenDependencyArtifacts;
    }

    public Set<RawDependency> listDeps() throws IOException, ModelBuildingException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieving Maven dependencies");
        }

        Set<RawDependency> deps = new HashSet<>();
        List<Dependency> mavenDeps = getMavenProject().getDependencies();
        for (Dependency d : mavenDeps) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing Maven dependency {}", d);
            }
            deps.add(new RawDependency(d.getGroupId(), d.getArtifactId(), d.getVersion(), d.getScope(), null));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieved Maven dependencies");
        }

        return deps;
    }

    @Override
    public List<String> getClassPath() throws ModelBuildingException, IOException, ArtifactResolutionException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Assembling Maven class path");
        }

        Set<Artifact> artifacts = resolveMavenDependencyArtifacts();
        List<String> ret = new ArrayList<>();
        for (Artifact artifact : artifacts) {
            File file = artifact.getFile();
            if (file != null) {
                ret.add(file.getAbsolutePath());
            }
        }
        return ret;
    }

    @Override
    public List<String> getSourcePath() throws Exception {
        // TODO (alexsaveliev) retrieve source path
        return null;
    }

    @Override
    public String getSourceCodeVersion() throws ModelBuildingException, IOException {

        Plugin compile = getMavenProject().getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (compile == null) {
            return DEFAULT_SOURCE_CODE_VERSION;
        }
        Object configuration = compile.getConfiguration();
        if (configuration == null || !(configuration instanceof Xpp3Dom)) {
            return DEFAULT_SOURCE_CODE_VERSION;
        }
        Xpp3Dom xmlConfiguration = (Xpp3Dom) configuration;
        Xpp3Dom source = xmlConfiguration.getChild("source");
        return source == null ? DEFAULT_SOURCE_CODE_VERSION : source.getValue();
    }

    @Override
    public String getSourceCodeEncoding() throws ModelBuildingException, IOException {
        Plugin compile = getMavenProject().getPlugin("org.apache.maven.plugins:maven-compiler-plugin");
        if (compile == null) {
            return null;
        }
        Object configuration = compile.getConfiguration();
        if (configuration == null || !(configuration instanceof Xpp3Dom)) {
            return null;
        }
        Xpp3Dom xmlConfiguration = (Xpp3Dom) configuration;
        Xpp3Dom encoding = xmlConfiguration.getChild("encoding");
        return encoding == null ? null : encoding.getValue();
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws
            IOException, ModelBuildingException, ArtifactResolutionException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieving dependency for JAR {}", jarFile.toAbsolutePath());
        }

        resolveMavenDependencyArtifacts();
        for (org.eclipse.aether.artifact.Artifact a : mavenDependencyArtifacts) {
            if (a.getFile() != null && a.getFile().toPath().equals(jarFile)) {
                return new RawDependency(a.getGroupId(), a.getArtifactId(), a.getVersion(), StringUtils.EMPTY, null);
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

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating source unit from {}", pomFile.toAbsolutePath());
        }

        MavenProject proj = new MavenProject(pomFile);
        final SourceUnit unit = new SourceUnit();

        // Add POMFile so we can open the corresponding Maven project.
        unit.Data.put("POMFile", PathUtil.normalize(pomFile.toString()));

        BuildAnalysis.POMAttrs attrs = proj.getPOMAttrs();
        unit.Type = "JavaArtifact";
        unit.Name = attrs.groupID + "/" + attrs.artifactID;
        unit.Dir = PathUtil.normalize(pomFile.getParent().toString());
        unit.Data.put("Description", attrs.description);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Collecting source files");
        }

        Set<String> files = new HashSet<>();
        List<String> sourceRoots = proj.getMavenProject().getCompileSourceRoots();
        for (int i = 0; i < sourceRoots.size(); i++) {
            sourceRoots.set(i, Paths.get(sourceRoots.get(i)).toAbsolutePath().normalize().toString());
        }

        Path root = pomFile.getParent().toAbsolutePath().normalize();

        getSourceFiles(files, sourceRoots);
        String sourceRoot = proj.getMavenProject().getBuild().getSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/main";
        }
        sourceRoot = root.resolve(sourceRoot).toAbsolutePath().normalize().toString();
        if (!sourceRoots.contains(sourceRoot)) {
            getSourceFiles(files, Collections.singletonList(sourceRoot));
        }
        List<String> testSourceRoots = proj.getMavenProject().getTestCompileSourceRoots();
        for (int i = 0; i < testSourceRoots.size(); i++) {
            testSourceRoots.set(i, Paths.get(testSourceRoots.get(i)).toAbsolutePath().normalize().toString());
        }
        getSourceFiles(files, testSourceRoots);
        sourceRoot = proj.getMavenProject().getBuild().getTestSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/test";
        }
        sourceRoot = root.resolve(sourceRoot).toAbsolutePath().normalize().toString();
        if (!sourceRoots.contains(sourceRoot) && !testSourceRoots.contains(sourceRoot)) {
            getSourceFiles(files, Collections.singletonList(sourceRoot));
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Source files collected");
        }

        unit.Files = new LinkedList<>(files);
        unit.sortFiles();

        unit.Dependencies = new ArrayList<>(proj.listDeps());

        return unit;
    }

    public static Collection<SourceUnit> findAllSourceUnits() throws IOException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieving source units");
        }

        HashSet<Path> pomFiles = ScanUtil.findMatchingFiles("pom.xml");
        List<SourceUnit> units = new ArrayList<>();
        for (Path pomFile : pomFiles) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing POM file {}", pomFile.toAbsolutePath());
            }
            try {
                SourceUnit unit = createSourceUnit(pomFile);
                units.add(unit);
            } catch (Exception e) {
                LOGGER.warn("Error processing POM file {}", pomFile.toAbsolutePath(), e);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieved source units");
        }

        return units;
    }

    /**
     * Retrieves all source files in Maven project
     *
     * @param files       set to fill with the data
     * @param sourceRoots list of source roots to search in, i.e. compile source roots, test compile source roots
     */
    private static void getSourceFiles(Set<String> files,
                                       List<String> sourceRoots) {
        for (String sourceRoot : sourceRoots) {
            if (sourceRoot == null) {
                continue;
            }

            Path path = Paths.get(sourceRoot);
            if (!path.toFile().isDirectory()) {
                return;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collecting source files from {}", path.toAbsolutePath());
            }

            final DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setIncludes(new String[]{"**/*.java"});
            directoryScanner.setExcludes(null);
            directoryScanner.setBasedir(sourceRoot);
            directoryScanner.scan();
            for (String fileName : directoryScanner.getIncludedFiles()) {
                files.add(PathUtil.relativizeCwd(Paths.get(sourceRoot, fileName).toString()));
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collected source files from {}", path.toAbsolutePath());
            }

        }
    }

    protected static String getRepoDir() {
        // TODO(sqs): If running in Docker, use a directory not inside the repo if in Docker since the Docker source volume is readonly.
        return REPO_DIR;
    }

    /**
     * Resolves artifact dependencies
     * @param dependencies dependencies to check
     * @param targets set to fill with data
     * @throws ModelBuildingException
     */
    private void resolveArtifactDependencies(List<Dependency> dependencies,
                                             Set<Artifact> targets) throws ModelBuildingException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolving artifact dependencies");
        }

        List<org.eclipse.aether.graph.Dependency> deps = new ArrayList<>();
        ArtifactTypeRegistry artifactTypeRegistry = repositorySystemSession.getArtifactTypeRegistry();
        for (Dependency dependency :dependencies) {
            Artifact artifact = new DefaultArtifact(dependency.getGroupId(),
                    dependency.getArtifactId(),
                    dependency.getClassifier(),
                    "jar",
                    dependency.getVersion(),
                    artifactTypeRegistry.get(dependency.getType()));
            deps.add(new org.eclipse.aether.graph.Dependency(artifact, dependency.getScope()));
        }
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setDependencies(deps);
        List<RemoteRepository> repoz = getMavenProject().getRepositories().stream().
                map(ArtifactDescriptorUtils::toRemoteRepository).collect(Collectors.toList());
        collectRequest.setRepositories(repoz);

        DependencyNode node;
        try {
            node = repositorySystem.collectDependencies(repositorySystemSession, collectRequest).getRoot();
        } catch (DependencyCollectionException e) {
            LOGGER.warn("Failed to collect dependencies - {}", e.getMessage());
            node = e.getResult().getRoot();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Collected dependencies");
        }
        if (node == null) {
            LOGGER.warn("Failed to collect dependencies - no dependencies were collected");
            return;
        }

        DependencyRequest projectDependencyRequest = new DependencyRequest(node, null);
        try {
            repositorySystem.resolveDependencies(repositorySystemSession, projectDependencyRequest);
        } catch (DependencyResolutionException e) {
            LOGGER.warn("Failed to resolve dependencies - {}", e.getMessage());
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved dependencies");
        }

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);

        targets.addAll(nlg.getDependencies(true).stream().map(org.eclipse.aether.graph.Dependency::getArtifact).
                collect(Collectors.toList()));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved artifact dependencies");
        }

    }

    private class MavenModelResolver implements ModelResolver {

        private static final String POM = "pom";

        private Set<String> repositoryKeys;
        private List<RemoteRepository> repositories;

        private RepositorySystem repositorySystem;
        private RepositorySystemSession repositorySystemSession;

        private RemoteRepositoryManager remoteRepositoryManager;

        private MavenModelResolver(RemoteRepositoryManager remoteRepositoryManager,
                                   RepositorySystem repositorySystem,
                                   RepositorySystemSession repositorySystemSession) {
            this.repositorySystem = repositorySystem;
            this.repositorySystemSession = repositorySystemSession;
            this.remoteRepositoryManager = remoteRepositoryManager;
            repositoryKeys = new HashSet<>();
            repositories = new ArrayList<>();

            RemoteRepository central = new RemoteRepository.Builder("central",
                    "default",
                    "http://central.maven.org/maven2/").build();
            repositoryKeys.add(central.getId());
            repositories.add(central);
        }

        private MavenModelResolver(MavenModelResolver source) {
            this.repositorySystem = source.repositorySystem;
            this.repositorySystemSession = source.repositorySystemSession;
            this.remoteRepositoryManager = source.remoteRepositoryManager;
            this.repositories = new ArrayList<>(source.repositories);
            this.repositoryKeys = new HashSet<>(source.repositoryKeys);
        }


        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version)
                throws UnresolvableModelException {

            Artifact pomArtifact = new DefaultArtifact(groupId, artifactId, StringUtils.EMPTY, POM, version);

            try {
                ArtifactRequest request = new ArtifactRequest(pomArtifact, repositories, null);
                pomArtifact = repositorySystem.resolveArtifact(repositorySystemSession, request).getArtifact();
            } catch (ArtifactResolutionException e) {
                throw new UnresolvableModelException(e.getMessage(), groupId, artifactId, version, e);
            }
            return new FileModelSource(pomArtifact.getFile());
        }

        @Override
        public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing parent model {}", parent.getRelativePath());
            }
            Artifact artifact = new DefaultArtifact(parent.getGroupId(),
                    parent.getArtifactId(),
                    StringUtils.EMPTY,
                    POM,
                    parent.getVersion());

            VersionRangeRequest versionRangeRequest = new VersionRangeRequest(artifact, repositories, null);

            try {
                VersionRangeResult versionRangeResult = repositorySystem.resolveVersionRange(repositorySystemSession,
                        versionRangeRequest);
                parent.setVersion(versionRangeResult.getHighestVersion().toString());
            } catch (VersionRangeResolutionException e) {
                throw new UnresolvableModelException(e.getMessage(), parent.getGroupId(), parent.getArtifactId(),
                        parent.getVersion(), e);

            }

            return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
        }

        @Override
        public void addRepository(Repository repository) throws InvalidRepositoryException {
            addRepository(repository, false);
        }

        @Override
        public void addRepository(Repository repository, boolean replace) throws InvalidRepositoryException {

            String id = repository.getId();

            if (repositoryKeys.contains(id)) {
                if (!replace) {
                    return;
                }
                Iterators.removeIf(repositories.iterator(), input -> input.getId().equals(id));
            }
            List<RemoteRepository> additions = Collections.singletonList(
                    ArtifactDescriptorUtils.toRemoteRepository(repository));
            repositories =
                    remoteRepositoryManager.aggregateRepositories(repositorySystemSession,
                            repositories,
                            additions,
                            true);
            repositoryKeys.add(id);
        }

        @Override
        public ModelResolver newCopy() {
            return new MavenModelResolver(this);
        }
    }

}
