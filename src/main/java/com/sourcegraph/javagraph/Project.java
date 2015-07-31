package com.sourcegraph.javagraph;

import java.nio.file.Path;
import java.util.List;

public interface Project {

    public static final String DEFAULT_SOURCE_CODE_VERSION = "1.8";

    public List<String> getClassPath() throws Exception;

    public List<String> getBootClassPath() throws Exception;

    public List<String> getSourcePath() throws Exception;

    public RawDependency getDepForJAR(Path jarFile) throws Exception;

    public String getSourceCodeVersion() throws Exception;

    public String getSourceCodeEncoding() throws Exception;
}
