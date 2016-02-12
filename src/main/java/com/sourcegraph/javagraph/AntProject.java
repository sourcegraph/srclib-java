package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.Javac;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class AntProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntProject.class);

    static final String BUILD_XML_PROPERTY = "BuildXML";

    private SourceUnit unit;


    public AntProject(SourceUnit unit) {
        this.unit = unit;
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
    public RawDependency getDepForJAR(Path jarFile) {
        return MavenCentralUtils.searchInCentral(jarFile);
    }

    /**
     * Retrieves all source units from current working directory by scanning for build.xml files and processing them
     *
     * @return all source units collected
     * @throws IOException
     */
    public static Collection<SourceUnit> findAllSourceUnits() throws IOException {

        LOGGER.debug("Retrieving source units");

        // step 1 : process all pom.xml files
        Collection<Path> buildXmlFiles = ScanUtil.findMatchingFiles("build.xml");

        Collection<SourceUnit> ret = new ArrayList<>();

        for (Path buildXml : buildXmlFiles) {

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Processing Ant file {}", buildXml.toAbsolutePath());
            }
            try {
                ret.add(getSourceUnit(buildXml));
            } catch (Exception e) {
                LOGGER.warn("Error processing Ant file {}", buildXml.toAbsolutePath(), e);
            }
        }

        LOGGER.debug("Retrieved source units");

        return ret;
    }

    public static boolean is(SourceUnit unit) {
        return unit.Data.containsKey(BUILD_XML_PROPERTY);
    }

    private static SourceUnit getSourceUnit(Path buildXml) {

        LOGGER.debug("Processing {}", buildXml);
        org.apache.tools.ant.Project project = new org.apache.tools.ant.Project();
        project.init();

        project.setUserProperty(MagicNames.ANT_FILE, buildXml.toAbsolutePath().toString());
        project.setUserProperty(MagicNames.ANT_FILE_TYPE, MagicNames.ANT_FILE_TYPE_FILE);

        ProjectHelper.configureProject(project, buildXml.toFile());

        ComponentHelper componentHelper = ComponentHelper.getComponentHelper(project);
        componentHelper.addTaskDefinition("javac", NoopJavac.class);

        Collection<String> files = new HashSet<>();
        Collection<String[]> sourcePath = new LinkedList<>();
        Collection<String> classPath = new LinkedList<>();
        Collection<String> bootClassPath = new LinkedList<>();

        String sourceVersion = null;
        String sourceEncoding = null;

        SourceUnit unit = new SourceUnit();
        unit.Files = new LinkedList<>();
        unit.Name = project.getName();
        unit.Dir = buildXml.getParent().toString();
        unit.Type = SourceUnit.DEFAULT_TYPE;
        unit.Data.put(BUILD_XML_PROPERTY, buildXml.toString());

        for (Target target : project.getTargets().values()) {
            LOGGER.debug("Processing target {}", target.getName());
            for (Task task : target.getTasks()) {

                String taskType = task.getTaskType();
                if (!"javac".equals(taskType)) {
                    continue;
                }
                task.getRuntimeConfigurableWrapper().setProxy(componentHelper.createComponent(taskType));
                task.maybeConfigure();
                NoopJavac javac = (NoopJavac) task.getRuntimeConfigurableWrapper().getProxy();
                javac.execute();

                String source = javac.getSource();
                if (source != null && !source.equals(sourceVersion)) {
                    if (sourceVersion != null) {
                        LOGGER.warn("Multiple source code versions detected: {} and {}", sourceVersion, source);
                    } else {
                        sourceVersion = source;
                    }
                }

                String encoding = javac.getEncoding();
                if (encoding != null && !encoding.equals(sourceEncoding)) {
                    if (sourceEncoding != null) {
                        LOGGER.warn("Multiple source code encodings detected: {} and {}", sourceEncoding, encoding);
                    } else {
                        sourceEncoding = encoding;
                    }
                }

                for (File file : javac.getCompileList()) {
                    files.add(file.getPath());
                }
                for (String item : getPathItems(javac.getSourcepath())) {
                    sourcePath.add(new String[]{StringUtils.EMPTY, StringUtils.EMPTY, item});
                }
                classPath.addAll(getPathItems(javac.getClasspath()));
                bootClassPath.addAll(getPathItems(javac.getBootclasspath()));
            }
        }

        unit.Files = new LinkedList<>(files);

        for (String item : classPath) {
            File file = new File(item);
            if (file.isFile()) {
                RawDependency dependency = MavenCentralUtils.searchInCentral(file.toPath());
                if (dependency != null) {
                    unit.Dependencies.add(dependency);
                }
            }
        }
        unit.Data.put("SourceVersion", sourceVersion);
        unit.Data.put("SourceEncoding", sourceEncoding);
        unit.Data.put("SourcePath", sourcePath);
        unit.Data.put("ClassPath", classPath);
        if (!bootClassPath.isEmpty()) {
            unit.Data.put("BootClassPath", bootClassPath);
        }
        return unit;
    }

    private static Collection<String> getPathItems(org.apache.tools.ant.types.Path path) {
        if (path == null) {
            return Collections.emptyList();
        }
        Collection<String> ret = new LinkedList<>();
        Collections.addAll(ret, path.list());
        return ret;
    }

    public static class NoopJavac extends Javac {

        @Override
        protected void compile() {
            // NOOP
        }

        @Override
        protected void checkParameters() throws BuildException {
            // NOOP
        }

        protected File[] getCompileList() {
            return compileList;
        }
    }

}
