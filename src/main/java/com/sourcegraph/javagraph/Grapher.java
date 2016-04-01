package com.sourcegraph.javagraph;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Grapher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Grapher.class);

    private final JavaCompiler compiler;
    private final DiagnosticCollector<JavaFileObject> diags;
    private final StandardJavaFileManager fileManager;
    private final GraphWriter emit;
    private final List<String> javacOpts;

    private final SourceUnit unit;

    /**
     * Constructs new grapher object
     * @param unit source unit
     * @param emit target responsible for emitting definitions and references
     * @throws Exception
     */
    public Grapher(SourceUnit unit,
                   GraphWriter emit) throws Exception {
        this.unit = unit;
        this.emit = emit;

        compiler = ToolProvider.getSystemJavaCompiler();
        diags = new DiagnosticCollector<>();
        fileManager = compiler.getStandardFileManager(diags, null, null);

        javacOpts = new ArrayList<>();

        Collection<String> bootClassPath = unit.getProject().getBootClassPath();
        if (bootClassPath == null) {
            String envBootClasspath = System.getProperty("sun.boot.class.path");
            if (StringUtils.isEmpty(envBootClasspath)) {
                LOGGER.error("System property sun.boot.class.path is not set. It is required to load rt.jar.");
                System.exit(1);
            }
            bootClassPath = Arrays.asList(envBootClasspath.split(SystemUtils.PATH_SEPARATOR));
        }
        Collection<File> bootClassPathFiles  = new ArrayList<>();
        Collection<String> resolvedBootClassPath = new ArrayList<>();
        for (String path : bootClassPath) {
            Path resolvedPath = PathUtil.CWD.resolve(path).toAbsolutePath();
            bootClassPathFiles.add(resolvedPath.toFile());
            resolvedBootClassPath.add(resolvedPath.toString());
        }

        fileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, bootClassPathFiles);
        javacOpts.add("-Xbootclasspath:" + StringUtils.join(resolvedBootClassPath, SystemUtils.PATH_SEPARATOR));

        Collection<String> classPath = unit.getProject().getClassPath();
        if (classPath == null) {
            classPath = Collections.emptyList();
        }
        Collection<File> classPathFiles = new ArrayList<>();
        Collection<String> resolvedClassPath = new ArrayList<>();
        for (String path : classPath) {
            Path resolvedPath = PathUtil.CWD.resolve(path).toAbsolutePath();
            classPathFiles.add(resolvedPath.toFile());
            resolvedClassPath.add(resolvedPath.toString());
        }

        fileManager.setLocation(StandardLocation.CLASS_PATH, classPathFiles);
        javacOpts.add("-classpath");
        javacOpts.add(StringUtils.join(resolvedClassPath, SystemUtils.PATH_SEPARATOR));

        Collection<String> sourcePath = unit.getProject().getSourcePath();
        if (sourcePath != null && !sourcePath.isEmpty()) {
            javacOpts.add("-sourcepath");
            Collection<String> resolvedSourcePath = new ArrayList<>();
            Collection<File> sourcePathFiles = new ArrayList<>();
            for (String path : sourcePath) {
                Path resolvedPath = PathUtil.CWD.resolve(path).toAbsolutePath();
                resolvedSourcePath.add(resolvedPath.toString());
                sourcePathFiles.add(resolvedPath.toFile());
            }
            javacOpts.add(StringUtils.join(resolvedSourcePath, SystemUtils.PATH_SEPARATOR));
            fileManager.setLocation(StandardLocation.SOURCE_PATH, sourcePathFiles);
        }

        // Speed up compilation by not doing dataflow, code gen, etc.
        javacOpts.add("-XDcompilePolicy=attr");
        javacOpts.add("-XDshouldStopPolicyIfError=ATTR");
        javacOpts.add("-XDshouldStopPolicyIfNoError=ATTR");

        String sourceVersion = unit.getProject().getSourceCodeVersion();
        if (!StringUtils.isEmpty(sourceVersion)) {
            javacOpts.add("-source");
            javacOpts.add(sourceVersion);
        }

        javacOpts.add("-implicit:none");

        // turn off warnings
        javacOpts.add("-Xlint:none");

        String sourceEncoding = unit.getProject().getSourceCodeEncoding();
        if (!StringUtils.isEmpty(sourceEncoding)) {
            javacOpts.add("-encoding");
            javacOpts.add(sourceEncoding);
        }

        // This is necessary to produce Elements (and therefore defs and refs) when compilation errors occur. It will still probably fail on syntax errors, but typechecking errors are survivable.
        javacOpts.add("-proc:none");

    }

    /**
     * Builds a graph of given files and directories.
     * @param filePaths collection of file path elements to graph sources of. If element is a file it will be scheduled
     * for graphing, otherwise, if element is a directory, we'll schedule for graphing all java files located in the
     * given directory recursively. Each element should point to existing file/directory
     * @throws IOException
     */
    public void graphFilesAndDirs(Collection<String> filePaths) throws IOException {

        LOGGER.debug("Collecting source files to graph");
        File root = PathUtil.CWD.toFile();

        final List<String> files = new ArrayList<>();
        for (String filePath : filePaths) {
            File file = PathUtil.concat(root, filePath);
            if (!file.exists()) {
                LOGGER.error("No such file {}", file.getAbsolutePath());
                System.exit(1);
            }
            if (file.isFile()) {
                files.add(file.toPath().normalize().toString());
            } else if (file.isDirectory()) {
                Files.walkFileTree(file.toPath(),
                        new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                if (attrs.isRegularFile() && file.toString().endsWith(".java")) {
                                    files.add(file.normalize().toString());
                                }
                                return FileVisitResult.CONTINUE;
                            }
                        });
            }
        }
        LOGGER.debug("Collected source files to graph");
        graphFiles(files);
    }

    /**
     * Builds a graph of given files
     * @param files collection of file path elements to graph sources of. Each element should point to existing file
     * @throws IOException
     */
    public void graphFiles(Collection<String> files) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("javac {} {}", StringUtils.join(javacOpts, ' '), StringUtils.join(files, ' '));
        }
        graphJavaFiles(fileManager.getJavaFileObjectsFromStrings(files));
    }

    /**
     * Builds a graph of given file objects
     * @param files list of file objects to build graphs for
     * @throws IOException
     */
    public void graphJavaFiles(Iterable<? extends JavaFileObject> files) throws IOException {
        final JavacTask task = (JavacTask) compiler.getTask(null,
                fileManager,
                diagnostic -> {
                    LOGGER.warn("{} javac: {}", unit.Name, diagnostic);
                },
                javacOpts,
                null,
                files);
        final Trees trees = Trees.instance(task);

        final Set<String> seenPackages = new HashSet<>();

        try {
            Iterable<? extends CompilationUnitTree> units = task.parse();
            task.analyze();
            for (final CompilationUnitTree unit : units) {

                try {
                    ExpressionTree pkgName = unit.getPackageName();
                    if (pkgName != null && !seenPackages.contains(pkgName.toString())) {
                        seenPackages.add(pkgName.toString());
                        writePackageSymbol(pkgName.toString());
                    }

                    TreePath root = new TreePath(unit);
                    new TreeScanner(emit, trees).scan(root, null);
                } catch (Exception e) {
                    LOGGER.warn("Skipping compilation unit {} ({})", unit.getPackageName(), unit.getSourceFile(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Compilation failed", e);
            for (Diagnostic<?> diagnostic : diags.getDiagnostics()) {
                LOGGER.warn("Error on line {} in {}", diagnostic.getLineNumber(), diagnostic.getSource());
            }
            LOGGER.warn("If the stack trace contains \"task.analyze();\", there's a reasonable chance you're using a buggy compiler.\n"
                    + "As of Nov 7, 2014, the Oracle 8 JDK is one of those compilers.\n"
                    + "See https://bugs.openjdk.java.net/browse/JDK-8062359?page=com.atlassian.jira.plugin.system.issuetabpanels:comment-tabpanel\n"
                    + "and compile OpenJDK 8 with that workaround. OpenJDK 8 build instructions: http://openjdk.java.net/projects/build-infra/guide.html\nWe can remove this once jdk 8u26+ is released. NOTE that you need to install from the jdk8u hg repo, not jdk8 (as that is frozen when the first version of jdk8 was released).");
            System.exit(1);
        }
    }

    /**
     * Emits package object definition to graph
     * @param packageName package name to emit
     * @throws IOException
     */
    private void writePackageSymbol(String packageName) throws IOException {
        Def s = new Def();
        // TODO(sqs): set origin to the JAR this likely came from (it's hard because it could be from multiple JARs)
        s.defKey = new DefKey(null, packageName);
        s.name = packageName.substring(packageName.lastIndexOf('.') + 1);
        s.kind = "PACKAGE";
        s.pkg = packageName;
        emit.writeDef(s);
    }

    /**
     * Closes grapher and releases underlying resources
     * @throws IOException
     */
    public void close() throws IOException {
        emit.flush();
        fileManager.close();
    }
}
