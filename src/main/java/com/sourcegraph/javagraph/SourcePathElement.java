package com.sourcegraph.javagraph;


import org.apache.commons.lang3.StringUtils;

/**
 * Source path element provides mapping between file path and (unit name, version).
 * File path may be used to include it when compiling source code and (unit name, version) to map
 * source file from given path to sibling source unit
 */
public class SourcePathElement {

    /**
     * Matching source unit name (groupid/artifactid)
     */
    String name;

    /**
     * Artifact version
     */
    String version;

    /**
     * FS path where source files for a given source unit may be found
     */
    String filePath;

    /**
     * Constructs source path element
     * @param filePath path where source files for a given source unit may be found
     */
    public SourcePathElement(String filePath) {
        this(StringUtils.EMPTY, StringUtils.EMPTY, filePath);
    }

    /**
     * Constructs source path element
     * @param name source unit name
     * @param version artifact version
     * @param filePath path where source files for a given source unit may be found
     */
    public SourcePathElement(String name, String version, String filePath) {
        this.name = name;
        this.version = version;
        this.filePath = filePath;
    }


}
