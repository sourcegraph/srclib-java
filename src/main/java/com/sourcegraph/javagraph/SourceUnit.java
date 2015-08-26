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
     * Source unit name
     */
    String Name;

    /**
     * Source unit type
     */
    String Type;

    /**
     * Repo URI
     */
    String Repo;

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
        if (Data.containsKey("POMFile")) {
            return new MavenProject(this);
        }
        if (Data.containsKey("GradleFile")) {
            return new GradleProject(this);
        }
        if (Data.containsKey("AndroidSDKSubdir")) {
            return new AndroidSDKProject(this);
        }
        if (Data.containsKey("AndroidCoreSubdir")) {
            return new AndroidCoreProject(this);
        }
        if (Data.containsKey("JDK")) {
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
