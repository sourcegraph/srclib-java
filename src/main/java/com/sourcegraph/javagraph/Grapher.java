package com.sourcegraph.javagraph;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

public class Grapher {
	private final JavaCompiler compiler;
	private final DiagnosticCollector<JavaFileObject> diags;
	private final StandardJavaFileManager fileManager;
	private final GraphWriter emit;
	private final List<String> javacOpts;

	public Grapher(String classpath, String sourcepath, GraphWriter emit) {
		this.emit = emit;

		compiler = ToolProvider.getSystemJavaCompiler();
		diags = new DiagnosticCollector<JavaFileObject>();
		fileManager = compiler.getStandardFileManager(diags, null, null);

		javacOpts = new ArrayList<>();
		javacOpts.add("-classpath");
		javacOpts.add(classpath);
		javacOpts.add("-sourcepath");
		javacOpts.add(sourcepath);
		String bootClasspath = System.getProperty("sun.boot.class.path");
		if (bootClasspath == null || bootClasspath.isEmpty()) {
			System.err.println("System property sun.boot.class.path is not set. It is required to load rt.jar.");
			System.exit(1);
		}
		javacOpts.add("-Xbootclasspath:" + bootClasspath);
	}

	public void graph(String[] filePaths) throws IOException {
		final List<File> files = new ArrayList<File>();
		for (String filePath : filePaths) {
			File file = new File(filePath);
			if (!file.exists()) {
				System.err.println("no such file: " + file.getAbsolutePath());
				System.exit(1);
			}
			if (file.isFile()) {
				files.add(file);
			} else if (file.isDirectory()) {
				Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
						if (attrs.isRegularFile() && file.toString().endsWith(".java"))
							files.add(file.toFile());
						return FileVisitResult.CONTINUE;
					}
				});
			}
		}
		Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(files);
		graph(units);
	}

	public void graph(Iterable<? extends JavaFileObject> files) throws IOException {
		final JavacTask task = (JavacTask) compiler.getTask(null, fileManager, null, javacOpts, null, files);

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
				} catch(Exception e) {
					e.printStackTrace(System.err);
					System.err.println("Skipping this compilation unit...");
				}
			}
		} catch(Exception e) {
			for (Diagnostic<?> diagnostic : diags.getDiagnostics())
				System.err.format("Error on line %d in %s%n", diagnostic.getLineNumber(), diagnostic.getSource().toString());
			System.exit(1);
		}
	}

	private void writePackageSymbol(String packageName) throws IOException {
		Symbol s = new Symbol();
		s.key = new Symbol.Key("", packageName);
		s.name = packageName.substring(packageName.lastIndexOf('.') + 1);
		s.kind = "PACKAGE";
		s.pkg = packageName;
		emit.writeSymbol(s);
	}

	public void close() throws IOException {
		emit.flush();
		fileManager.close();
	}
}
