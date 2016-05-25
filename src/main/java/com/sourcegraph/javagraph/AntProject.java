package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;
import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.AntlibDefinition;
import org.apache.tools.ant.taskdefs.Javac;
import org.apache.tools.ant.taskdefs.MacroInstance;
import org.apache.tools.ant.taskdefs.UpToDate;
import org.apache.tools.ant.taskdefs.optional.javacc.JavaCC;
import org.apache.tools.ant.types.FileSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class AntProject implements Project {

    private static final Logger LOGGER = LoggerFactory.getLogger(AntProject.class);

    private SourceUnit unit;

    /**
     * Keeps mapping between JAR files and dependencies computed via JAR's SHA1
     */
    private static Map<Path, RawDependency> dependencyCache = new HashMap<>();

    /**
     * This set keeps known Ant types that might be executed while collecting data
     */
    private static Set<String> executables = new HashSet<>();

    static {

        executables.add("property");
        executables.add("basename");
        executables.add("condition");
        executables.add("dirname");
        executables.add("import");
        executables.add("loadproperties");
        executables.add("xmlproperty");
        executables.add("and");
        executables.add("antversion");
        executables.add("contains");
        executables.add("equals");
        executables.add("filesmatch");
        executables.add("hasfreespace");
        executables.add("hasmethod");
        executables.add("isfailure");
        executables.add("isfalse");
        executables.add("isfileselected");
        executables.add("islastmodified");
        executables.add("isreference");
        executables.add("isset");
        executables.add("issigned");
        executables.add("istrue");
        executables.add("matches");
        executables.add("not");
        executables.add("or");
        executables.add("os");
        executables.add("parsersupports");
        executables.add("resourcecontains");
        executables.add("resourceexists");
        executables.add("resourcesmatch");
        executables.add("typefound");
        executables.add("xor");
        executables.add("available");
        executables.add("uptodate");
        executables.add("fileset");
        executables.add("path");
        executables.add("get");
        executables.add("mkdir");
        executables.add("javacc");
    }

    private static ErrorTolerantJavac lastJavac;

    public AntProject(SourceUnit unit) {
        this.unit = unit;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> getClassPath() {
        // simply looking in the unit's data, classpath was collected at the "scan" phase
        return unit.Data.ClassPath;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> getBootClassPath() {
        // simply looking in the unit's data, bootstrap classpath was collected at the "scan" phase
        return unit.Data.BootClassPath;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Collection<String> getSourcePath() {
        // simply looking in the unit's data, sourcepath was collected at the "scan" phase
        return unit.Data.SourcePath.stream().map(element -> element.filePath).collect(Collectors.toList());
    }

    @Override
    public String getSourceCodeVersion() {
        // simply looking in the unit's data, source version was returieved at the "scan" phase
        return unit.Data.SourceVersion;
    }

    @Override
    public String getSourceCodeEncoding() {
        // simply looking in the unit's data, source encoding was returieved at the "scan" phase
        return unit.Data.SourceEncoding;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) {
        for (RawDependency dependency : unit.Data.Dependencies) {
            if (dependency.file != null &&
                    jarFile.equals(PathUtil.CWD.resolve(dependency.file).toAbsolutePath())) {
                return dependency;
            }
        }
        return null;
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
        return unit.Data.BuildXML != null;
    }

    /**
     * Constructs source unit based on given build.xml
     *
     * @param buildXml location of build.xml file
     * @return source unit
     */
    private static SourceUnit getSourceUnit(Path buildXml) {

        LOGGER.info("Processing {}", buildXml);
        org.apache.tools.ant.Project project = new org.apache.tools.ant.Project();
        project.init();

        project.setUserProperty(MagicNames.ANT_FILE, buildXml.toAbsolutePath().toString());
        project.setUserProperty(MagicNames.ANT_FILE_TYPE, MagicNames.ANT_FILE_TYPE_FILE);

        // Component helper that handles unknown objects
        ComponentHelper componentHelper = new ComponentHelper() {
            @Override
            public Object createComponent(String componentName) {
                AntTypeDefinition def = getDefinition(componentName);
                return def == null ? new DynamicObject() : def.create(project);
            }
        };
        componentHelper.setProject(project);
        componentHelper.initDefaultDefinitions();

        project.addReference(ComponentHelper.COMPONENT_HELPER_REFERENCE, componentHelper);

        // alexsaveliev: Using special implementations of javac, fileset, path, and uptodate typedefs
        // to ignore missing directories errors. Ant scripts usually make directories before
        // compiling files but we'd like to collect all the available files and directories
        // without making them

        componentHelper.addTaskDefinition("javac", ErrorTolerantJavac.class);
        componentHelper.addTaskDefinition("javacc", ErrorTolerantJavaCC.class);
        componentHelper.addDataTypeDefinition("fileset", ErrorTolerantFileSet.class);
        componentHelper.addDataTypeDefinition("path", ErrorTolerantPath.class);
        componentHelper.addDataTypeDefinition("uptodate", ErrorTolerantUpToDate.class);
        // preventing custom tasks creation
        componentHelper.addDataTypeDefinition("taskdef", DynamicObject.class);

        ProjectHelper.configureProject(project, buildXml.toFile());
        Collection<String> files = new HashSet<>();
        Collection<SourcePathElement> sourcePath = new LinkedList<>();
        Collection<String> classPath = new LinkedList<>();
        Collection<String> bootClassPath = new LinkedList<>();

        String sourceVersion = null;
        String sourceEncoding = null;

        SourceUnit unit = new SourceUnit();
        unit.Files = new LinkedList<>();
        unit.Name = getProjectName(project, buildXml);
        unit.Dir = buildXml.getParent().toString();
        unit.Type = SourceUnit.DEFAULT_TYPE;
        unit.Data.BuildXML = buildXml.toString();

        for (Target target : project.getTargets().values()) {
            LOGGER.debug("Processing target {}", target.getName());
            for (Task task : target.getTasks()) {

                String taskType = task.getTaskType();

                ErrorTolerantJavac javac = null;

                if (!"javac".equals(taskType)) {

                    task = maybeJavacInMacro(task);
                    if (task == null) {
                        continue;
                    }
                    LOGGER.debug("Found javac in macro {}:{}", target.getName(), task.getTaskName());

                    // executing macrodef and using last encountered javac as a reference
                    prepare(project, target);

                    MacroInstance macroinstance = (MacroInstance) task.getRuntimeConfigurableWrapper().getProxy();
                    macroinstance.execute();

                    javac = lastJavac;
                } else {
                    LOGGER.debug("Found javac {}:{}", target.getName(), task.getTaskName());
                    prepare(project, target);

                    task.getRuntimeConfigurableWrapper().setProxy(componentHelper.createComponent(taskType));
                    task.maybeConfigure();
                    javac = (ErrorTolerantJavac) task.getRuntimeConfigurableWrapper().getProxy();
                    javac.execute();
                }

                if (javac == null) {
                    continue;
                }

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
                    sourcePath.add(new SourcePathElement(item));
                }
                classPath.addAll(getPathItems(javac.getClasspath()));
                bootClassPath.addAll(getPathItems(javac.getBootclasspath()));
            }
        }

        unit.Files = new LinkedList<>(files);

        for (String item : classPath) {
            File file = new File(item);
            if (file.isFile()) {

                Path jarPath = file.toPath();
                RawDependency dependency;
                if (dependencyCache.containsKey(jarPath)) {
                    dependency = dependencyCache.get(jarPath);
                } else {
                    dependency = MavenCentralUtils.searchInCentral(jarPath);
                    if (dependency != null) {
                        dependency.file = file.toString();
                        dependency.scope = StringUtils.EMPTY;
                    }
                    dependencyCache.put(jarPath, dependency);
                }
                if (dependency != null) {
                    unit.Data.Dependencies.add(dependency);
                }
            }
        }
        sourceEncoding = nonVariable(sourceEncoding);
        sourceVersion = nonVariable(sourceVersion);

        if (sourceVersion != null) {
            unit.Data.SourceVersion = sourceVersion;
        }
        if (sourceEncoding != null) {
            unit.Data.SourceEncoding = sourceEncoding;
        }
        unit.Data.SourcePath = sourcePath;
        unit.Data.ClassPath = classPath;
        if (!bootClassPath.isEmpty()) {
            unit.Data.BootClassPath = bootClassPath;
        }
        return unit;
    }

    /**
     * @param task task to check
     * @return not-null if task points to macrodef with javac inside
     */
    private static Task maybeJavacInMacro(Task task) {
        ComponentHelper componentHelper = ComponentHelper.getComponentHelper(task.getProject());
        Class clazz = componentHelper.getComponentClass(task.getTaskType());
        if (clazz != MacroInstance.class) {
            return null;
        }
        task.getRuntimeConfigurableWrapper().setProxy(componentHelper.createComponent(task.getTaskType()));
        task.maybeConfigure();
        MacroInstance instance = (MacroInstance) task.getRuntimeConfigurableWrapper().getProxy();
        if (findJavac(instance) != null) {
            return task;
        }
        return null;
    }

    /**
     * @param instance macroinstance object
     * @return javac task in given macroinstance
     */
    private static Task findJavac(MacroInstance instance) {
        for (UnknownElement element : instance.getMacroDef().getNestedTask().getChildren()) {
            if ("javac".equals(element.getTaskType())) {
                return (Task) element.getRuntimeConfigurableWrapper().getProxy();
            }
        }
        return null;
    }

    /**
     * @param project Ant project
     * @param buildXml location of build.xml file
     * @return project name extracted from build.xml or relative path to project's build.xml dir if project name
     * wasn't defined in build.xml
     */
    private static String getProjectName(org.apache.tools.ant.Project project, Path buildXml) {
        String name = project.getName();
        if (!StringUtils.isEmpty(name)) {
            return name;
        }
        return PathUtil.relativizeCwd(buildXml.getParent());
    }

    /**
     * @param path path to process
     * @return list of existing files denoted by a given path
     */
    private static Collection<String> getPathItems(org.apache.tools.ant.types.Path path) {
        if (path == null) {
            return Collections.emptyList();
        }
        Collection<String> ret = new LinkedList<>();
        try {
            for (String item : path.list()) {
                if (new File(item).exists()) {
                    ret.add(item);
                } else {
                    LOGGER.warn("Removing non-existent item {}", item);
                }
            }
        } catch (BuildException ex) {
            // it happens, ignore
        }
        return ret;
    }

    /**
     * @param s string to check
     * @return null if string contains unresolved variables
     */
    private static String nonVariable(String s) {
        if (s == null || s.contains("${")) {
            return null;
        }
        return s;
    }

    /**
     * Collects dependencies and runs all tasks specific target depends on
     *
     * @param project Ant project
     * @param target  Ant target
     */
    private static void prepare(org.apache.tools.ant.Project project, Target target) {
        Collection<Target> dependencies = getDependencies(project, target);
        for (Target dependency : dependencies) {
            runTasks(project, dependency);
        }
    }

    /**
     * Runs all tasks of specific target (that we know how to deal with)
     *
     * @param project Ant project
     * @param target  Ant target
     */
    private static void runTasks(org.apache.tools.ant.Project project, Target target) {
        try {
            PropertyHelper propertyHelper = PropertyHelper.getPropertyHelper(project);

            String ifString = StringUtils.defaultString(target.getIf());
            Object o = propertyHelper.parseProperties(ifString);
            if (!propertyHelper.testIfCondition(o)) {
                return;
            }

            String unlessString = StringUtils.defaultString(target.getUnless());
            o = propertyHelper.parseProperties(unlessString);
            if (!propertyHelper.testUnlessCondition(o)) {
                return;
            }

            for (Task task : target.getTasks()) {
                String taskType = task.getTaskType();
                if (!executables.contains(taskType)) {
                    continue;
                }
                LOGGER.debug("Executing {}:{}", target.getName(), task.getTaskName());

                Object proxy = ComponentHelper.getComponentHelper(project).
                        createComponent(taskType);
                task.getRuntimeConfigurableWrapper().setProxy(proxy);
                task.maybeConfigure();
                try {
                    task.execute();
                } catch (BuildException ex) {
                    LOGGER.warn("Unable to execute {}:{}", target.getName(), task.getTaskName(), ex);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("An error occurred while executing {}", target.getName(), ex);
        }
    }

    /**
     * @param project Ant project
     * @param target  Ant target
     * @return list of  target's dependencies
     */
    private static Collection<Target> getDependencies(org.apache.tools.ant.Project project, Target target) {
        return project.topoSort(target.getName(), project.getTargets());
    }

    /**
     * The main purpose of this hack around "javac" Ant's task is to tolerate missing directories.
     * For example, Ant may create "build" directory and use it as a path element later while we don't making any dirs
     */
    public static class ErrorTolerantJavac extends Javac {

        private org.apache.tools.ant.types.Path src;
        private org.apache.tools.ant.types.Path compileClasspath;
        private org.apache.tools.ant.types.Path compileSourcepath;
        private org.apache.tools.ant.types.Path bootclasspath;
        private org.apache.tools.ant.types.Path extdirs;

        public ErrorTolerantJavac() {
            super();
            fileset.setErrorOnMissingDir(false);
        }

        @Override
        public org.apache.tools.ant.types.Path createSrc() {
            if (src == null) {
                src = new ErrorTolerantPath(getProject());
            }
            return src.createPath();
        }

        @Override
        protected org.apache.tools.ant.types.Path recreateSrc() {
            src = null;
            return createSrc();
        }

        @Override
        public org.apache.tools.ant.types.Path getSrcdir() {
            return src;
        }

        @Override
        public void setSrcdir(final org.apache.tools.ant.types.Path srcDir) {
            if (src == null) {
                src = new ErrorTolerantPath(getProject());
            }
            src.append(srcDir);
        }

        @Override
        public org.apache.tools.ant.types.Path createClasspath() {
            if (compileClasspath == null) {
                compileClasspath = new ErrorTolerantPath(getProject());
            }
            return compileClasspath.createPath();
        }

        @Override
        public org.apache.tools.ant.types.Path getClasspath() {
            return compileClasspath;
        }

        @Override
        public void setClasspath(final org.apache.tools.ant.types.Path classpath) {
            if (compileClasspath == null) {
                compileClasspath = new ErrorTolerantPath(getProject());
            }
            compileClasspath.append(classpath);
        }

        @Override
        public org.apache.tools.ant.types.Path createSourcepath() {
            if (compileSourcepath == null) {
                compileSourcepath = new ErrorTolerantPath(getProject());
            }
            return compileSourcepath.createPath();
        }

        @Override
        public org.apache.tools.ant.types.Path getSourcepath() {
            return compileSourcepath;
        }

        @Override
        public void setSourcepath(final org.apache.tools.ant.types.Path sourcepath) {
            if (compileSourcepath == null) {
                compileSourcepath = sourcepath;
            }
            compileSourcepath.append(sourcepath);
        }

        @Override
        public org.apache.tools.ant.types.Path createBootclasspath() {
            if (bootclasspath == null) {
                bootclasspath = new ErrorTolerantPath(getProject());
            }
            return bootclasspath.createPath();
        }

        @Override
        public org.apache.tools.ant.types.Path getBootclasspath() {
            return bootclasspath;
        }

        @Override
        public void setBootclasspath(final org.apache.tools.ant.types.Path bootclasspath) {
            if (this.bootclasspath == null) {
                this.bootclasspath = bootclasspath;
            }
            this.bootclasspath.append(bootclasspath);
        }

        @Override
        public org.apache.tools.ant.types.Path createExtdirs() {
            if (extdirs == null) {
                extdirs = new ErrorTolerantPath(getProject());
            }
            return extdirs.createPath();
        }

        @Override
        public org.apache.tools.ant.types.Path getExtdirs() {
            return extdirs;
        }

        @Override
        public void setExtdirs(final org.apache.tools.ant.types.Path extdirs) {
            if (this.extdirs == null) {
                this.extdirs = extdirs;
            }
            this.extdirs.append(extdirs);
        }

        @Override
        public void execute() throws BuildException {

            lastJavac = this;

            resetFileLists();

            final String[] list = getSrcdir().list();
            for (String aList : list) {
                final File srcDir = getProject().resolveFile(aList);
                if (!srcDir.exists()) {
                    continue;
                }

                final DirectoryScanner ds = this.getDirectoryScanner(srcDir);
                final String[] files = ds.getIncludedFiles();

                scanDir(srcDir, getDestdir() != null ? getDestdir() : srcDir, files);
            }
        }

        protected File[] getCompileList() {
            return compileList;
        }
    }

    /**
     * The purpose of this hack around "fileset" Ant's type is to tolerate missing directories.
     * For example, Ant may create "build" directory and use it as a path element later while we don't making any dirs
     */
    public static class ErrorTolerantFileSet extends FileSet {

        @SuppressWarnings("unused")
        public ErrorTolerantFileSet() {
            super();
            setErrorOnMissingDir(false);
        }

        @SuppressWarnings("unused")
        public ErrorTolerantFileSet(FileSet fileSet) {
            super(fileSet);
            setErrorOnMissingDir(false);
        }
    }

    /**
     * The purpose of this hack around "path" Ant's type is to tolerate missing directories.
     * For example, Ant may create "build" directory and use it as a path element later while we don't making any dirs
     */
    public static class ErrorTolerantPath extends org.apache.tools.ant.types.Path {

        @SuppressWarnings("unused")
        public ErrorTolerantPath(org.apache.tools.ant.Project p, String path) {
            super(p, path);
        }

        public ErrorTolerantPath(org.apache.tools.ant.Project p) {
            super(p);
        }

        @Override
        public org.apache.tools.ant.types.Path createPath() throws BuildException {
            org.apache.tools.ant.types.Path p = new ErrorTolerantPath(getProject());
            add(p);
            return p;
        }

        @Override
        public void addFileset(FileSet fs) throws BuildException {
            fs.setErrorOnMissingDir(false);
            super.addFileset(fs);
        }

    }

    /**
     * The purpose of this hack around "uptodate" Ant's task is to tolerate missing directories.
     * For example, Ant may create "build" directory and use it as a path element later while we don't making any dirs
     */
    public static class ErrorTolerantUpToDate extends UpToDate {

        @Override
        public void addSrcfiles(final FileSet fs) {
            fs.setErrorOnMissingDir(false);
            super.addSrcfiles(fs);
        }
    }

    /**
     * We'll instantiate objects of this type for all "unknown" elements
     */
    public static class DynamicObject extends AntlibDefinition implements DynamicAttribute, TaskContainer {

        @SuppressWarnings("unused")
        public void addText(String text) {
        }

        @Override
        public void setDynamicAttribute(String name, String value) throws BuildException {
        }

        @Override
        public void addTask(Task task) {
        }
    }

    /**
     * srclib-java includes JavaCC library so we making special version of "javacc" Ant task
     * that uses bundled library
     */
    public static class ErrorTolerantJavaCC extends JavaCC {

        @Override
        public void execute() throws BuildException {
            File f = new File(AntProject.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            // we are in .bin/somefile.jar
            File javaCcHome = new File(f.getParentFile(), "../vendor/javacc");
            setJavacchome(javaCcHome);
            super.execute();
        }
    }


}
