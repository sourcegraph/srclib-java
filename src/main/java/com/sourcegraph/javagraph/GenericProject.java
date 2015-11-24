package com.sourcegraph.javagraph;

import java.nio.file.Path;
import java.util.List;

/**
 * Generic java project, defines no specific javac options
 */
public class GenericProject implements Project {
    private SourceUnit unit;

    public GenericProject(SourceUnit unit) {
        this.unit=unit;
    }

    @Override
    public void init() {
    }

    @Override
    public List<String> getBootClassPath() throws Exception {
        return null;
    }

    @Override
    public List<String> getClassPath() throws Exception {
        return null;
    }

    @Override
    public List<String> getSourcePath() throws Exception {
        return null;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        return null;
    }

    @Override
    public String getSourceCodeVersion() throws Exception {
        return DEFAULT_SOURCE_CODE_VERSION;
    }

    @Override
    public String getSourceCodeEncoding() throws Exception {
        return null;
    }

}
