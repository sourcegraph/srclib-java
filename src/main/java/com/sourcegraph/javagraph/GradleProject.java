package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * List of rules to compile and graph Gradle-based project. All settings are extracted at the 'scan' phase and stored in
 * the unit's data. Later, at the 'graph' phase they are extracted from cached data
 */
public class GradleProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(GradleProject.class);

    private SourceUnit unit;

    /**
     * Maps gradle build files path to list of build info associated with a given build file. Each build file refers
     * to map source unit name -> build info.
     */
    private static Map<Path, Map<String, BuildAnalysis.BuildInfo>> buildInfoCache = new HashMap<>();
    /**
     * Maps source unit name to build info
     */
    private static Map<String, BuildAnalysis.BuildInfo> unitCache = new HashMap<>();

    public GradleProject(SourceUnit unit) {
        this.unit = unit;
    }

    /**
     * Extracts all artifacts from a given Gradle build file
     * @param build Gradle build file location
     * @return map unit name -> build info
     * @throws IOException
     */
    public static Map<String, BuildAnalysis.BuildInfo> getGradleAttrs(Path build) throws IOException {
        return getBuildInfo(build);
    }

    /**
     * @param gradleFile Gradle build file to process
     * @return best suitable Gradle command (gradlew in top directory if there is any or regular gradle)
     */
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

    /**
     * @return cached bootstrap class path from unit data
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getBootClassPath() {
        return (List<String>) unit.Data.get("BootClassPath");
    }

    /**
     * @return cached class path from unit data
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getClassPath() {
        return (List<String>) unit.Data.get("ClassPath");
    }

    /**
     * @return cached source path from unit data
     */
    @Override
    @SuppressWarnings("unchecked")
    public List<String> getSourcePath() {
        List<List<String>> sourceDirs = (List<List<String>>) unit.Data.get("SourcePath");
        return sourceDirs.stream().map(sourceDir -> sourceDir.get(2)).collect(Collectors.toList());
    }

    /**
     * @return cached source code version
     */
    @Override
    public String getSourceCodeVersion() {
        return (String) unit.Data.get("SourceVersion");
    }

    /**
     * @return cached source code encoding
     */
    @Override
    public String getSourceCodeEncoding() {
        return (String) unit.Data.get("SourceEncoding");
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        for (RawDependency dependency : unit.Dependencies) {
            if (dependency.file != null && jarFile.equals(PathUtil.CWD.resolve(dependency.file))) {
                return dependency;
            }
        }
        return null;
    }

    public static boolean is(SourceUnit unit) {
        return unit.Data.containsKey("GradleFile");
    }

    /**
     * Collects all source units in a given Gradle build file
     * @param gradleFile Gradle build file to process
     * @param visited holds all visited build files to avoid infinite loops when we scanning repository for build files
     * because some build files may be already taken into account by including them in parent's build file
     * @return list of source units collected
     * @throws IOException
     * @throws XmlPullParserException
     */
    private static Collection<SourceUnit> createSourceUnits(Path gradleFile,
                                                            Set<Path> visited)
            throws IOException, XmlPullParserException {
        Map<String, BuildAnalysis.BuildInfo> infos = getGradleAttrs(gradleFile);

        Collection<SourceUnit> ret = new ArrayList<>();

        for (BuildAnalysis.BuildInfo info : infos.values()) {

            for (BuildAnalysis.ProjectDependency projectDependency : info.projectDependencies) {
                if (!StringUtils.isEmpty(projectDependency.buildFile)) {
                    Path p = PathUtil.CWD.resolve(projectDependency.buildFile).toAbsolutePath().normalize();
                    visited.add(p);
                }
            }

            if (info.sources.isEmpty()) {
                // excluding units without sources
                continue;
            }
            final SourceUnit unit = new SourceUnit();
            unit.Type = SourceUnit.DEFAULT_TYPE;
            unit.Name = info.getName();
            Path projectRoot = PathUtil.CWD.resolve(info.projectDir);
            unit.Dir = info.projectDir;
            if (info.buildFile != null) {
                unit.Data.put("GradleFile", PathUtil.normalize(
                        projectRoot.relativize(PathUtil.CWD.resolve(info.buildFile)).normalize().toString()));
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
                File f = PathUtil.CWD.resolve(sourceFile).toFile();
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


    /**
     * Collects all source units from all Gradle build files in current working directory
     * @return collection of source units
     * @throws IOException
     */
    public static Collection<SourceUnit> findAllSourceUnits() throws IOException {

        LOGGER.debug("Retrieving source units");

        // putting root gradle file first, it may contain references to all the subprojects
        Set<Path> gradleFiles = new LinkedHashSet<>();
        File rootGradleFile = PathUtil.CWD.resolve("build.gradle").toFile();
        if (rootGradleFile.isFile()) {
            gradleFiles.add(rootGradleFile.toPath().toAbsolutePath().normalize());
        } else {
            // alexsaveliev: trying settings.gradle - build file name may be custom one
            // (see https://github.com/Netflix/archaius)
            rootGradleFile = PathUtil.CWD.resolve("settings.gradle").toFile();
            if (rootGradleFile.isFile()) {
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
                units.addAll(createSourceUnits(gradleFile, visited));
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

    /**
     * Retrieving build information from a given build file
     * @param path path to build file
     * @return map (source unit id -> build info) extracted by meta information script
     * @throws IOException
     */
    private static Map<String, BuildAnalysis.BuildInfo> getBuildInfo(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        Map<String, BuildAnalysis.BuildInfo> ret = buildInfoCache.get(path);
        if (ret == null) {
            LOGGER.debug("Collecting meta information from {}", path);
            BuildAnalysis.BuildInfo items[] = BuildAnalysis.Gradle.collectMetaInformation(getWrapper(path),
                    path);
            LOGGER.debug("Collected meta information from {}", path);
            ret = new HashMap<>();
            for (BuildAnalysis.BuildInfo info : items) {
                String unitId = info.getName();
                // updating cache for sub-projects too
                if (info.buildFile != null) {
                    Path subProjectPath = PathUtil.CWD.resolve(info.buildFile).toAbsolutePath().normalize();
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

    /**
     * Collects all build info from given source unit and its dependencies
     * @param unitId unit ID to collect all dependencies for
     * @return list of build info that includes self and all dependencies
     * @throws IOException
     */
    private static Collection<BuildAnalysis.BuildInfo> collectBuildInfo(String unitId) throws IOException {
        Set<String> visited = new HashSet<>();
        Collection<BuildAnalysis.BuildInfo> infos = new LinkedHashSet<>();
        collectBuildInfo(unitId, infos, visited);
        return infos;
    }

    /**
     * Collects all build info from given source unit and its dependencies
     * @param unitId unit ID to collect all dependencies for
     * @param infos collection to fill with found information
     * @param visited set to control visited source units to avoid infinite loops
     * @throws IOException
     */
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
        // alexsaveliev: github.com/elastic/elasticsearch declares project dependencies as external
        // in order to resolve it, we'll check if there is an source unit with the same name and version
        // as declared in external dependency and use unit's sourcepath if found
        for (RawDependency raw : info.dependencies) {
            String id = raw.groupID + '/' + raw.artifactID;
            info = unitCache.get(id);
            if (info != null && info.version.equals(raw.version)) {
                if (!visited.contains(id)) {
                    collectBuildInfo(id, infos, visited);
                }
            }
        }
    }

    /**
     * Collects source unit dependencies, updates classpath and sourcepath of each source unit based on dependencies
     * @param units units to process
     * @throws IOException
     */
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
                    File file = PathUtil.CWD.resolve(path).toFile();
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
