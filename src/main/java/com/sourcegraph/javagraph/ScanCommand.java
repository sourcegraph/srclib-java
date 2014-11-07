package com.sourcegraph.javagraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.Charset;
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
import org.apache.commons.io.FileUtils;
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

  public static String getGradleClassPath(Path gradleFile)
		throws IOException
	{
		injectInspectorTaskIntoGradleFile(gradleFile);

		String[] gradleArgs = {"gradle", "srclibCollectMetaInformation"};
		ProcessBuilder pb = new ProcessBuilder(gradleArgs);
		pb.directory(new File(gradleFile.getParent().toString()));

		BufferedReader in = null;
		HashSet<SourceUnit.RawDependency> results =
			new HashSet<SourceUnit.RawDependency>();

		String result = null;

		try {
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(
				process.getInputStream()));

			IOUtils.copy(process.getErrorStream(), System.err);

			String line = null;
			while ((line = in.readLine()) != null) {
				result = extractPayloadFromPrefixedLine("CLASSPATH", line);
				if (result != null) break;
			}
		}
		finally {
			if (in != null) {
				in.close();
			}
		}

		return result;
	}

	public static void injectInspectorTaskIntoGradleFile(Path gradleFile)
		throws IOException
	{
		String g = FileUtils.readFileToString(gradleFile.toFile());
		if (-1 != g.indexOf("srclibCollectMetaInformation")) return;

		/*
			Ok! This is a pretty nasty hack to try and collection information
			from a gradle build. Here's some known issues:

				- project.{group,version,name} might not be defined. It's
				possible to generate POM files for the Maven repo with explicit
				values for theses fields. The project._ values are just the
				defaults. Basically, this is a flakey heuristic, and it remains
				to be seen if it will work well for gradle projects in general.

					- It might be possible to actually ask gradle to
					generate the pom files, and then inspect those. This
					is what we're doing for dependencies that we pull
					from the maven repository. However! I'm not sure if
					there's a sane way to request this, or if that approach
					will work for gradle projects in general.
		*/
		String task = ""
			+ "task srclibCollectMetaInformation << {\n"
			+ "	String desc = project.description\n"
			+ "	if (desc == null) { desc = \"\" }\n"
			+ "	println \"DESCRIPTION $desc\"\n"
			+ "	println \"GROUP $project.group\"\n"
			+ "	println \"VERSION $project.version\"\n"
			+ "	println \"ARTIFACT $project.name\"\n"
			+ "	println \"CLASSPATH $configurations.runtime.asPath\"\n"
			+ "\n"
			+ "	project.configurations.each { conf ->\n"
			+ "		conf.resolvedConfiguration.getResolvedArtifacts().each {\n"
			+ "			String group = it.moduleVersion.id.group\n"
			+ "			String name = it.moduleVersion.id.name\n"
			+ "			String version = it.moduleVersion.id.version\n"
			+ "			String file = it.file\n"
			+ "			println \"DEPENDENCY $conf.name:$group:$name:$version:$file\"\n"
			+ "		}\n"
			+ "	}\n"
			+ "}\n";

		try {
				FileWriter fw = new FileWriter(gradleFile.toFile(),true);
				fw.write(task);
				fw.close();
		} catch(IOException ioe) {
				System.err.println("IOException: " + ioe.getMessage());
		}
	}

	public static String extractPayloadFromPrefixedLine(String prefix, String line) {
		int idx = line.indexOf(prefix);
		if (-1 == idx) return null;
		int offset = idx + prefix.length();
		return line.substring(offset).trim();
	}

	// TODO Merge this function with ‘getGradleDependencies’.
	public static POMAttrs getGradleAttrs(Path gradleFile)
		throws IOException
	{
		injectInspectorTaskIntoGradleFile(gradleFile);

		String[] gradleArgs = {"gradle", "srclibCollectMetaInformation"};
		ProcessBuilder pb = new ProcessBuilder(gradleArgs);
		pb.directory(new File(gradleFile.getParent().toString()));

		BufferedReader in = null;
		HashSet<SourceUnit.RawDependency> results =
			new HashSet<SourceUnit.RawDependency>();

		String groupID = "default-group";
        String artifactID = gradleFile.getParent().normalize().toString(); // default to path to build.gradle
        String description = null;

		try {
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(
				process.getInputStream()));

			IOUtils.copy(process.getErrorStream(), System.err);

			String line = null;
			while ((line = in.readLine()) != null) {

				String groupPayload = extractPayloadFromPrefixedLine("GROUP", line);
				String artifactPayload = extractPayloadFromPrefixedLine("ARTIFACT", line);
				String descriptionPayload = extractPayloadFromPrefixedLine("DESCRIPTION", line);

				if (null != groupPayload) groupID = groupPayload;
				if (null != artifactPayload) artifactID = artifactPayload;
				if (null != descriptionPayload) description = descriptionPayload;
			}
		}
		finally {
			if (in != null) {
				in.close();
			}
		}

		return new POMAttrs(groupID, artifactID, description);
	}

	/**
		This collects gradle dependency information by calling
		a custom task ("srclibCollectMetaInformation") that we have
		injected into the build.gradle file. This outputs a bunch of
		information including a list of dependencies, each on it's
		own line. These lines take the following form:

			^DEPENDENCY $scope:$group:$artifact:$version:$jarfile$

		Here's an example of a dependency line:

			line="DEPENDENCY compile:com.beust:jcommander:1.30:~/.gradle/caches/modules-2/files-2.1/com.beust/jcommander/1.30/c440b30a944ba199751551aee393f8aa03b3c327/jcommander-1.30.jar"
	*/
	public static HashSet<SourceUnit.RawDependency> getGradleDependencies(Path gradleFile)
		throws IOException
	{
		injectInspectorTaskIntoGradleFile(gradleFile);

		String[] gradleArgs = {"gradle", "srclibCollectMetaInformation"};
		ProcessBuilder pb = new ProcessBuilder(gradleArgs);
		pb.directory(new File(gradleFile.getParent().toString()));

		BufferedReader in = null;
		HashSet<SourceUnit.RawDependency> results =
			new HashSet<SourceUnit.RawDependency>();

		try {
			Process process = pb.start();
			in = new BufferedReader(new InputStreamReader(
				process.getInputStream()));

			IOUtils.copy(process.getErrorStream(), System.err);

			String line = null;
			while ((line = in.readLine()) != null) {

				String payload = extractPayloadFromPrefixedLine("DEPENDENCY", line);

				if (null == payload) continue;

				String[] parts = payload.split(":");
				SourceUnit.RawDependency dep = new SourceUnit.RawDependency(
					parts[1], // GroupID
					parts[2], // ArtifactID
					parts[3], // Version
					parts[0], // Scope
					parts[4] // JarFile
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

	public static HashSet<Path> findMatchingFiles(String fileName)
		throws IOException
	{
		String pat = "glob:**/" + fileName;
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pat);
		HashSet<Path> result = new HashSet<Path>();

		Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
					throws IOException
				{
					if(matcher.matches(file))
						result.add(file);

					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult visitFileFailed(Path file, IOException e)
					throws IOException
				{
					return FileVisitResult.CONTINUE;
				}
		});

		return result;
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
					parts[parts.length - 1] // JarFile
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
		if (null == repoURI) { repoURI = "."; }

		// Recursivly find all pom.xml and build.gradle files.
		HashSet<Path> pomFiles = null;
		HashSet<Path> gradleFiles = null;

		try {
			System.err.println("Walking tree, looking for pom.xml files.");
			pomFiles = findMatchingFiles("pom.xml");
			System.err.println(pomFiles.size() + " POM files found.");

			System.err.println("Walking tree, looking for build.gradle files.");
			gradleFiles = findMatchingFiles("build.gradle");
			System.err.println(gradleFiles.size() + " gradle files found.");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		ArrayList<SourceUnit> result = new ArrayList<SourceUnit>();

		try {
			for(Path pomFile : pomFiles) {
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
				result.add(unit);

			}

            if (pomFiles.size() == 0) { // only look for gradle files if no pom.xml's are present
                for (Path gradleFile : gradleFiles) {
                    System.err.println("Reading " + gradleFile + "...");
                    POMAttrs attrs = getGradleAttrs(gradleFile);

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
                    unit.Dependencies = new ArrayList(getGradleDependencies(gradleFile));
                    result.add(unit);
                }
            }

        } catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
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
				result.add(unit);

				// Test code source unit
				// FIXME(rameshvarun): Test code scanning is currently disabled, because graphing code expects package names (which the test code lacks)
				/* final SourceUnit testUnit = new SourceUnit();
				testUnit.Type = "JavaArtifact";
				testUnit.Name = "Tests";
				testUnit.Dir = "test/";
				testUnit.Files = scanFiles("test/");
				testUnit.Files.sort( (String a, String b) -> a.compareTo(b) ); // Sort for testing consistency
				result.add(testUnit); */

				// Build tools source unit
				final SourceUnit toolsUnit = new SourceUnit();
				toolsUnit.Type = "JavaArtifact";
				toolsUnit.Name = "BuildTools";
				toolsUnit.Dir = "make/src/classes/";
				toolsUnit.Files = scanFiles("make/src/classes/");
				toolsUnit.Files.sort( (String a, String b) -> a.compareTo(b) ); // Sort for testing consistency
				result.add(toolsUnit);

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}

		}

		Gson gson = new GsonBuilder().serializeNulls().create();
		System.out.println(gson.toJson(result));
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
