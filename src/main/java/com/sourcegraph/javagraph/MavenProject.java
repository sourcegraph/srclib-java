package com.sourcegraph.javagraph;

import com.google.common.collect.Iterators;
import com.sourcegraph.javagraph.maven.plugins.MavenPlugins;
import org.apache.commons.io.IOUtils;
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
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class MavenProject implements Project {

    public static final String SOURCE_CODE_VERSION_PROPERTY = "srclib-source-code-version";
    public static final String SOURCE_CODE_ENCODING_PROPERTY = "srclib-source-code-encoding";
    public static final String ANDROID_PROPERTY = "srclib-android";

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenProject.class);

    /**
     * Maven default source setting is 1.5, see http://maven.apache.org/plugins/maven-compiler-plugin/
     */
    private static final String DEFAULT_SOURCE_CODE_VERSION = "1.5";

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

    /**
     * Initializes Maven's local repository system and session
     */
    private static void initRepositorySystem() {
        repositorySystem = newRepositorySystem();
        repositorySystemSession = newRepositorySystemSession(repositorySystem);
    }

    private transient org.apache.maven.project.MavenProject mavenProject;

    /**
     * Fetches and parses POM file if necessary, applies processing plugins
     *
     * @return maven project data
     * @throws ModelBuildingException
     */
    protected org.apache.maven.project.MavenProject getMavenProject() throws ModelBuildingException {
        if (mavenProject == null) {
            LOGGER.debug("Building Maven project structure from {}", pomFile);
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
            LOGGER.debug("Maven project structure is built", pomFile);
            // applying all registered plugins to adjust project data
            MavenPlugins.getInstance().apply(mavenProject, PathUtil.CWD.resolve(getRepoDir()).toFile());
        }
        return mavenProject;
    }

    /**
     * Initializes repository system
     *
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
     *
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

        LOGGER.debug("Retrieving Maven dependencies");

        Set<RawDependency> deps = new HashSet<>();
        List<Dependency> mavenDeps = getMavenProject().getDependencies();
        for (Dependency d : mavenDeps) {
            RawDependency rawDependency = new RawDependency(d.getGroupId(),
                    d.getArtifactId(),
                    d.getVersion(),
                    d.getScope(),
                    null);
            rawDependency.classifier = d.getClassifier();
            rawDependency.type = d.getType();
            rawDependency.repoURI = null;
            deps.add(rawDependency);
        }

        LOGGER.debug("Retrieved Maven dependencies");

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
    public List<String> getBootClassPath() {
        // simply looking in the unit's data, bootstrap classpath was collected at the "scan" phase
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
    public RawDependency getDepForJAR(Path jarFile) {
        for (RawDependency dependency : unit.Dependencies) {
            if (dependency.file != null &&
                    jarFile.equals(PathUtil.CWD.resolve(dependency.file).toAbsolutePath())) {
                return dependency;
            }
        }
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
     *
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
        info.buildFile = proj.pomFile.toAbsolutePath().normalize().toString();
        info.dependencies = proj.listDeps();
        info.version = proj.getMavenProject().getVersion();
        info.projectDir = proj.pomFile.getParent().toString();
        info.projectDependencies = new ArrayList<>();
        for (String module : proj.getMavenProject().getModules()) {
            info.projectDependencies.add(new BuildAnalysis.ProjectDependency(StringUtils.EMPTY,
                    StringUtils.EMPTY,
                    PathUtil.concat(PathUtil.CWD.resolve(proj.pomFile.getParent().toString()), module).
                            resolve("pom.xml").
                            toAbsolutePath().
                            normalize().
                            toString()));
        }

        Collection<String> sourceRoots = collectSourceRoots(proj.pomFile, proj);
        info.sourceDirs = sourceRoots.stream().map(sourceRoot ->
                new String[]{info.getName(), info.version, sourceRoot}).collect(Collectors.toList());
        info.sources = collectSourceFiles(sourceRoots);
        info.sourceEncoding = proj.getMavenProject().getProperties().getProperty(SOURCE_CODE_ENCODING_PROPERTY);
        info.sourceVersion = proj.getMavenProject().getProperties().getProperty(SOURCE_CODE_VERSION_PROPERTY,
                DEFAULT_SOURCE_CODE_VERSION);
        info.androidSdk = proj.getMavenProject().getProperties().getProperty(ANDROID_PROPERTY);

        return info;
    }

    /**
     * Retrieves all source units from current working directory by scanning for pom.xml files and processing them
     *
     * @return all source units collected
     * @throws IOException
     */
    public static Collection<SourceUnit> findAllSourceUnits() throws IOException {

        LOGGER.debug("Retrieving source units");

        // step 1 : process all pom.xml files
        Collection<Path> pomFiles = ScanUtil.findMatchingFiles("pom.xml");
        Map<String, BuildAnalysis.BuildInfo> artifactsByUnitId = new HashMap<>();
        Map<String, String> unitsByPomFile = new HashMap<>();

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
                artifactsByUnitId.put(info.getName() + '/' + info.version, info);
                unitsByPomFile.put(info.buildFile, info.getName() + '/' + info.version);
                repositories.addAll(project.getMavenProject().getRepositories());
            } catch (Exception e) {
                LOGGER.warn("Error processing POM file {}", pomFile.toAbsolutePath(), e);
            }
        }

        LOGGER.debug("Retrieved source units");

        // step 2: resolve dependencies

        for (BuildAnalysis.BuildInfo info : infos) {
            Collection<RawDependency> externalDeps = new ArrayList<>();
            // if source unit depends on another source units, let's exclude them from the list before
            // trying to resolve, otherwise request may fail
            externalDeps.addAll(info.dependencies.stream().filter(dep ->
                    !artifactsByUnitId.containsKey(dep.groupID + '/' + dep.artifactID + '/' + dep.version)).
                    collect(Collectors.toList()));
            // reading POM files to retrieve SCM repositories
            retrieveRepoUri(info.attrs.groupID + '/' + info.attrs.artifactID, externalDeps, repositories);

        }

        // step 3: resolving dependencies between units and updating source path and class path

        Collection<SourceUnit> ret = new ArrayList<>();
        for (BuildAnalysis.BuildInfo info : infos) {
            SourceUnit unit = new SourceUnit();
            unit.Files = new LinkedList<>();
            unit.Name = info.getName();
            unit.Dir = info.projectDir;
            unit.Files.addAll(info.sources);
            unit.Dependencies = new ArrayList<>(info.dependencies);
            unit.Type = SourceUnit.DEFAULT_TYPE;
            unit.Data.put("POMFile", info.buildFile);
            unit.Data.put("Description", info.attrs.description);
            unit.Data.put("SourceVersion", info.sourceVersion);
            unit.Data.put("SourceEncoding", info.sourceEncoding);
            try {
                unit.Data.put("POM", org.json.XML.toJSONObject(IOUtils.toString(new FileInputStream(info.buildFile))));
            } catch (Exception e) {
                LOGGER.warn("Unable to embed POM object into the {} unit data", unit.Name, e);
            }
            Collection<BuildAnalysis.BuildInfo> dependencies = collectDependencies(info.getName() + '/' + info.version,
                    artifactsByUnitId,
                    unitsByPomFile);
            Set<String[]> sourcePath = new HashSet<>();
            Collection<RawDependency> allDependencies = new ArrayList<>();
            for (BuildAnalysis.BuildInfo dependency : dependencies) {
                allDependencies.addAll(dependency.dependencies);
                sourcePath.addAll(dependency.sourceDirs);
            }
            Collection<RawDependency> externalDeps = new ArrayList<>();
            // if source unit depends on another source units, let's exclude them from the list before
            // trying to resolve, otherwise request may fail
            externalDeps.addAll(allDependencies.stream().filter(dep ->
                    !artifactsByUnitId.containsKey(dep.groupID + '/' + dep.artifactID + '/' + dep.version)).
                    collect(Collectors.toList()));
            LOGGER.debug("Resolving artifacts for {} [{}]", unit.Name, info.buildFile);
            Collection<Artifact> resolvedArtifacts = resolveDependencyArtifacts(unit.Name,
                    externalDeps,
                    repositories,
                    "jar");
            LOGGER.debug("Resolved artifacts for {} [{}]", unit.Name, info.buildFile);
            List<String> classPath = new ArrayList<>();
            for (Artifact artifact : resolvedArtifacts) {
                File file = artifact.getFile();
                if (file != null) {
                    classPath.add(file.getAbsolutePath());
                    // updating unit dependencies with files after resolution
                    for (RawDependency rawDependency : unit.Dependencies) {
                        if (rawDependency.artifactID.equals(artifact.getArtifactId()) &&
                                rawDependency.groupID.equals(artifact.getGroupId()) &&
                                rawDependency.version.equals(artifact.getVersion())) {
                            rawDependency.file = file.toString();
                            break;
                        }
                    }
                }
            }
            unit.Data.put("ClassPath", classPath);
            unit.Data.put("SourcePath", sourcePath);
            if (info.androidSdk != null) {
                unit.Data.put(SourceUnit.ANDROID_MARKER, true);
            }
            ret.add(unit);
        }

        return ret;
    }

    public static boolean is(SourceUnit unit) {
        return unit.Data.containsKey("POMFile");
    }

    /**
     * Retrieves all source files in Maven project
     *
     * @param sourceRoots source roots to search in, i.e. compile source roots, test compile source roots
     */
    private static Set<String> collectSourceFiles(Collection<String> sourceRoots) {

        Set<String> sourceFiles = new HashSet<>();

        for (String sourceRoot : sourceRoots) {
            if (sourceRoot == null) {
                continue;
            }

            Path path = PathUtil.CWD.resolve(sourceRoot);
            if (!path.toFile().isDirectory()) {
                continue;
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collecting source files from {}", path.toAbsolutePath());
            }

            final DirectoryScanner directoryScanner = new DirectoryScanner();
            directoryScanner.setIncludes(new String[]{"**/*.java"});
            directoryScanner.setExcludes(null);
            directoryScanner.setBasedir(path.toString());
            directoryScanner.scan();
            for (String fileName : directoryScanner.getIncludedFiles()) {
                sourceFiles.add(PathUtil.concat(path, fileName).toString());
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collected source files from {}", path.toAbsolutePath());
            }
        }
        return sourceFiles;
    }

    /**
     * @return location of Maven's local repository
     */
    protected static String getRepoDir() {
        return REPO_DIR;
    }

    /**
     * Resolves dependency artifacts
     *
     * @param unitId       source unit ID
     * @param dependencies dependencies to check
     * @param repositories repositories to search artifacts in
     * @param extension    artifact extension (jar, pom, etc)
     */
    static Collection<Artifact> resolveDependencyArtifacts(String unitId,
                                                           Collection<RawDependency> dependencies,
                                                           Collection<Repository> repositories,
                                                           String extension) {

        LOGGER.debug("Resolving dependency artifacts");

        Collection<Artifact> ret = new ArrayList<>();

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
            // TODO
            LOGGER.warn("Failed to collect dependencies for {} - {}", unitId, e.getMessage(), e);
            node = e.getResult().getRoot();
        }
        LOGGER.debug("Collected dependencies");
        if (node == null) {
            LOGGER.warn("Failed to collect dependencies for {} - no dependencies were collected", unitId);
            return ret;
        }

        DependencyRequest projectDependencyRequest = new DependencyRequest(node, null);
        try {
            repositorySystem.resolveDependencies(repositorySystemSession, projectDependencyRequest);
        } catch (DependencyResolutionException e) {
            LOGGER.warn("Failed to resolve dependencies for {} - {}", unitId, e.getMessage());
        }
        LOGGER.debug("Resolved dependencies");

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);

        ret.addAll(nlg.getDependencies(true).stream().map(org.eclipse.aether.graph.Dependency::getArtifact).
                collect(Collectors.toList()));

        LOGGER.debug("Resolved dependency artifacts");

        return ret;
    }

    /**
     * Collects unique project source directories
     *
     * @param pomFile POM file to process
     * @param proj    current project
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
                LOGGER.debug("Adding source root {}", f);
                sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
            }
        }
        for (String sourceRoot : proj.getMavenProject().getTestCompileSourceRoots()) {
            File f = PathUtil.concat(root, sourceRoot);
            if (f.isDirectory()) {
                LOGGER.debug("Adding source root {}", f);
                sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
            }
        }

        String sourceRoot = proj.getMavenProject().getBuild().getSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/main";
        }
        File f = PathUtil.concat(root, sourceRoot);
        if (f.isDirectory()) {
            LOGGER.debug("Adding source root {}", f);
            sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
        }

        sourceRoot = proj.getMavenProject().getBuild().getTestSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/test";
        }
        f = PathUtil.concat(root, sourceRoot);
        if (f.isDirectory()) {
            LOGGER.debug("Adding source root {}", f);
            sourceRoots.add(PathUtil.relativizeCwd(f.toString()));
        }

        return sourceRoots;
    }

    /**
     * Gathers BuildInfo objects that represent dependencies of specific artifact.
     * If dependency has sub-dependencies, they will be collected as well resursively
     *
     * @param unitId    source unit identifier (group/artifact/version)
     * @param unitCache cache that contains build info objects (unitid => buildindo)
     * @param pomCache  cache that contains build info objects (POM file => unitid)
     * @return collected objects
     */
    private static Collection<BuildAnalysis.BuildInfo> collectDependencies(
            String unitId,
            Map<String, BuildAnalysis.BuildInfo> unitCache,
            Map<String, String> pomCache) {
        Set<String> visited = new HashSet<>();
        Collection<BuildAnalysis.BuildInfo> infos = new LinkedHashSet<>();
        collectDependencies(unitId, infos, unitCache, pomCache, visited);
        return infos;
    }

    /**
     * Recursively collects dependencies of a given unit
     *
     * @param unitId    source unit ID to process (group/artifact/version)
     * @param infos     collection to fill with data
     * @param unitCache cache that contains build info objects (unitid => buildindo)
     * @param pomCache  cache that contains build info objects (POM file => unitid)
     * @param visited   marks visited units to avoid infinite loops
     */
    private static void collectDependencies(String unitId,
                                            Collection<BuildAnalysis.BuildInfo> infos,
                                            Map<String, BuildAnalysis.BuildInfo> unitCache,
                                            Map<String, String> pomCache,
                                            Set<String> visited) {
        visited.add(unitId);
        BuildAnalysis.BuildInfo info = unitCache.get(unitId);
        if (info == null) {
            return;
        }
        infos.add(info);

        for (RawDependency dependency : info.dependencies) {
            String depId = dependency.groupID + '/' + dependency.artifactID + '/' + dependency.version;
            if (!visited.contains(depId)) {
                collectDependencies(depId, infos, unitCache, pomCache, visited);
            }
        }

        for (BuildAnalysis.ProjectDependency dependency : info.projectDependencies) {
            unitId = pomCache.get(dependency.buildFile);
            if (unitId != null) {
                if (!visited.contains(unitId)) {
                    collectDependencies(unitId, infos, unitCache, pomCache, visited);
                }
            }
        }
    }

    /**
     * Fetches POM files for specified dependencies and extract SCM URI if there are any
     *
     * @param dependencies list of dependencies to collect URI for
     * @param repositories repositories to use when looking for external files
     */
    private static void retrieveRepoUri(String unitId,
                                        Collection<RawDependency> dependencies,
                                        Collection<Repository> repositories) {

        Collection<Artifact> pomArtifacts = resolveDependencyArtifacts(unitId, dependencies, repositories, "pom");

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

    /**
     * Resolves Maven models
     */
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
