package com.sourcegraph.javagraph;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Created by sqs on 12/21/14.
 */
public class GenericProject implements Project {
    private SourceUnit unit;

    public GenericProject(SourceUnit unit) {
        this.unit=unit;
    }

    @Override
    public Set<RawDependency> listDeps() throws Exception {
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
