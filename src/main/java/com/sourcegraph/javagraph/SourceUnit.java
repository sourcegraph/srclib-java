package com.sourcegraph.javagraph;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

import com.sourcegraph.javagraph.DepresolveCommand.Resolution;
import com.sourcegraph.javagraph.DepresolveCommand.ResolvedTarget;

public class SourceUnit {
	
	public static String StdLibRepoURI = "hg.openjdk.java.net/jdk8/jdk8/jdk";
	public boolean isStdLib() {
		return Repo.equals(StdLibRepoURI);
	}
	
	public static class RawDependency {
		String GroupId;
		String ArtifactId;
		String Version;
		String Scope;
		String JarPath;
		
		private transient Resolution resolved = null;
		
		public RawDependency(String GroupId, String ArtifactId, String Version, String Scope, String JarPath) {
			this.GroupId = GroupId;
			this.ArtifactId = ArtifactId;
			this.Version = Version;
			this.Scope = Scope;
			this.JarPath = JarPath;
		}

		public Resolution Resolve() {
			if(resolved == null) {
				// Get the url to the POM file for this artifact
				String url = "http://central.maven.org/maven2/" + GroupId
						+ "/" + ArtifactId + 
						"/" + Version + "/" + 
						ArtifactId + "-" + 
						Version + ".pom";
				
				resolved = new Resolution();
				
				try {
					InputStream input = new BOMInputStream(new URL(url).openStream());
					
					MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
					Model model = xpp3Reader.read(input);
					input.close();
					
					Scm scm = model.getScm();
					if(scm != null) {
					
					
						resolved.Raw = this;
						
						ResolvedTarget target = new ResolvedTarget();
						target.ToRepoCloneURL = model.getScm().getConnection();
						target.ToUnit = model.getGroupId() + "/" + model.getArtifactId();
						target.ToUnitType = "JavaArtifact";
						target.ToVersionString = model.getVersion();
						
						resolved.Target = target;
					}
					else {
						resolved.Error = model.getArtifactId() + " does not have an associated SCM repository.";
					}
					
					
				} catch (Exception e) {
					resolved.Error = e.getMessage();
				}
			}
			
			return resolved;
		}
	}
	
	String Name;
	String Type;
	String Repo;
	
	//TODO(rameshvarun): Globs entry
	
	List<String> Files = new LinkedList<String>();
	String Dir;
	
	List<RawDependency> Dependencies = new LinkedList<RawDependency>();
	
	// TODO(rameshvarun): Info field
	
	Map<String, Object> Data = new HashMap<String, Object>();
	
	// TODO(rameshvarun): Config list
	
	Map<String, String> Ops = new HashMap<String,String>();
	
	public SourceUnit() {
		Ops.put("graph", null);
		Ops.put("depresolve", null);
	}
}
