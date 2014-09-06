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
		
		private static Resolution stdlib = null;

		public static Resolution StdLib() {
			if(stdlib == null) {
				stdlib = new Resolution();
				stdlib.Target = new ResolvedTarget();
				stdlib.Target.ToRepoCloneURL = SourceUnit.StdLibRepoURI;
				stdlib.Target.ToUnitType = "Java";
				stdlib.Target.ToUnit = ".";
			}

			return stdlib;
		}
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
			resolutions.add(rawDep.Resolve());
		}

		resolutions.add(Resolution.StdLib());
		
		System.out.println(gson.toJson(resolutions));
	}
}
