package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;
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
        Path current = gradleFile.toAbsolutePath().getParent().toAbsolutePath().normalize();
        while (true) {
            Path p = current.resolve(gradleExe);
            File exeFile = p.toFile();
            if (exeFile.exists() && !exeFile.isDirectory()) {
                return p;
            }
            if (current.startsWith(PathUtil.CWD) && !current.equals(PathUtil.CWD)) {
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
    @SuppressWarnings("unchecked")
    public List<String> getClassPath() throws Exception {
        return (List<String>) unit.Data.get("ClassPath");
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getSourcePath() throws Exception {
        List<List<String>> sourceDirs = (List<List<String>>) unit.Data.get("SourcePath");
        return sourceDirs.stream().map(sourceDir -> sourceDir.get(2)).collect(Collectors.toList());
    }

    @Override
    public String getSourceCodeVersion() throws ModelBuildingException, IOException {
        return (String) unit.Data.get("SourceVersion");
    }

    @Override
    public String getSourceCodeEncoding() throws ModelBuildingException, IOException {
        return (String) unit.Data.get("SourceEncoding");
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        return null;
    }

    public String getGroupId() {
        return (String) unit.Data.get("GroupId");
    }

    private static Collection<SourceUnit> createSourceUnits(Path gradleFile,
                                                            String repoURI,
                                                            Set<Path> visited)
            throws IOException, XmlPullParserException {
        Map<String, BuildAnalysis.BuildInfo> infos = getGradleAttrs(repoURI, gradleFile);

        Collection<SourceUnit> ret = new ArrayList<>();

        for (BuildAnalysis.BuildInfo info : infos.values()) {

            for (String projectDependency : info.projectDependencies) {
                Path p = Paths.get(projectDependency).toAbsolutePath().normalize();
                visited.add(p);
            }

            if (info.sources.isEmpty()) {
                // excluding units without sources
                continue;
            }
            final SourceUnit unit = new SourceUnit();
            unit.Type = "JavaArtifact";
            unit.Name = info.attrs.groupID + "/" + info.attrs.artifactID;
            Path projectRoot = Paths.get(info.projectDir);
            unit.Dir = PathUtil.relativizeCwd(info.projectDir);
            if (info.gradleFile != null) {
                unit.Data.put("GradleFile", PathUtil.normalize(
                        projectRoot.relativize(Paths.get(info.gradleFile)).normalize().toString()));
            } else {
                unit.Data.put("GradleFile", StringUtils.EMPTY);
            }
            unit.Data.put("Description", info.attrs.description);
            unit.Data.put("GroupId", info.attrs.groupID);
            if (!StringUtils.isEmpty(info.sourceVersion)) {
                unit.Data.put("SourceVersion", info.sourceVersion);
            }
            if (!StringUtils.isEmpty(info.sourceEncoding)) {
                unit.Data.put("SourceEncoding", info.sourceEncoding);
            }

            unit.Files = new LinkedList<>();
            for (String sourceFile :info.sources) {
                File f = new File(sourceFile);
                if (f.exists() && !f.isDirectory()) {
                    // including only existing files to make 'make' tool happy
                    unit.Files.add(PathUtil.relativizeCwd(sourceFile));
                }
            }
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

        // putting root gradle file first, it may contain references to all the subprojects
        Set<Path> gradleFiles = new LinkedHashSet<>();
        File rootGradleFile = new File("build.gradle");
        if (rootGradleFile.exists() && !rootGradleFile.isDirectory()) {
            gradleFiles.add(rootGradleFile.toPath().toAbsolutePath().normalize());
        }
        gradleFiles.addAll(ScanUtil.findMatchingFiles("build.gradle"));
        Set<SourceUnit> units = new LinkedHashSet<>();
        Set<Path> visited = new HashSet<>();
        for (Path gradleFile : gradleFiles) {

            gradleFile = gradleFile.toAbsolutePath().normalize();

            if (visited.contains(gradleFile)) {
                continue;
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing Gradle file {}", gradleFile);
            }
            visited.add(gradleFile);

            try {
                units.addAll(createSourceUnits(gradleFile, repoURI, visited));
            } catch (Exception e) {
                LOGGER.warn("An error occurred while processing Gradle file {}",
                        gradleFile, e);
            }
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolving source unit dependencies");
        }

        try {
            collectSourceUnitsDependencies(units);
        } catch (Exception e) {
            LOGGER.warn("An error occurred while resolving source unit dependencies", e);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Resolved source unit dependencies");
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Retrieved source units");
        }
        return units;
    }

    private static Map<String, BuildAnalysis.BuildInfo> getBuildInfo(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        Map<String, BuildAnalysis.BuildInfo> ret = buildInfoCache.get(path);
        if (ret == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collecting meta information from {}", path);
            }
            BuildAnalysis.BuildInfo items[] = BuildAnalysis.Gradle.collectMetaInformation(getWrapper(path), path);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Collected meta information from {}", path);
            }
            ret = new HashMap<>();
            for (BuildAnalysis.BuildInfo info : items) {
                // updating cache for sub-projects too
                if (info.gradleFile != null) {
                    Path subProjectPath = Paths.get(info.gradleFile).toAbsolutePath().normalize();
                    if (!subProjectPath.equals(path)) {
                        Map<String, BuildAnalysis.BuildInfo> map = buildInfoCache.get(subProjectPath);
                        if (map == null) {
                            map = new HashMap<>();
                            buildInfoCache.put(subProjectPath, map);
                        }
                        map.put(info.attrs.groupID + '/' + info.attrs.artifactID, info);
                    }
                }
                ret.put(info.attrs.groupID + '/' + info.attrs.artifactID, info);
            }
            buildInfoCache.put(path, ret);
        }
        return ret;
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
            // mark as visited all sub projects encountered in current gradle file
            if (info.gradleFile != null) {
                visited.add(Paths.get(info.gradleFile).toAbsolutePath().normalize());
            }
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

    private static void collectSourceUnitsDependencies(Collection<SourceUnit> units) throws IOException {
        for (SourceUnit unit : units) {
            Path gradlePath = Paths.get(unit.Dir, (String) unit.Data.get("GradleFile")).toAbsolutePath().normalize();
            Collection<BuildAnalysis.BuildInfo> infos = collectBuildInfo(gradlePath);
            Collection<String> classpath = new LinkedHashSet<>();

            Collection<String[]> sourcepath = new ArrayList<>();
            for (BuildAnalysis.BuildInfo info : infos) {
                classpath.addAll(info.classPath.stream().map(PathUtil::relativizeCwd).
                        collect(Collectors.toList()));
                classpath.addAll(info.dependencies.stream().filter(dependency ->
                        !StringUtils.isEmpty(dependency.file)).map(dependency ->
                        PathUtil.relativizeCwd(dependency.file)).
                        collect(Collectors.toList()));
                for (String sourceDir[] : info.sourceDirs) {
                    sourcepath.add(new String[]{sourceDir[0],
                            sourceDir[1],
                            PathUtil.relativizeCwd(sourceDir[2])});
                }
                for (String path : info.classPath) {
                    File file = new File(path);
                    if (file.isDirectory()) {
                        sourcepath.add(new String[]{unit.Name,
                                info.version,
                                PathUtil.relativizeCwd(path)});
                    }
                }
            }
            unit.Data.put("ClassPath", classpath);
            unit.Data.put("SourcePath", sourcepath);
        }
    }

}
