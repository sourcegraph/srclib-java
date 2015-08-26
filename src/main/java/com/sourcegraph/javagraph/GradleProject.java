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
    private static Map<String, BuildAnalysis.BuildInfo> unitCache = new HashMap<>();

    public GradleProject(SourceUnit unit) {
        this.unit = unit;
    }

    public static Map<String, BuildAnalysis.BuildInfo> getGradleAttrs(String repoURI, Path build) throws IOException {
        Map<String, BuildAnalysis.BuildInfo> ret = getBuildInfo(repoURI, build);

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
    public List<String> getBootClassPath() throws Exception {
        return (List<String>) unit.Data.get("BootClassPath");
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

    private static Collection<SourceUnit> createSourceUnits(Path gradleFile,
                                                            String repoURI,
                                                            Set<Path> visited)
            throws IOException, XmlPullParserException {
        Map<String, BuildAnalysis.BuildInfo> infos = getGradleAttrs(repoURI, gradleFile);

        Collection<SourceUnit> ret = new ArrayList<>();

        for (BuildAnalysis.BuildInfo info : infos.values()) {

            for (BuildAnalysis.ProjectDependency projectDependency : info.projectDependencies) {
                if (!StringUtils.isEmpty(projectDependency.buildFile)) {
                    Path p = Paths.get(projectDependency.buildFile).toAbsolutePath().normalize();
                    visited.add(p);
                }
            }

            if (info.sources.isEmpty()) {
                // excluding units without sources
                continue;
            }
            final SourceUnit unit = new SourceUnit();
            unit.Type = "JavaArtifact";
            unit.Name = info.getName();
            Path projectRoot = Paths.get(info.projectDir);
            unit.Dir = info.projectDir;
            if (info.buildFile != null) {
                unit.Data.put("GradleFile", PathUtil.normalize(
                        projectRoot.relativize(Paths.get(info.buildFile)).normalize().toString()));
            } else {
                unit.Data.put("GradleFile", StringUtils.EMPTY);
            }
            unit.Data.put("Description", info.attrs.description);
            if (!StringUtils.isEmpty(info.sourceVersion)) {
                unit.Data.put("SourceVersion", info.sourceVersion);
            }
            if (!StringUtils.isEmpty(info.sourceEncoding)) {
                unit.Data.put("SourceEncoding", info.sourceEncoding);
            }

            if (info.androidSdk != null) {
                unit.Data.put("Android", info.androidSdk);
            }

            // leave only existing files
            unit.Files = new LinkedList<>();
            for (String sourceFile : info.sources) {
                File f = new File(sourceFile);
                if (f.isFile()) {
                    // including only existing files to make 'make' tool happy
                    unit.Files.add(f.getAbsolutePath());
                }
            }
            unit.Dependencies = new ArrayList<>(info.dependencies);
            if (!info.bootClassPath.isEmpty()) {
                unit.Data.put("BootClassPath", new ArrayList<>(info.bootClassPath));
            }
            ret.add(unit);
        }
        return ret;

    }


    public static Collection<SourceUnit> findAllSourceUnits(String repoURI) throws IOException {

        LOGGER.debug("Retrieving source units");

        // putting root gradle file first, it may contain references to all the subprojects
        Set<Path> gradleFiles = new LinkedHashSet<>();
        File rootGradleFile = new File("build.gradle");
        if (rootGradleFile.exists() && !rootGradleFile.isDirectory()) {
            gradleFiles.add(rootGradleFile.toPath().toAbsolutePath().normalize());
        } else {
            // alexsaveliev: trying settings.gradle - build file name may be custom one
            // (see https://github.com/Netflix/archaius)
            rootGradleFile = new File("settings.gradle");
            if (rootGradleFile.exists() && !rootGradleFile.isDirectory()) {
                gradleFiles.add(rootGradleFile.toPath().toAbsolutePath().normalize());
            }
        }

        gradleFiles.addAll(ScanUtil.findMatchingFiles("build.gradle"));
        Set<SourceUnit> units = new LinkedHashSet<>();
        Set<Path> visited = new HashSet<>();
        for (Path gradleFile : gradleFiles) {

            gradleFile = gradleFile.toAbsolutePath().normalize();

            if (visited.contains(gradleFile)) {
                continue;
            }
            LOGGER.debug("Processing Gradle file {}", gradleFile);
            visited.add(gradleFile);

            try {
                units.addAll(createSourceUnits(gradleFile, repoURI, visited));
            } catch (Exception e) {
                LOGGER.warn("An error occurred while processing Gradle file {}",
                        gradleFile, e);
            }
        }

        LOGGER.debug("Resolving source unit dependencies");

        try {
            collectSourceUnitsDependencies(units);
        } catch (Exception e) {
            LOGGER.warn("An error occurred while resolving source unit dependencies", e);
        }

        LOGGER.debug("Resolved source unit dependencies");

        LOGGER.debug("Retrieved source units");
        return units;
    }

    private static Map<String, BuildAnalysis.BuildInfo> getBuildInfo(String repoUri, Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        Map<String, BuildAnalysis.BuildInfo> ret = buildInfoCache.get(path);
        if (ret == null) {
            LOGGER.debug("Collecting meta information from {}", path);
            BuildAnalysis.BuildInfo items[] = BuildAnalysis.Gradle.collectMetaInformation(repoUri,
                    getWrapper(path),
                    path);
            LOGGER.debug("Collected meta information from {}", path);
            ret = new HashMap<>();
            for (BuildAnalysis.BuildInfo info : items) {
                String unitId = info.getName();
                // updating cache for sub-projects too
                if (info.buildFile != null) {
                    Path subProjectPath = Paths.get(info.buildFile).toAbsolutePath().normalize();
                    if (!subProjectPath.equals(path)) {
                        Map<String, BuildAnalysis.BuildInfo> map = buildInfoCache.get(subProjectPath);
                        if (map == null) {
                            map = new HashMap<>();
                            buildInfoCache.put(subProjectPath, map);
                        }
                        map.put(unitId, info);
                    }
                }
                ret.put(unitId, info);
                unitCache.put(unitId, info);
            }
            buildInfoCache.put(path, ret);
        }
        return ret;
    }

    private static Collection<BuildAnalysis.BuildInfo> collectBuildInfo(String unitId) throws IOException {
        Set<String> visited = new HashSet<>();
        Collection<BuildAnalysis.BuildInfo> infos = new LinkedHashSet<>();
        collectBuildInfo(unitId, infos, visited);
        return infos;
    }

    private static void collectBuildInfo(String unitId,
                                         Collection<BuildAnalysis.BuildInfo> infos,
                                         Set<String> visited) throws IOException {
        visited.add(unitId);
        BuildAnalysis.BuildInfo info = unitCache.get(unitId);
        if (info == null) {
            return;
        }
        infos.add(info);

        for (BuildAnalysis.ProjectDependency projectDependency : info.projectDependencies) {
            String depId = projectDependency.artifactID + '/' + projectDependency.groupID;
            if (!visited.contains(depId)) {
                collectBuildInfo(depId, infos, visited);
            }
        }
    }

    private static void collectSourceUnitsDependencies(Collection<SourceUnit> units) throws IOException {
        for (SourceUnit unit : units) {
            Collection<BuildAnalysis.BuildInfo> infos = collectBuildInfo(unit.Name);
            Collection<String> classpathSet = new HashSet<>();

            List<String[]> sourcepath = new ArrayList<>();
            for (BuildAnalysis.BuildInfo info : infos) {
                classpathSet.addAll(info.classPath);
                classpathSet.addAll(info.dependencies.stream().filter(dependency ->
                        !StringUtils.isEmpty(dependency.file)).map(dependency ->
                        dependency.file).
                        collect(Collectors.toList()));
                for (String sourceDir[] : info.sourceDirs) {
                    sourcepath.add(new String[]{sourceDir[0],
                            sourceDir[1],
                            sourceDir[2]});
                }
                for (String path : info.classPath) {
                    File file = new File(path);
                    if (file.isDirectory()) {
                        sourcepath.add(new String[]{unit.Name,
                                info.version,
                                path});
                    }
                }
            }
            List<String> classpath = new ArrayList<>(classpathSet);

            unit.Data.put("ClassPath", classpath);
            unit.Data.put("SourcePath", sourcepath);
        }
    }

}
