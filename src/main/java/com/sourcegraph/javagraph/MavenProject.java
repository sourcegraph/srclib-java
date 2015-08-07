package com.sourcegraph.javagraph;

import com.google.common.collect.Iterators;
import com.sourcegraph.javagraph.maven.plugins.MavenPlugins;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.*;
import org.apache.maven.model.building.*;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.repository.internal.ArtifactDescriptorUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
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
import java.io.FileInputStream;
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

    private SourceUnit unit;

    private static RepositorySystem repositorySystem;
    private static RepositorySystemSession repositorySystemSession;

    static {
        initRepositorySystem();
    }

    public MavenProject(SourceUnit unit) {
        this.unit = unit;
        this.pomFile = FileSystems.getDefault().getPath((String) unit.Data.get("POMFile")).toAbsolutePath();
    }

    public MavenProject(Path pomFile) {
        this.pomFile = pomFile;
    }

    private static void initRepositorySystem() {
        repositorySystem = newRepositorySystem();
        repositorySystemSession = newRepositorySystemSession(repositorySystem);
    }

    private transient org.apache.maven.project.MavenProject mavenProject;

    /**
     * Fetches and parses POM file if necessary, applies processing plugins
     * @return maven project data
     * @throws ModelBuildingException
     */
    protected org.apache.maven.project.MavenProject getMavenProject() throws ModelBuildingException {
        if (mavenProject == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Building Maven project structure from {}", pomFile);
            }
            DefaultModelBuilderFactory factory = new DefaultModelBuilderFactory();
            ModelBuildingRequest request = new DefaultModelBuildingRequest();
            request.setSystemProperties(System.getProperties());
            request.setPomFile(pomFile.toFile());
            // alexsaveliev: adding a resolver used by model builder to fetch POM files
            request.setModelResolver(new MavenModelResolver(new DefaultRemoteRepositoryManager(),
                    repositorySystem,
                    repositorySystemSession));
            ModelBuildingResult result = factory.newInstance().build(request);
            mavenProject = new org.apache.maven.project.MavenProject(result.getEffectiveModel());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Maven project structure is built", pomFile);
            }
            // applying all registered plugins to adjust project data
            MavenPlugins.getInstance().apply(mavenProject, PathUtil.CWD.resolve(getRepoDir()).toFile());
        }
        return mavenProject;
    }

    /**
     * Initializes repository system
     * @return repository system
     */
    private static RepositorySystem newRepositorySystem() {
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

    /**
     * Initializes repository system session
     * @param system repository system to use
     * @return repository system session
     */
    private static RepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        String repoDir = getRepoDir();

        LocalRepository localRepo = new LocalRepository(repoDir);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    /**
     * @return list of Maven project dependencies
     * @throws IOException
     * @throws ModelBuildingException
     */
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
            RawDependency rawDependency = new RawDependency(d.getGroupId(),
                    d.getArtifactId(),
                    d.getVersion(),
                    d.getScope(),
                    null);
            rawDependency.classifier = d.getClassifier();
            rawDependency.type = d.getType();
            rawDependency.repoURI = StringUtils.EMPTY;
            deps.add(rawDependency);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieved Maven dependencies");
        }

        return deps;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getClassPath() {
        // simply looking in the unit's data, classpath was collected at the "scan" phase
        return (List<String>) unit.Data.get("ClassPath");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getBootClassPath() throws Exception {
        // simply looking in the unit's data, bootsrap classpath was collected at the "scan" phase
        return (List<String>) unit.Data.get("BootClassPath");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getSourcePath() {
        // simply looking in the unit's data, sourcepath was collected at the "scan" phase
        List<List<String>> sourceDirs = (List<List<String>>) unit.Data.get("SourcePath");
        return sourceDirs.stream().map(sourceDir -> sourceDir.get(2)).collect(Collectors.toList());
    }

    @Override
    public String getSourceCodeVersion() {
        // simply looking in the unit's data, source version was returieved at the "scan" phase
        return (String) unit.Data.get("SourceVersion");
    }

    @Override
    public String getSourceCodeEncoding() {
        // simply looking in the unit's data, source encoding was returieved at the "scan" phase
        return (String) unit.Data.get("SourceEncoding");
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws
            IOException, ModelBuildingException {
        return null;

    }

    /**
     * @return POM attributes from current project
     * @throws IOException
     * @throws ModelBuildingException
     */
    private BuildAnalysis.POMAttrs getPOMAttrs() throws IOException, ModelBuildingException {
        org.apache.maven.project.MavenProject p = getMavenProject();
        String groupId = p.getGroupId() == null ? p.getParent().getGroupId() : p.getGroupId();
        return new BuildAnalysis.POMAttrs(groupId, p.getArtifactId(), p.getDescription());
    }

    /**
     * Converts Maven project into BuildInfo
     * @param proj project to convert
     * @return BuildInfo structure
     * @throws IOException
     * @throws ModelBuildingException
     */
    private static BuildAnalysis.BuildInfo createBuildInfo(MavenProject proj)
            throws IOException, ModelBuildingException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating source unit from {}", proj.pomFile.toAbsolutePath());
        }

        BuildAnalysis.BuildInfo info = new BuildAnalysis.BuildInfo();

        info.attrs = proj.getPOMAttrs();
        info.buildFile = proj.pomFile.toString();
        info.dependencies = proj.listDeps();
        info.version = proj.getMavenProject().getVersion();
        info.projectDir = proj.pomFile.getParent().toString();

        Collection<String> sourceRoots = collectSourceRoots(proj.pomFile, proj);
        info.sourceDirs = sourceRoots.stream().map(sourceRoot ->
                new String[] {info.getName(), info.version, sourceRoot}).collect(Collectors.toList());
        info.sources = collectSourceFiles(sourceRoots);
        info.sourceEncoding = proj.getMavenProject().getProperties().getProperty(SOURCE_CODE_ENCODING_PROPERTY);
        info.sourceVersion = proj.getMavenProject().getProperties().getProperty(SOURCE_CODE_VERSION_PROPERTY,
                DEFAULT_SOURCE_CODE_VERSION);
        info.androidSdk = proj.getMavenProject().getProperties().getProperty(ANDROID_PROPERTY);

        return info;
    }

    public static Collection<SourceUnit> findAllSourceUnits(String repoUri) throws IOException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieving source units");
        }

        // step 1 : process all pom.xml files
        Collection<Path> pomFiles = ScanUtil.findMatchingFiles("pom.xml");
        Map<String, BuildAnalysis.BuildInfo> artifacts = new HashMap<>();

        Collection<BuildAnalysis.BuildInfo> infos = new ArrayList<>();
        Collection<Repository> repositories = new HashSet<>();
        for (Path pomFile : pomFiles) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing POM file {}", pomFile.toAbsolutePath());
            }
            try {
                MavenProject project = new MavenProject(pomFile);
                BuildAnalysis.BuildInfo info = createBuildInfo(project);
                infos.add(info);
                artifacts.put(info.getName(), info);
                repositories.addAll(project.getMavenProject().getRepositories());
            } catch (Exception e) {
                LOGGER.warn("Error processing POM file {}", pomFile.toAbsolutePath(), e);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieved source units");
        }

        // step 2: resolve dependencies

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolving dependencies");
        }
        for (BuildAnalysis.BuildInfo info : infos) {
            Collection<RawDependency> externalDeps = new ArrayList<>();
            // if source unit depends on another source units, let's exclude them from the list before
            // trying to resolve, otherwise request may fail
            externalDeps.addAll(info.dependencies.stream().filter(dep ->
                    !artifacts.containsKey(dep.groupID + '/' + dep.artifactID)).
                    collect(Collectors.toList()));
            Collection<Artifact> resolvedArtifacts = resolveArtifactDependencies(externalDeps, repositories, "jar");
            List<String> classPath = new ArrayList<>();
            for (Artifact artifact : resolvedArtifacts) {
                File file = artifact.getFile();
                if (file != null) {
                    classPath.add(file.getAbsolutePath());
                }
            }
            info.classPath = classPath;

            // reading POM files to retrieve SCM repositories
            retrieveRepoUri(externalDeps, repositories);

        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved dependencies");
        }

        // step 3: resolving dependencies between units and updating source path and class path

        Collection<SourceUnit> ret = new ArrayList<>();
        for (BuildAnalysis.BuildInfo info: infos) {
            SourceUnit unit = new SourceUnit();
            unit.Name = info.getName();
            unit.Dir = info.projectDir;
            unit.Files.addAll(info.sources);
            unit.Dependencies = new ArrayList<>(info.dependencies);
            unit.Type = "JavaArtifact";
            unit.Repo = repoUri;
            unit.Data.put("POMFile", info.buildFile);
            unit.Data.put("Description", info.attrs.description);
            unit.Data.put("SourceVersion", info.sourceVersion);
            unit.Data.put("SourceEncoding", info.sourceEncoding);
            Collection<BuildAnalysis.BuildInfo> dependencies = collectDependencies(info.getName(), artifacts);
            Set<String[]> sourcePath = new HashSet<>();
            Collection<String> classPath = new HashSet<>();
            for (BuildAnalysis.BuildInfo dependency : dependencies) {
                sourcePath.addAll(dependency.sourceDirs);
                classPath.addAll(dependency.classPath);
            }
            unit.Data.put("ClassPath", classPath);
            unit.Data.put("SourcePath", sourcePath);
            if (info.androidSdk != null) {
                unit.Data.put("Android", true);
            }
            ret.add(unit);
        }

        return ret;
    }

    /**
     * Retrieves all source files in Maven project
     * @param sourceRoots source roots to search in, i.e. compile source roots, test compile source roots
     */
    private static Set<String> collectSourceFiles(Collection<String> sourceRoots) {

        Set<String> sourceFiles = new HashSet<>();

        for (String sourceRoot : sourceRoots) {
            if (sourceRoot == null) {
                continue;
            }

            Path path = Paths.get(sourceRoot);
            if (!path.toFile().isDirectory()) {
                continue;
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
                sourceFiles.add(Paths.get(sourceRoot, fileName).toString());
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collected source files from {}", path.toAbsolutePath());
            }
        }
        return sourceFiles;
    }

    protected static String getRepoDir() {
        // TODO(sqs): If running in Docker, use a directory not inside the repo if in Docker since the Docker source volume is readonly.
        return REPO_DIR;
    }

    /**
     * Resolves artifact dependencies
     * @param dependencies dependencies to check
     */
    static Collection<Artifact> resolveArtifactDependencies(Collection<RawDependency> dependencies,
                                                            Collection<Repository> repositories,
                                                            String extension) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolving artifact dependencies");
        }

        Collection<Artifact> ret = new HashSet<>();

        List<org.eclipse.aether.graph.Dependency> deps = new ArrayList<>();
        ArtifactTypeRegistry artifactTypeRegistry = repositorySystemSession.getArtifactTypeRegistry();
        for (RawDependency dependency : dependencies) {
            Artifact artifact = new DefaultArtifact(dependency.groupID,
                    dependency.artifactID,
                    dependency.classifier,
                    extension,
                    dependency.version,
                    artifactTypeRegistry.get(dependency.type));
            deps.add(new org.eclipse.aether.graph.Dependency(artifact, dependency.scope));
        }
        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setDependencies(deps);
        List<RemoteRepository> repoz = repositories.stream().
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
            return ret;
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

        ret.addAll(nlg.getDependencies(true).stream().map(org.eclipse.aether.graph.Dependency::getArtifact).
                collect(Collectors.toList()));

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved artifact dependencies");
        }

        return ret;
    }

    /**
     * Collects unique project source directories
     * @param pomFile POM file to process
     * @param proj current project
     * @return collection of source roots of given project, each entry is relative to CWD
     * @throws ModelBuildingException
     */
    private static Collection<String> collectSourceRoots(Path pomFile, MavenProject proj)
            throws ModelBuildingException {
        File root = pomFile.getParent().toFile().getAbsoluteFile();
        Set<String> sourceRoots = new HashSet<>();
        for (String sourceRoot : proj.getMavenProject().getCompileSourceRoots()) {
            File f = PathUtil.concat(root, sourceRoot);
            if (f.isDirectory()) {
                sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
            }
        }
        for (String sourceRoot : proj.getMavenProject().getTestCompileSourceRoots()) {
            File f = PathUtil.concat(root, sourceRoot);
            if (f.isDirectory()) {
                sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
            }
        }

        String sourceRoot = proj.getMavenProject().getBuild().getSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/main";
        }
        File f = PathUtil.concat(root, sourceRoot);
        if (f.isDirectory()) {
            sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
        }

        sourceRoot = proj.getMavenProject().getBuild().getTestSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/test";
        }
        f = PathUtil.concat(root, sourceRoot);
        if (f.isDirectory()) {
            sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
        }

        return sourceRoots;
    }

    /**
     * Gathers BuildInfo objects that represent dependencies of specific artifact.
     * If dependency has sub-dependencies, they will be collected as well resursively
     * @param unitId source unit identifier (group/artifact)
     * @param cache cache that contains build info objects (unitid => buildindo)
     * @return collected objects
     */
    private static Collection<BuildAnalysis.BuildInfo> collectDependencies(String unitId,
                                                              Map<String, BuildAnalysis.BuildInfo> cache) {
        Set<String> visited = new HashSet<>();
        Collection<BuildAnalysis.BuildInfo> infos = new LinkedHashSet<>();
        collectDependencies(unitId, infos, cache, visited);
        return infos;
    }

    /**
     * Recursively collects dependencies of a given unit
     * @param unitId source unit ID to process (group/artifact)
     * @param infos collection to fill with data
     * @param cache cache that contains build info objects (unitid => buildindo)
     * @param visited marks visited units to avoid infinite loops
     */
    private static void collectDependencies(String unitId,
                                            Collection<BuildAnalysis.BuildInfo> infos,
                                            Map<String, BuildAnalysis.BuildInfo> cache,
                                            Set<String> visited) {
        visited.add(unitId);
        BuildAnalysis.BuildInfo info = cache.get(unitId);
        if (info == null) {
            return;
        }
        infos.add(info);

        for (RawDependency dependency : info.dependencies) {
            String depId = dependency.groupID + '/' + dependency.artifactID;
            if (!visited.contains(depId)) {
                collectDependencies(depId, infos, cache, visited);
            }
        }
    }

    /**
     * Fetches POM files for specified dependencies and extract SCM URI if there are any
     * @param dependencies list of dependencies to collect URI for
     * @param repositories repositories to use when looking for external files
     */
    private static void retrieveRepoUri(Collection<RawDependency> dependencies,
                                        Collection<Repository> repositories) {

        Collection<Artifact> pomArtifacts = resolveArtifactDependencies(dependencies, repositories, "pom");

        for (Artifact artifact : pomArtifacts) {
            File file = artifact.getFile();
            if (file != null && file.exists()) {
                MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
                try (FileInputStream inputStream = new FileInputStream(file)) {
                    Model model = xpp3Reader.read(inputStream);
                    Scm scm = model.getScm();
                    if (scm != null) {
                        for (RawDependency rawDependency : dependencies) {
                            if (StringUtils.equals(rawDependency.artifactID, artifact.getArtifactId()) &&
                                    StringUtils.equals(rawDependency.groupID, artifact.getGroupId()) &&
                                    StringUtils.equals(rawDependency.version, artifact.getVersion())) {
                                rawDependency.repoURI = scm.getUrl();
                                break;
                            }
                        }
                    }
                } catch (IOException | XmlPullParserException e) {
                    // ignore
                }
            }
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
