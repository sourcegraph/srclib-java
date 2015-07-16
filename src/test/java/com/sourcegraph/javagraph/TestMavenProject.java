package com.sourcegraph.javagraph;

import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TestMavenProject {

	@Before()
	public void setUp() throws Exception {
		FileUtils.deleteDirectory(new File(MavenProject.getRepoDir()));
	}

	@After
	public void tearDown() throws Exception {
		FileUtils.deleteDirectory(new File(MavenProject.getRepoDir()));
	}

	/**
	 * Making sure that all artifacts are resolvable, including the following cases:
	 * - property substitution (${...})
	 * - dependency manager + no version specified in dependency definition
	 * - version ranges
	 */
	@Test
	public void testResolveMavenDependencyArtifacts() throws Exception {
		MavenProject project = new MavenProject(Paths.get("src/test/resources/pom-dependencies.xml"));
		Set<Artifact> artifacts = project.resolveMavenDependencyArtifacts();
		// alexsaveliev: please note, that transient artifacts are included too
		assertEquals("Some artifacts were unresolved", 14, artifacts.size());
	}

	/**
	 * Making sure that we are able to fetch data from custom remote repositories
	 */
	@Test
	public void testRepositories() throws Exception {
		MavenProject project = new MavenProject(Paths.get("src/test/resources/pom-repositories.xml"));
		Set<Artifact> artifacts = project.resolveMavenDependencyArtifacts();
		// alexsaveliev: please note, that transient artifacts are included too
		assertEquals("Some artifacts were unresolved, probably repository was not resolved", 6, artifacts.size());
	}

}
