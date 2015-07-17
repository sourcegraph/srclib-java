package com.sourcegraph.javagraph;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Project {

    public static final String DEFAULT_SOURCE_CODE_VERSION = "1.8";

    public Set<RawDependency> listDeps() throws Exception;

    public List<String> getClassPath() throws Exception;

    public RawDependency getDepForJAR(Path jarFile) throws Exception;

    public String getSourceCodeVersion() throws Exception;
}
