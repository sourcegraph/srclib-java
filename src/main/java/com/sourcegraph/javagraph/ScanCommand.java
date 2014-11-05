package com.sourcegraph.javagraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ProcessBuilder.Redirect;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.tools.javac.util.List;
import org.javatuples.Triplet;

public class ScanCommand {
	@Parameter(names = { "--repo" }, description = "The URI of the repository that contains the directory tree being scanned")
	String repoURI;

	@Parameter(names = { "--subdir" }, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
	String subdir;

	public static class POMAttrs {
		String groupID;
		String artifactID;
		String description;
		public POMAttrs(String g, String a, String d) {
			groupID = g;
			artifactID = a;
			description = d;
		}
	};

	public static POMAttrs getPOMAttrs(Path pomFile)
		throws IOException, FileNotFoundException, XmlPullParserException
	{
		BOMInputStream reader = new BOMInputStream(new FileInputStream(pomFile.toFile()));
		MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
		Model model = xpp3Reader.read(reader);

		String groupId = model.getGroupId() == null
			? model.getParent().getGroupId()
			: model.getGroupId();

		return new POMAttrs(groupId, model.getArtifactId(), model.getDescription());
	}

	public static HashSet<SourceUnit.RawDependency> getPOMDependencies(Path pomFile)
		throws IOException
	{
		String[] mavenArgs = {
			"mvn", "dependency:resolve",
			"-DoutputAbsoluteArtifactFilename=true",
			"-DoutputFile=/dev/stderr"};

		HashSet<SourceUnit.RawDependency> results = new HashSet<SourceUnit.RawDependency>();

		ProcessBuilder pb = new ProcessBuilder(mavenArgs);
		pb.directory(new File(pomFile.getParent().toString()));

		BufferedReader in = null;

		try {
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(
				process.getErrorStream()));

			IOUtils.copy(process.getInputStream(), System.err);

			String line = null;
			while ((line = in.readLine()) != null) {
				if (!line.startsWith("   ")) continue;
				if (line.trim().equals("none")) continue;

				String[] parts = line.trim().split(":");

				SourceUnit.RawDependency dep = new SourceUnit.RawDependency(
					parts[0], // GroupID
					parts[1], // ArtifactID
					parts[parts.length - 3], // Version
					parts[parts.length - 2], // Scope
					parts[parts.length - 1]  // JarFile
				);

				results.add(dep);
			}

			return results;
		}
		finally {
			if (in != null) {
				in.close();
			}
		}
	}

	public static ArrayList<String> getSourcePaths() {
		ArrayList<String> sourcePaths = new ArrayList<String>();
		sourcePaths.add("src/share/classes/");

		if(SystemUtils.IS_OS_WINDOWS) {
			sourcePaths.add("src/windows/classes/");
		} else if(SystemUtils.IS_OS_MAC_OSX) {
			sourcePaths.add("src/macosx/classes/");
		} else {
			sourcePaths.add("src/solaris/classes/");
		}

		return sourcePaths;
	}

	public void Execute() {
		// Defaults
		if (null == repoURI) {
			repoURI = ".";
		}

		// Source Units list
		ArrayList<SourceUnit> units = new ArrayList<SourceUnit>();

		// Scan directory, looking for pom.xml files
		System.err.println("Walking tree, looking for pom.xml files.");
		final PathMatcher pomPattern = FileSystems.getDefault().getPathMatcher("glob:**/pom.xml");
		final ArrayList<Path> pomFiles = new ArrayList<Path>();

		//TODO: Also match .pom files
		try {
			Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
						throws IOException
					{
						if(pomPattern.matches(file))
							pomFiles.add(file);

						return FileVisitResult.CONTINUE;
					}
          @Override
          public FileVisitResult visitFileFailed(Path file, IOException e)
						throws IOException
					{
						return FileVisitResult.CONTINUE;
					}
			});
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		System.err.println(pomFiles.size() + " POM files found.");

		for(Path pomFile : pomFiles) {
			try {
				System.err.println("Reading " + pomFile + "...");
				POMAttrs attrs = getPOMAttrs(pomFile);

				final SourceUnit unit = new SourceUnit();
				unit.Type = "JavaArtifact";
				unit.Name = attrs.groupID + "/" + attrs.artifactID;
				unit.Dir = pomFile.getParent().toString();
				unit.Data.put("POMFile", pomFile.toString());
				unit.Data.put("Description", attrs.description);

				// TODO: Java source files can be other places besides ‘./src’
				unit.Files = scanFiles(pomFile.getParent().resolve("src"));

				// We need consistent output ordering for testing purposes.
				unit.Files.sort((String a, String b) -> a.compareTo(b));

				// This will list all dependencies, not just direct ones.
				unit.Dependencies = new ArrayList(getPOMDependencies(pomFile));
				units.add(unit);

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
		}

		// Java Standard Library
		if(repoURI.equals(SourceUnit.StdLibRepoURI) || repoURI.equals(SourceUnit.StdLibTestRepoURI)) {
			try{
				// Standard Library Unit
				final SourceUnit unit = new SourceUnit();
				unit.Type = "Java";
				unit.Name = ".";
				unit.Dir = "src/";
				unit.Files = scanFiles(getSourcePaths());
				unit.Files.sort( (String a, String b) -> a.compareTo(b) ); // Sort for testing consistency
				units.add(unit);

				// Test code source unit
				// FIXME(rameshvarun): Test code scanning is currently disabled, because graphing code expects package names (which the test code lacks)
				/* final SourceUnit testUnit = new SourceUnit();
				testUnit.Type = "JavaArtifact";
				testUnit.Name = "Tests";
				testUnit.Dir = "test/";
				testUnit.Files = scanFiles("test/");
				testUnit.Files.sort( (String a, String b) -> a.compareTo(b) ); // Sort for testing consistency
				units.add(testUnit); */

				// Build tools source unit
				final SourceUnit toolsUnit = new SourceUnit();
				toolsUnit.Type = "JavaArtifact";
				toolsUnit.Name = "BuildTools";
				toolsUnit.Dir = "make/src/classes/";
				toolsUnit.Files = scanFiles("make/src/classes/");
				toolsUnit.Files.sort( (String a, String b) -> a.compareTo(b) ); // Sort for testing consistency
				units.add(toolsUnit);

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}

		}

		Gson gson = new GsonBuilder().serializeNulls().create();
		System.out.println(gson.toJson(units));
	}


	// Recursively find .java files under a given source path
	public static java.util.List<String> scanFiles(String sourcePath) throws IOException {
		final LinkedList<String> files = new LinkedList<String>();

		if(Files.exists(Paths.get(sourcePath))) {
			Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {
				@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
							throws IOException
					{
					String filename = file.toString();
					if(filename.endsWith(".java")) {
						if(filename.startsWith("./"))
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

	public static java.util.List<String> scanFiles(Collection<String> sourcePaths) throws IOException {
		final LinkedList<String> files = new LinkedList<String>();
		for(String sourcePath : getSourcePaths()) files.addAll(scanFiles(sourcePath));
		return files;
	}

	public static java.util.List<String> scanFiles(Path resolve) throws IOException {
		return scanFiles(resolve.toString());
	}
}
