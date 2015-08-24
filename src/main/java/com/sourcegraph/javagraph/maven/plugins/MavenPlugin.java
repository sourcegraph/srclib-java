package com.sourcegraph.javagraph.maven.plugins;

import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Adds special processing of maven project.
 * For example, plugin may add extra properties, do a pre-processing or modify
 * project properties
 */
public interface MavenPlugin {

    boolean isApplicable(MavenProject project);

    void apply(MavenProject project, File repoDir);

}
