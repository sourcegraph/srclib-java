package com.sourcegraph.javagraph;


import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * SourceUnit represents a source unit expected by srclib. A source unit is a
 * build-system- and language-independent abstraction of a Maven repository or
 * Gradle project.
 */
public class SourceUnit {
    String Name;
    String Type;
    String Repo;
    List<String> Files = new LinkedList<>();
    String Dir;
    List<RawDependency> Dependencies = new LinkedList<>();

    // TODO(rameshvarun): Globs entry
    Map<String, Object> Data = new HashMap<>();
    Map<String, String> Ops = new HashMap<>();

    {
        Ops.put("graphJavaFiles", null);
        Ops.put("depresolve", null);
    }

    public SourceUnit() {

    }

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
        if (Data.containsKey("JDK")) {
            return new JDKProject(this);
        }
        return new GenericProject(this);
    }

    public void sortFiles() {
        Files.sort(String::compareTo);
    }
}
