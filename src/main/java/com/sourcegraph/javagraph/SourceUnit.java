package com.sourcegraph.javagraph;


import org.apache.commons.lang3.StringUtils;

import java.util.*;

/**
 * SourceUnit represents a source unit expected by srclib. A source unit is a
 * build-system- and language-independent abstraction of a Maven repository or
 * Gradle project.
 */
public class SourceUnit {

    /**
     * Source unit type (may be located in the Data, optional)
     */
    public static final String TYPE = "Type";

    /**
     * Source unit name
     */
    String Name;

    /**
     * Source unit type
     */
    String Type;

    /**
     * List of files that produce source units
     */
    List<String> Files = new LinkedList<>();

    /**
     * Source unit directory
     */
    String Dir;

    /**
     * Source unit dependencies
     */
    List<RawDependency> Dependencies = new LinkedList<>();

    // TODO(rameshvarun): Globs entry
    /**
     * Source unit raw data
     */
    Map<String, Object> Data = new HashMap<>();

    /**
     * Source unit ops data
     */
    Map<String, String> Ops = new HashMap<>();

    {
        Ops.put("graphJavaFiles", null);
        Ops.put("depresolve", null);
    }

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
        if (AndroidSDKProject.is(this)) {
            return new AndroidSDKProject(this);
        }
        if (AndroidCoreProject.is(this)) {
            return new AndroidCoreProject(this);
        }
        if (JDKProject.is(this)) {
            return new JDKProject(this);
        }
        return new GenericProject(this);
    }

    @Override
    public int hashCode() {
        return Name == null ? 0 : Name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof SourceUnit)) {
            return false;
        }
        return StringUtils.equals(Name, ((SourceUnit) o).Name);
    }
}
