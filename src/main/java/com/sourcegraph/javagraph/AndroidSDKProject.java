package com.sourcegraph.javagraph;

import org.apache.maven.model.building.ModelBuildingException;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class AndroidSDKProject implements Project {
    private SourceUnit unit;

    public AndroidSDKProject(SourceUnit unit) {
        this.unit = unit;
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
    public String getSourceCodeEncoding() throws ModelBuildingException, IOException {
        return null;
    }

    public static SourceUnit createSourceUnit(String subdir) throws Exception {
        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = "AndroidSDK";
        unit.Dir = subdir;
        unit.Files = ScanUtil.findAllJavaFiles(FileSystems.getDefault().getPath(subdir));
        unit.Data.put("AndroidSDKSubdir", subdir);
        unit.sortFiles();
        return unit;
    }
}
