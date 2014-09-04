package com.sourcegraph.javagraph;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class DepresolveCommand {
	static class Resolution {
		SourceUnit.RawDependency Raw;
		ResolvedTarget Target;
		String Error;
	}
	
	static class ResolvedTarget {
		String ToRepoCloneURL;
		String ToUnit;
		String ToUnitType;
		String ToVersionString;
	}
	public void Execute() {
		Gson gson = new GsonBuilder().serializeNulls().create();
		
		SourceUnit unit = null;
		try {
			InputStreamReader reader = new InputStreamReader(System.in);
			unit = gson.fromJson(reader, SourceUnit.class);
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
		final ArrayList<Resolution> resolutions = new ArrayList<Resolution>();
		
		for(SourceUnit.RawDependency rawDep : unit.Dependencies) {
			// Get the url to the POM file for this artifact
			String url = "http://central.maven.org/maven2/" + rawDep.GroupId
					+ "/" + rawDep.ArtifactId + 
					"/" + rawDep.Version + "/" + 
					rawDep.ArtifactId + "-" + 
					rawDep.Version + ".pom";
			
			Resolution resolution = new Resolution();
			
			try {
				InputStream input = new URL(url).openStream();
				
				MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
				Model model = xpp3Reader.read(input);
				input.close();
				
				Scm scm = model.getScm();
				if(scm != null) {
				
				
					resolution.Raw = rawDep;
					
					ResolvedTarget target = new ResolvedTarget();
					target.ToRepoCloneURL = model.getScm().getConnection();
					target.ToUnit = model.getGroupId() + "/" + model.getArtifactId();
					target.ToUnitType = "MavenArtifact";
					target.ToVersionString = model.getVersion();
					
					resolution.Target = target;
				}
				else {
					resolution.Error = model.getArtifactId() + " does not have an associated SCM repository.";
				}
				
				
			} catch (Exception e) {
				resolution.Error = e.getMessage();
			}
			
			resolutions.add(resolution);
		}
		
		System.out.println(gson.toJson(resolutions));
	}
}
