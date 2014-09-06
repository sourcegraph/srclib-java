package com.sourcegraph.javagraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ScanCommand {
	@Parameter(names = { "--repo" }, description = "The URI of the repository that contains the directory tree being scanned")
	String repoURI;
	
	@Parameter(names = { "--subdir" }, description = "The path of the current directory (in which the scanner is run), relative to the root directory of the repository being scanned (this is typically the root, \".\", as it is most useful to scan the entire repository)")
	String subdir;
	
	public static String[] dependencyResolveArgs = {"mvn", "dependency:resolve", "-DoutputAbsoluteArtifactFilename=true", "-DoutputFile=/dev/stderr"};
	
	public void Execute() {
		// Source Units list
		ArrayList<SourceUnit> units = new ArrayList<SourceUnit>();
		
		// Scan directory, looking for pom.xml files
		System.err.println("Walking tree, looking for pom.xml files.");
		final PathMatcher pomPattern = FileSystems.getDefault().getPathMatcher("glob:**/pom.xml");
		final ArrayList<Path> pomFiles = new ArrayList<Path>();
		
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
			});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
		System.err.println(pomFiles.size() + " POM files found.");
		
		for(Path pomFile : pomFiles) {
			try {
				System.err.println("Reading " + pomFile + "...");
				BOMInputStream reader = new BOMInputStream(new FileInputStream(pomFile.toFile()));
				
				//Reader reader = new FileReader(pomFile.toFile());
				MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
		    	Model model = xpp3Reader.read(reader);
		    	
		    	final SourceUnit unit = new SourceUnit();
				unit.Type = "JavaArtifact";
				
				String groupId = model.getGroupId() == null ? model.getParent().getGroupId() : model.getGroupId();
				unit.Name = groupId + "/" + model.getArtifactId();
				
				unit.Dir = pomFile.getParent().toString();
				
				// Extra information for Data field
				unit.Data.put("POMFile", pomFile.toString());
				unit.Data.put("Description", model.getDescription());
				
				// List all files TODO(rameshvarun): Maybe can't assume files are in src directory?
				Files.walkFileTree(pomFile.getParent().resolve("src"), new SimpleFileVisitor<Path>() {
					@Override
				     public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				         throws IOException
				     {
						String filename = file.toString();
						if(filename.endsWith(".java")) {
							if(filename.startsWith("./"))
								filename = filename.substring(2);
							unit.Files.add(filename);
						}
						return FileVisitResult.CONTINUE;
				     }
				});
				
				
				//NOTE: This method of listing dependencies lists all dependencies, not just direct dependencies
				System.err.println("Listing dependencies from " + pomFile + "...");
				ProcessBuilder pb = new ProcessBuilder(dependencyResolveArgs);
				pb.directory(new File(unit.Dir));
				try {
					Process process = pb.start();
					
					BufferedReader in = new BufferedReader(new InputStreamReader(process.getErrorStream()));
					IOUtils.copy(process.getInputStream(), System.err);

					String line = null;
					while((line = in.readLine()) != null) {
						if(line.startsWith("   ")) {
							String[] parts = line.trim().split(":");
							
							unit.Dependencies.add(new SourceUnit.RawDependency(
									parts[0], // GroupID
									parts[1], // ArtifactID
									parts[3], // Version
									parts[4], // Scope
									parts[5]  // JarFile
							));
						}
					}
					
					in.close();
					
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					System.exit(1);
				}
				
				units.add(unit);
				
		    	reader.close();
			} catch(Exception e) {
				System.err.println(e.getMessage());
			}
		}
		
		// Java Standard Library
		if(repoURI.equals(SourceUnit.StdLibRepoURI)) {
			try{
				// List all java src files
				final SourceUnit unit = new SourceUnit();
				unit.Type = "Java";
				unit.Name = ".";
				
				unit.Dir = ".";
				
				Files.walkFileTree(Paths.get("src/share/classes/"), new SimpleFileVisitor<Path>() {
					@Override
				     public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				         throws IOException
				     {
						String filename = file.toString();
						if(filename.endsWith(".java") && filename.contains("/classes/")) {
							if(filename.startsWith("./"))
								filename = filename.substring(2);
							unit.Files.add(filename);
						}
						return FileVisitResult.CONTINUE;
				     }
				});
				
				units.add(unit);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(1);
			}
			
		}
		
		Gson gson = new GsonBuilder().serializeNulls().create();
		System.out.println(gson.toJson(units));
	}
}
