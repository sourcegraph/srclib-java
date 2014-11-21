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

/**
 * SourceUnit represents a source unit expected by srclib. A source unit is a
 * build-system- and language-independent abstraction of a Maven repository or
 * Gradle project. This class also includes static helpers for special case
 * source units like the Java SDK.
 *
 */
public class SourceUnit {

	public static String StdLibRepoURI = "hg.openjdk.java.net/jdk8/jdk8/jdk";
	public static String StdLibTestRepoURI = "github.com/sgtest/java-jdk-sample";
	public static String AndroidSdkURI = "android.googlesource.com/platform/frameworks/base";

	public boolean isStdLib() {
		if (Repo == null) {
			return false;
		}
		return isStdLib(Repo);
	}

	public static boolean isStdLib(String repo) {
		return repo.equals(StdLibRepoURI) || repo.equals(StdLibTestRepoURI)
				|| repo.equals(AndroidSdkURI);
	}

	/**
	 * A Raw, unresolved Maven Dependency.
	 */
	public static class RawDependency {
		String GroupId;
		String ArtifactId;
		String Version;
		String Scope;
		String JarPath;

		/**
		 * Cache the result of the resolution, so no additional url requests
		 * need to be made.
		 */
		private transient Resolution resolved = null;

		public RawDependency(String GroupId, String ArtifactId, String Version,
				String Scope, String JarPath) {
			this.GroupId = GroupId;
			this.ArtifactId = ArtifactId;
			this.Version = Version;
			this.Scope = Scope;
			this.JarPath = JarPath;
		}

		/**
		 * Provide Clone URL overrides for different groupid/artifactid source
		 * units
		 */
		static HashMap<String, String> overrides = new HashMap<String, String>() {
			{
				put("org.hamcrest/", "https://github.com/hamcrest/JavaHamcrest");
				put("com.badlogicgames.gdx/",
						"https://github.com/libgdx/libgdx");
				put("com.badlogicgames.jglfw/",
						"https://github.com/badlogic/jglfw");
				put("org.json/json",
						"https://github.com/douglascrockford/JSON-java");
				put("junit/junit", "https://github.com/junit-team/junit");
			}
		};

		/**
		 * @param lookup
		 *            GroupID + "/" + ArtifactID
		 * @return A VCS url, if an override was found, null if not.
		 */
		public static String checkOverrides(String lookup) {
			for (String key : overrides.keySet()) {
				if (lookup.startsWith(key))
					return overrides.get(key);
			}
			return null;
		}

		/**
		 * Try to resolve this raw Dependency to its VCS target.
		 * 
		 * @return The Resolution Object. Error will be non-null if a Resolution
		 *         could not be performed.
		 */
		public Resolution Resolve() {
			if (resolved == null) {
				// Get the url to the POM file for this artifact
				String url = "http://central.maven.org/maven2/"
						+ GroupId.replace(".", "/") + "/" + ArtifactId + "/"
						+ Version + "/" + ArtifactId + "-" + Version + ".pom";

				resolved = new Resolution();

				try {
					String cloneUrl = checkOverrides(GroupId + "/" + ArtifactId);

					if (cloneUrl == null) {
						InputStream input = new BOMInputStream(
								new URL(url).openStream());

						MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
						Model model = xpp3Reader.read(input);
						input.close();

						Scm scm = model.getScm();
						if (scm != null)
							cloneUrl = scm.getUrl();
					}

					if (cloneUrl != null) {
						resolved.Raw = this;

						ResolvedTarget target = new ResolvedTarget();
						target.ToRepoCloneURL = cloneUrl;
						target.ToUnit = GroupId + "/" + ArtifactId;
						target.ToUnitType = "JavaArtifact";
						target.ToVersionString = Version;

						resolved.Target = target;
					} else {
						resolved.Error = ArtifactId
								+ " does not have an associated SCM repository.";
					}

				} catch (Exception e) {
					resolved.Error = "Could not download file "
							+ e.getMessage();
				}
			}

			if (resolved.Error != null)
				System.err.println("Error in resolving dependency - "
						+ resolved.Error);

			return resolved;
		}

		// Auto-generated HashCode method that compares ArtifactId, GroupId, and
		// Version
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((ArtifactId == null) ? 0 : ArtifactId.hashCode());
			result = prime * result
					+ ((GroupId == null) ? 0 : GroupId.hashCode());
			result = prime * result
					+ ((Version == null) ? 0 : Version.hashCode());
			return result;
		}

		// Auto-generated Equals method that compares ArtifactId, GroupId, and
		// Version
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RawDependency other = (RawDependency) obj;
			if (ArtifactId == null) {
				if (other.ArtifactId != null)
					return false;
			} else if (!ArtifactId.equals(other.ArtifactId))
				return false;
			if (GroupId == null) {
				if (other.GroupId != null)
					return false;
			} else if (!GroupId.equals(other.GroupId))
				return false;
			if (Version == null) {
				if (other.Version != null)
					return false;
			} else if (!Version.equals(other.Version))
				return false;
			return true;
		}
	}

	String Name;
	String Type;
	String Repo;

	// TODO(rameshvarun): Globs entry

	List<String> Files = new LinkedList<String>();
	String Dir;

	List<RawDependency> Dependencies = new LinkedList<RawDependency>();

	// TODO(rameshvarun): Info field

	Map<String, Object> Data = new HashMap<String, Object>();

	// TODO(rameshvarun): Config list

	Map<String, String> Ops = new HashMap<String, String>();

	public SourceUnit() {
		Ops.put("graph", null);
		Ops.put("depresolve", null);
	}
}
