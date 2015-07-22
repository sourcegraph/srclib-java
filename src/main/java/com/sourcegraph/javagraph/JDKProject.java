package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.building.ModelBuildingException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class JDKProject implements Project {
    private SourceUnit unit;

    public JDKProject(SourceUnit unit) {
        this.unit=unit;
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
    public String getSourceCodeVersion() throws ModelBuildingException, IOException {

        return DEFAULT_SOURCE_CODE_VERSION;
    }

    @Override
    public String getSourceCodeEncoding() throws ModelBuildingException, IOException {
        return null;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        return null;
    }

    private static List<String> getJDKSourcePaths() throws Exception {
        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add("src/share/classes/");

        if (SystemUtils.IS_OS_WINDOWS) {
            sourcePaths.add("src/windows/classes/");
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            sourcePaths.add("src/macosx/classes/");
        } else if (SystemUtils.IS_OS_LINUX) {
            sourcePaths.add("src/linux/classes/");
        } else if (SystemUtils.IS_OS_FREE_BSD || SystemUtils.IS_OS_NET_BSD || SystemUtils.IS_OS_OPEN_BSD) {
            sourcePaths.add("src/bsd/classes/");
        } else if (SystemUtils.IS_OS_SOLARIS) {
            sourcePaths.add("src/solaris/classes/");
        }

        return sourcePaths;
    }

    public static Collection<SourceUnit> standardSourceUnits() throws Exception {
        List<SourceUnit> units = new ArrayList<>();

        // Java SDK Unit
        final SourceUnit unit = new SourceUnit();
        unit.Type = "Java";
        unit.Name = ".";
        unit.Dir = "src/";
        unit.Files = ScanUtil.scanFiles(getJDKSourcePaths());
        unit.Data.put("JDK", true);
        unit.sortFiles();
        units.add(unit);

        // Build tools source unit
        final SourceUnit toolsUnit = new SourceUnit();
        toolsUnit.Type = "JavaArtifact";
        toolsUnit.Name = "BuildTools";
        toolsUnit.Dir = "make/src/classes/";
        toolsUnit.Files = ScanUtil.scanFiles("make/src/classes/");
        toolsUnit.sortFiles();
        units.add(toolsUnit);

        return units;
    }
}
