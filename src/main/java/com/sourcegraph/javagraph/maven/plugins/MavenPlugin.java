package com.sourcegraph.javagraph.maven.plugins;

import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Adds special processing of Maven projects.
 * For example, plugin may add extra properties, do a pre-processing or modify project properties
 */
public interface MavenPlugin {

    /**
     * Indicates if current plugin is applicable to given Maven project
     * @param project Maven project to check plugin against
     * @return true if current plugin is applicable, false otherwise
     */
    boolean isApplicable(MavenProject project);

    /**
     * Applies current plugin against given project
     * @param project Maven project to apply plugin against
     * @param repoDir Maven repository directory to use
     */
    void apply(MavenProject project, File repoDir);

}
