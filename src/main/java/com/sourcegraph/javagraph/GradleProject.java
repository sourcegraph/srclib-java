package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.building.ModelBuildingException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GradleProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradleProject.class);

    private SourceUnit unit;

    private static Map<Path, Map<String, BuildAnalysis.BuildInfo>> buildInfoCache = new HashMap<>();

    public GradleProject(SourceUnit unit) {
        this.unit = unit;
    }

    public static Map<String, BuildAnalysis.BuildInfo> getGradleAttrs(String repoURI, Path build) throws IOException {
        Map<String, BuildAnalysis.BuildInfo> ret = getBuildInfo(build);

        // HACK: fix the project name inside docker containers. By default, the name of a Gradle project is the name
        // of its containing directory. srclib checks out code to /src inside Docker containers, which makes the name of
        // every Gradle project rooted at the VCS root directory "src". This hack could erroneously change the project
        // name if the name is actually supposed to be "src" (e.g., if the name is set manually).
        if (System.getenv().get("IN_DOCKER_CONTAINER") != null) {
            ret.values().stream().filter(info -> info.attrs.artifactID.equals("src")).forEach(info -> {
                String[] parts = repoURI.split("/");
                info.attrs.artifactID = parts[parts.length - 1];
            });
        }

        return ret;
    }

    private static Path getWrapper(Path gradleFile) {
        // looking for gradle wrapper from build file's path to current working dir
        String gradleExe;
        if (SystemUtils.IS_OS_WINDOWS) {
            gradleExe = "gradlew.bat";
        } else {
            gradleExe = "gradlew";
        }
        Path root = SystemUtils.getUserDir().toPath().toAbsolutePath().normalize();
        Path current = gradleFile.toAbsolutePath().getParent().toAbsolutePath().normalize();
        while (true) {
            Path p = current.resolve(gradleExe);
            File exeFile = p.toFile();
            if (exeFile.exists() && !exeFile.isDirectory()) {
                return p;
            }
            if (current.startsWith(root) && !current.equals(root)) {
                Path parent = current.getParent();
                if (parent == null || parent.equals(current)) {
                    break;
                }
                current = parent;
            } else {
                break;
            }
        }

        return null;
    }

    @Override
    public Set<RawDependency> listDeps() throws Exception {
        BuildAnalysis.BuildInfo info = getBuildInfo(unit);
        return info.dependencies;
    }

    @Override
    public List<String> getClassPath() throws Exception {
        Path path = Paths.get((String) unit.Data.get("GradleFile"));
        Collection<BuildAnalysis.BuildInfo> infos = collectBuildInfo(path);
        Set<String> ret = new LinkedHashSet<>();
        for (BuildAnalysis.BuildInfo info : infos) {
            ret.addAll(info.classPath);
        }
        return new ArrayList<>(ret);
    }

    @Override
    public List<String> getSourcePath() throws Exception {
        Path path = Paths.get((String) unit.Data.get("GradleFile"));
        Collection<BuildAnalysis.BuildInfo> infos = collectBuildInfo(path);
        List<String> ret = new ArrayList<>();
        for (BuildAnalysis.BuildInfo info : infos) {
            if (unit.Name.equals(info.attrs.groupID + '/' + info.attrs.artifactID)) {
                continue;
            }
            ret.addAll(info.sourceDirs);
        }
        return ret;
    }

    @Override
    public String getSourceCodeVersion() throws ModelBuildingException, IOException {
        BuildAnalysis.BuildInfo info = getBuildInfo(unit);
        return info.sourceVersion;
    }

    @Override
    public String getSourceCodeEncoding() throws ModelBuildingException, IOException {
        BuildAnalysis.BuildInfo info = getBuildInfo(unit);
        return info.sourceEncoding;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        return null;
    }

    public BuildAnalysis.POMAttrs getAttributes() throws Exception {
        BuildAnalysis.BuildInfo info = getBuildInfo(unit);
        return info.attrs;
    }

    private static Collection<SourceUnit> createSourceUnits(
            Path gradleFile, String repoURI) throws IOException, XmlPullParserException {
        Map<String, BuildAnalysis.BuildInfo> infos = getGradleAttrs(repoURI, gradleFile);

        Collection<SourceUnit> ret = new ArrayList<>();
        for (BuildAnalysis.BuildInfo info : infos.values()) {
            final SourceUnit unit = new SourceUnit();
            unit.Type = "JavaArtifact";
            unit.Name = info.attrs.groupID + "/" + info.attrs.artifactID;
            Path projectRoot = Paths.get(info.projectDir);
            Path relative = Paths.get(info.rootDir).relativize(projectRoot).normalize();
            unit.Dir = PathUtil.normalize(relative.toString());
            unit.Data.put("GradleFile", PathUtil.normalize(gradleFile.normalize().toString()));
            unit.Data.put("Description", info.attrs.description);
            Set<String> dirs = info.sourceDirs.stream().map(dir ->
                    PathUtil.normalize(projectRoot.relativize(Paths.get(dir)).toString())).collect(Collectors.toSet());
            unit.Data.put("SourceDirs", dirs);

            Set<String> projectDependencies = info.projectDependencies.stream().map(dependency ->
                    PathUtil.normalize(projectRoot.relativize(Paths.get(dependency)).toString())).
                    collect(Collectors.toSet());
            unit.Data.put("ProjectDependencies", projectDependencies);

            unit.Files = new LinkedList<>();
            unit.Files.addAll(info.sources.stream().map(file ->
                    PathUtil.normalize(projectRoot.relativize(Paths.get(file)).toString())).collect(Collectors.toList()));
            unit.sortFiles();

            // This will list all dependencies, not just direct ones.
            unit.Dependencies = new ArrayList<>(info.dependencies);
            ret.add(unit);
        }
        return ret;

    }


    public static Collection<SourceUnit> findAllSourceUnits(String repoURI) throws IOException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieving source units");
        }

        HashSet<Path> gradleFiles = ScanUtil.findMatchingFiles("build.gradle");
        Set<SourceUnit> units = new LinkedHashSet<>();
        for (Path gradleFile : gradleFiles) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing Gradle file {}", gradleFile.toAbsolutePath());
            }

            try {
                units.addAll(createSourceUnits(gradleFile, repoURI));
            } catch (Exception e) {
                LOGGER.warn("An error occurred while processing Gradle file {}", gradleFile.toAbsolutePath(), e);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieved source units");
        }
        return units;
    }

    private static Map<String, BuildAnalysis.BuildInfo> getBuildInfo(Path path) throws IOException {
        Map<String, BuildAnalysis.BuildInfo> ret = buildInfoCache.get(path);
        if (ret == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collecting meta information from {}", path.toAbsolutePath().normalize());
            }
            BuildAnalysis.BuildInfo items[] = BuildAnalysis.Gradle.collectMetaInformation(getWrapper(path), path);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collected meta information from {}", path.toAbsolutePath().normalize());
            }
            ret = new HashMap<>();
            for (BuildAnalysis.BuildInfo info : items) {
                ret.put(info.attrs.groupID + '/' + info.attrs.artifactID, info);
            }
            buildInfoCache.put(path, ret);
        }
        return ret;
    }

    private static BuildAnalysis.BuildInfo getBuildInfo(SourceUnit unit) throws IOException {
        Path path = Paths.get((String) unit.Data.get("GradleFile"));
        return getBuildInfo(path).get(unit.Name);
    }

    private static Collection<BuildAnalysis.BuildInfo> collectBuildInfo(Path build) throws IOException {
        Set<Path> visited = new HashSet<>();
        Collection<BuildAnalysis.BuildInfo> infos = new LinkedHashSet<>();
        collectBuildInfo(build, infos, visited);
        return infos;
    }

    private static void collectBuildInfo(Path build,
                                         Collection<BuildAnalysis.BuildInfo> infos,
                                         Set<Path> visited) throws  IOException {
        visited.add(build.toAbsolutePath().normalize());
        Map<String, BuildAnalysis.BuildInfo> ret = getBuildInfo(build);
        if (ret == null) {
            return;
        }
        for (BuildAnalysis.BuildInfo info : ret.values()) {
            infos.add(info);
            for (String gradleFile : info.projectDependencies) {
                Path root = Paths.get(info.projectDir).toAbsolutePath().normalize();
                build = root.resolve(gradleFile).toAbsolutePath().normalize();
                if (!visited.contains(build)) {
                    collectBuildInfo(build, infos, visited);
                }
            }
        }
    }

}
