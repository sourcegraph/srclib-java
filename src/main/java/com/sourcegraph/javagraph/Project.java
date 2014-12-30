package com.sourcegraph.javagraph;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public interface Project {
    public Set<RawDependency> listDeps() throws Exception;

    public List<String> getClassPath() throws Exception;

    public RawDependency getDepForJAR(Path jarFile) throws Exception;
}
