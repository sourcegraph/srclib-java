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

    public Grapher(String classpath, Collection<String> sourcepath, String sourceVersion, String sourceEncoding, GraphWriter emit) {
        this.emit = emit;

        compiler = ToolProvider.getSystemJavaCompiler();
        diags = new DiagnosticCollector<>();
        fileManager = compiler.getStandardFileManager(diags, null, null);

        javacOpts = new ArrayList<>();
        javacOpts.add("-classpath");
        javacOpts.add(classpath != null ? classpath : StringUtils.EMPTY);
        if (sourcepath != null && !sourcepath.isEmpty()) {
            javacOpts.add("-sourcepath");
            javacOpts.add(StringUtils.join(sourcepath, SystemUtils.PATH_SEPARATOR));
        }

        // Speed up compilation by not doing dataflow, code gen, etc.
        javacOpts.add("-XDcompilePolicy=attr");
        javacOpts.add("-XDshouldStopPolicyIfError=ATTR");
        javacOpts.add("-XDshouldStopPolicyIfNoError=ATTR");

        javacOpts.add("-source");
        javacOpts.add(sourceVersion);

        javacOpts.add("-implicit:none");

        // turn off warnings
        javacOpts.add("-Xlint:none");

        if (!StringUtils.isEmpty(sourceEncoding)) {
            javacOpts.add("-encoding");
            javacOpts.add(sourceEncoding);
        }

        // This is necessary to produce Elements (and therefore defs and refs) when compilation errors occur. It will still probably fail on syntax errors, but typechecking errors are survivable.
        javacOpts.add("-proc:none");

        String bootClasspath = System.getProperty("sun.boot.class.path");
        if (StringUtils.isEmpty(bootClasspath)) {
            LOGGER.error("System property sun.boot.class.path is not set. It is required to load rt.jar.");
            System.exit(1);
        }
        javacOpts.add("-Xbootclasspath:" + bootClasspath);
    }

    public void graphFilesAndDirs(String dir, Iterable<String> filePaths) throws IOException {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Collecting source files to graph");
        }
        final List<String> files = new ArrayList<>();
        for (String filePath : filePaths) {
            File file = new File(dir, filePath);
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
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Collected source files to graph");
        }
        graphFiles(files);
    }

    public void graphFiles(Iterable<String> files) throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("javac {} {}", StringUtils.join(javacOpts, ' '), StringUtils.join(files, ' '));
        }
        graphJavaFiles(fileManager.getJavaFileObjectsFromStrings(files));
    }

    public void graphJavaFiles(Iterable<? extends JavaFileObject> files) throws IOException {
        final JavacTask task = (JavacTask) compiler.getTask(null, fileManager, new DiagnosticListener<JavaFileObject>() {
            @Override
            public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
                LOGGER.warn("javac: {}", diagnostic);
            }
        }, javacOpts, null, files);

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

    private void writePackageSymbol(String packageName) throws IOException {
        Def s = new Def();
        // TODO(sqs): set origin to the JAR this likely came from (it's hard because it could be from multiple JARs)
        s.defKey = new DefKey(null, packageName);
        s.name = packageName.substring(packageName.lastIndexOf('.') + 1);
        s.kind = "PACKAGE";
        s.pkg = packageName;
        emit.writeDef(s);
    }

    public void close() throws IOException {
        emit.flush();
        fileManager.close();
    }
}
