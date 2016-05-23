package com.sourcegraph.javagraph;


import org.json.JSONObject;

import java.util.*;

/**
 * SourceUnitData holds data specific to java toolchain
 */
public class SourceUnitData {

    /**
     * Source unit sub-type (Maven project, Gradle project, ...)
     */
    String Type;

    /**
     * JDK sub-project name (jdk, langtools, ...)
     */
    String JDKProjectName;

    /**
     * Android-based project
     */
    Boolean Android;

    /**
     * Source code version (javac -source)
     */
    String SourceVersion;

    /**
     * Source code encoding
     */
    String SourceEncoding;

    /**
     * Classpath to use
     */
    Collection<String> ClassPath;

    /**
     * Boot class path to use
     */
    Collection<String> BootClassPath;

    /**
     * Raw dependencies
     */
    Collection<RawDependency> Dependencies = new LinkedList<>();

    /**
     * Source path to use
     */
    Collection<SourcePathElement> SourcePath;

    /**
     * Extra source files (which may be located outside of repo)
     */
    Collection<String> ExtraSourceFiles;

    /**
     * Location of Ant's build.xml
     */
    String BuildXML;

    /**
     * Location of Maven's pom.xml
     */
    String POMFile;

    /**
     * Location of Gradle's build.gradle
     */
    String GradleFile;

    /**
     * Maven's POM data
     */
    JSONObject POM;

    /**
     * @return true if source unit represents Android-based project
     */
    boolean isAndroid() {
        return Android != null && Android;
    }


}
