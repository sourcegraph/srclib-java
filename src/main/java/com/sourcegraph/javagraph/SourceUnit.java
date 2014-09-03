package com.sourcegraph.javagraph;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SourceUnit {
	
	public static class RawDependency {
		String Version;
		String ArtifactId;
		String GroupId;
		
		public RawDependency( String ArtifactId, String Version, String GroupId) {
			this.Version = Version;
			this.ArtifactId = ArtifactId;
			this.GroupId = GroupId;
		}
	}
	
	String Name;
	String Type;
	String Repo;
	
	//TODO(rameshvarun): Globs entry
	
	List<String> Files = new LinkedList<String>();
	String Dir;
	
	// TODO(rameshvarun): Dependencies list
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
