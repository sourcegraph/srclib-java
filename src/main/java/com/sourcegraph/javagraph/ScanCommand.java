package com.sourcegraph.javagraph;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

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
	
	public void Execute() {
		// Source Units list
		ArrayList<SourceUnit> units = new ArrayList<SourceUnit>();
		
		// Scan directory, looking for pom.xml files
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
		
		for(Path pomFile : pomFiles) {
			try {
				Reader reader = new FileReader(pomFile.toFile());
				MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
		    	Model model = xpp3Reader.read(reader);
		    	
		    	final SourceUnit unit = new SourceUnit();
				unit.Type = "MavenPackage";
				unit.Name = model.getArtifactId();
				
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
				
				// Dependencies
				for(Dependency dep : model.getDependencies()) {
					unit.Dependencies.add(new SourceUnit.RawDependency(
							dep.getArtifactId(),
							dep.getVersion()
					));
				}
				
				
				units.add(unit);
				
		    	reader.close();
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
		
		Gson gson = new GsonBuilder().serializeNulls().create();
		System.out.println(gson.toJson(units));
	}
}
