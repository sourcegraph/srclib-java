package com.sourcegraph.javagraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ScanCommand {
	@Parameter(names = { "--repo" }, description = "The URI of the repository that contains the directory tree being scanned")
	String repoURI;

	@Parameter(names = { "--subdir" }, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
	String subdir;

	public static String getGradleClassPath(Path build) throws IOException {
		return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).classPath;
	}

	// TODO Merge this function with ‘getGradleDependencies’.
	public static BuildAnalysis.POMAttrs getGradleAttrs(Path build) throws IOException {
		return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).attrs;
	}

	public static Path getWrapper() {
		Path result = Paths.get("./gradlew").toAbsolutePath();
		File tmp = new File(result.toString());
		if (tmp.exists() && !tmp.isDirectory()) {
			return result;
		}

		return null;
	}

	public static HashSet<SourceUnit.RawDependency> getGradleDependencies(Path build) throws IOException {
		return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).dependencies;
	}

	public static BuildAnalysis.POMAttrs getPOMAttrs(Path pomFile) throws IOException, FileNotFoundException,
			XmlPullParserException {
		BOMInputStream reader = new BOMInputStream(new FileInputStream(pomFile.toFile()));
		MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
		Model model = xpp3Reader.read(reader);

		String groupId = model.getGroupId() == null ? model.getParent().getGroupId() : model.getGroupId();

		return new BuildAnalysis.POMAttrs(groupId, model.getArtifactId(), model.getDescription());
	}

	public static HashSet<Path> findMatchingFiles(String fileName) throws IOException {
		String pat = "glob:**/" + fileName;
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pat);
		HashSet<Path> result = new HashSet<Path>();

		Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (matcher.matches(file))
					result.add(file);

				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});

		return result;
	}

	public static String swapPrefix(String path, String old, String replacement) {
		// System.err.println("Swap " + path + " " + old + " " + replacement);
		if (path.startsWith(old + File.separator)) {
			// System.err.println("Swap " + path + " with " + replacement +
			// path.substring(old.length()));
			return replacement + path.substring(old.length());
		}
		return path;
	}

	public static HashSet<SourceUnit.RawDependency> getPOMDependencies(Path pomFile) throws IOException {
		String homedir = System.getProperty("user.home");
		String[] mavenArgs = { "mvn", "dependency:resolve", "-DoutputAbsoluteArtifactFilename=true",
				"-DoutputFile=/dev/stderr" };

		HashSet<SourceUnit.RawDependency> results = new HashSet<SourceUnit.RawDependency>();

		ProcessBuilder pb = new ProcessBuilder(mavenArgs);
		pb.directory(new File(pomFile.getParent().toString()));

		BufferedReader in = null;

		try {
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(process.getErrorStream()));

			IOUtils.copy(process.getInputStream(), System.err);

			String line = null;
			while ((line = in.readLine()) != null) {
				if (!line.startsWith("   "))
					continue;
				if (line.trim().equals("none"))
					continue;

				String[] parts = line.trim().split(":");

				SourceUnit.RawDependency dep = new SourceUnit.RawDependency(parts[0], // GroupID
						parts[1], // ArtifactID
						parts[parts.length - 3], // Version
						parts[parts.length - 2], // Scope
						swapPrefix(parts[parts.length - 1], homedir, "~") // JarFile
				);

				results.add(dep);
			}

			return results;
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	public static ArrayList<String> getSourcePaths() {
		ArrayList<String> sourcePaths = new ArrayList<String>();
		sourcePaths.add("src/share/classes/");

		if (SystemUtils.IS_OS_WINDOWS) {
			sourcePaths.add("src/windows/classes/");
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			sourcePaths.add("src/macosx/classes/");
		} else {
			sourcePaths.add("src/solaris/classes/");
		}

		return sourcePaths;
	}

	public static Collection<SourceUnit> stdLibUnits() throws Exception {
		List<SourceUnit> units = new ArrayList<>();

		// Standard Library Unit
		final SourceUnit unit = new SourceUnit();
		unit.Type = "Java";
		unit.Name = ".";
		unit.Dir = "src/";
		unit.Files = scanFiles(getSourcePaths());
		// Sort for testing consistency
		unit.Files.sort((String a, String b) -> a.compareTo(b));
		units.add(unit);

		// Build tools source unit
		final SourceUnit toolsUnit = new SourceUnit();
		toolsUnit.Type = "JavaArtifact";
		toolsUnit.Name = "BuildTools";
		toolsUnit.Dir = "make/src/classes/";
		toolsUnit.Files = scanFiles("make/src/classes/");
		// Sort for testing consistency
		toolsUnit.Files.sort((String a, String b) -> a.compareTo(b));
		units.add(toolsUnit);
		return units;
	}

	public static SourceUnit androidSDKUnit(String subdir) throws Exception {
		// Android Standard Library Unit
		final SourceUnit unit = new SourceUnit();
		unit.Type = "JavaArtifact";
		unit.Name = "AndroidSDK";
		unit.Dir = ".";
		unit.Files = scanFiles(subdir);
		// Sort for testing consistency
		unit.Files.sort((String a, String b) -> a.compareTo(b));
		return unit;
	}

	public void Execute() {
		try {
			if (null == repoURI) {
				repoURI = ".";
			}
			if (null == subdir) {
				subdir = ".";
			}

			List<SourceUnit> units = new ArrayList<>();

			if (SourceUnit.isStdLib(repoURI)) {
				// Standard library special cases
				if (repoURI.equals(SourceUnit.StdLibRepoURI) || repoURI.equals(SourceUnit.StdLibTestRepoURI)) {
					units.addAll(stdLibUnits());
				} else if (repoURI.equals(SourceUnit.AndroidSdkURI)) {
					units.add(androidSDKUnit(this.subdir));
				}
			} else {
				// Recursively find all pom.xml and build.gradle files
				HashSet<Path> pomFiles = null;
				HashSet<Path> gradleFiles = null;

				pomFiles = findMatchingFiles("pom.xml");
				System.err.println(pomFiles.size() + " POM files found.");

				gradleFiles = findMatchingFiles("build.gradle");
				System.err.println(gradleFiles.size() + " gradle files found.");

				for (Path pomFile : pomFiles) {
					try {
						System.err.println("Reading " + pomFile + "...");
						BuildAnalysis.POMAttrs attrs = getPOMAttrs(pomFile);

						final SourceUnit unit = new SourceUnit();
						unit.Type = "JavaArtifact";
						unit.Name = attrs.groupID + "/" + attrs.artifactID;
						unit.Dir = pomFile.getParent().toString();
						unit.Data.put("POMFile", pomFile.toString());
						unit.Data.put("Description", attrs.description);

						// TODO: Java source files can be other places './src'
						unit.Files = scanFiles(pomFile.getParent().resolve("src"));

						// Sort for test consistency.
						unit.Files.sort((String a, String b) -> a.compareTo(b));

						// This will list all dependencies, not just direct ones.
						unit.Dependencies = new ArrayList<>(getPOMDependencies(pomFile));
						units.add(unit);
					} catch (Exception e) {
						System.err.println("Error processing pom file " + pomFile + ": " + e.toString());
					}
				}
				for (Path gradleFile : gradleFiles) {
					try {
						System.err.println("Reading " + gradleFile + "...");
						BuildAnalysis.POMAttrs attrs = getGradleAttrs(gradleFile);

						final SourceUnit unit = new SourceUnit();
						unit.Type = "JavaArtifact";
						unit.Name = attrs.groupID + "/" + attrs.artifactID;
						unit.Dir = gradleFile.getParent().toString();
						unit.Data.put("GradleFile", gradleFile.toString());
						unit.Data.put("Description", attrs.description);

						// TODO: Java source files can be other places besides ‘./src’
						unit.Files = scanFiles(gradleFile.getParent().resolve("src"));

						// We need consistent output ordering for testing purposes.
						unit.Files.sort((String a, String b) -> a.compareTo(b));

						// This will list all dependencies, not just direct ones.
						unit.Dependencies = new ArrayList<>(getGradleDependencies(gradleFile));
						units.add(unit);
					} catch (Exception e) {
						System.err.println("Error processing gradle file " + gradleFile + ": " + e.toString());
					}

				}
			}

			Gson gson = new GsonBuilder().serializeNulls().create();
			System.out.println(gson.toJson(units));
		} catch (Exception e) {
			System.err.println("Uncaught error: " + e.toString());
			e.printStackTrace();
			System.exit(1);
		}
	}

	// Recursively find .java files under a given source path
	public static List<String> scanFiles(String sourcePath) throws IOException {
		final LinkedList<String> files = new LinkedList<String>();

		if (Files.exists(Paths.get(sourcePath))) {
			Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					String filename = file.toString();
					if (filename.endsWith(".java")) {
						if (filename.startsWith("./"))
							filename = filename.substring(2);
						files.add(filename);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} else {
			System.err.println(sourcePath + " does not exist... Skipping...");
		}

		return files;
	}

	public static List<String> scanFiles(Collection<String> sourcePaths) throws IOException {
		final LinkedList<String> files = new LinkedList<String>();
		for (String sourcePath : getSourcePaths())
			files.addAll(scanFiles(sourcePath));
		return files;
	}

	public static List<String> scanFiles(Path resolve) throws IOException {
		return scanFiles(resolve.toString());
	}
}
