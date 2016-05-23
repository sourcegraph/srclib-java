package com.sourcegraph.javagraph;


import java.util.LinkedList;
import java.util.List;

/**
 * SourceUnit represents a source unit expected by srclib. A source unit is a
 * build-system- and language-independent abstraction of a Maven repository or
 * Gradle project.
 */
public class SourceUnit extends Key {

    /**
     * Default source unit type
     */
    public static final String DEFAULT_TYPE = "JavaArtifact";

    /**
     * List of files that produce source units
     */
    List<String> Files;


    /**
     * Globs is a list of patterns that match files that make up this source
     * unit. It is used to detect when the source unit definition is out of date
     * (e.g., when a file matches the glob but is not in the Files list).
     */
    List<String> Globs;

    /**
     * Source unit directory
     */
    String Dir;

    /**
     * Source unit dependencies
     */
    List<Key> Dependencies = new LinkedList<>();

    /**
     * Source unit data
     */
    SourceUnitData Data = new SourceUnitData();

    public SourceUnit() {

    }

    /**
     * @return project (aka compiler settings) based on source unit data
     */
    // TODO(rameshvarun): Info field
    public Project getProject() {
        if (MavenProject.is(this)) {
            return new MavenProject(this);
        }
        if (GradleProject.is(this)) {
            return new GradleProject(this);
        }
        if (AntProject.is(this)) {
            return new AntProject(this);
        }
        if (AndroidSDKProject.is(this)) {
            return new AndroidSDKProject(this);
        }
        if (AndroidSupportProject.is(this)) {
            return new AndroidSupportProject(this);
        }
        if (AndroidCoreProject.is(this)) {
            return new AndroidCoreProject(this);
        }
        if (JDKProject.is(this)) {
            return new JDKProject(this);
        }
        return new GenericProject(this);
    }
}
