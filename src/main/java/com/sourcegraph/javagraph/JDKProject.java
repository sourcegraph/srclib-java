package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.building.ModelBuildingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class JDKProject implements Project {

    public static final String OPENJDK_REPO_ROOT = "hg.openjdk.java.net/jdk8/jdk8/";

    public static final String JDK_REPO = OPENJDK_REPO_ROOT + "jdk";
    public static final String TOOLS_JAR_REPO = OPENJDK_REPO_ROOT + "langtools";
    public static final String NASHORN_REPO = OPENJDK_REPO_ROOT + "nashorn";


    private SourceUnit unit;

    public JDKProject(SourceUnit unit) {
        this.unit = unit;
    }

    @Override
    public List<String> getBootClassPath() throws Exception {
        if (unit.Repo.equals(JDK_REPO)) {
            return Collections.emptyList();
        }
        return null;
    }

    @Override
    public List<String> getClassPath() throws Exception {
        File javaHome = SystemUtils.getJavaHome();
        if (javaHome.isDirectory()) {
            File toolsJar = new File(javaHome, "lib/tools.jar");
            if (toolsJar.isFile()) {
                return Collections.singletonList(toolsJar.getAbsolutePath());
            }
            // JAVA_HOME may point to JRE, trying one level up
            toolsJar = new File(javaHome, "../lib/tools.jar");
            if (toolsJar.isFile()) {
                return Collections.singletonList(toolsJar.getAbsolutePath());
            }
        }
        return null;
    }

    @Override
    public List<String> getSourcePath() throws Exception {
        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add("src/share/classes/");
        sourcePaths.add("src/share/jaxws_classes/");
        sourcePaths.add("src/share/jaf_classes/");
        sourcePaths.add("src/share/");
        sourcePaths.add("src/");

        if (SystemUtils.IS_OS_WINDOWS) {
            sourcePaths.add("src/windows/classes/");
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            sourcePaths.add("src/macosx/classes/");
        } else if (SystemUtils.IS_OS_LINUX) {
            sourcePaths.add("src/linux/classes/");
            sourcePaths.add("src/solaris/classes/");
        } else if (SystemUtils.IS_OS_FREE_BSD || SystemUtils.IS_OS_NET_BSD || SystemUtils.IS_OS_OPEN_BSD) {
            sourcePaths.add("src/bsd/classes/");
            sourcePaths.add("src/solaris/classes/");
        } else if (SystemUtils.IS_OS_SOLARIS) {
            sourcePaths.add("src/solaris/classes/");
        }

        // adding project's generated sources dir, if any
        if (unit.Repo.startsWith(OPENJDK_REPO_ROOT)) {
            // TODO (alexsaveliev) support other build configurations?
            String project = unit.Repo.substring(OPENJDK_REPO_ROOT.length());
            sourcePaths.add("../build/linux-x86_64-normal-server-release/" + project + "/gensrc");
            sourcePaths.add("../build/linux-x86_64-normal-server-release/" + project + "/impsrc");
        }

        return sourcePaths.stream().filter(element -> new File(element).isDirectory()).collect(Collectors.toList());
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
        sourcePaths.add("src/share/jaxws_classes/");
        sourcePaths.add("src/share/jaf_classes/");
        sourcePaths.add("src/share/vm/");
        sourcePaths.add("src/share/tools/");
        sourcePaths.add("src/org/");
        sourcePaths.add("src/javax/");
        sourcePaths.add("src/com/");
        sourcePaths.add("src/jdk/");

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

        return sourcePaths.stream().filter(element -> new File(element).isDirectory()).collect(Collectors.toList());
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
        units.add(unit);

        addKnownSourceUnit(units, "make/src/classes/");
        addKnownSourceUnit(units, "buildtools/nasgen/");

        return units;
    }

    private static void addKnownSourceUnit(Collection<SourceUnit> units, String directory) throws IOException {
        File dir = new File(directory);
        if (dir.isDirectory()) {
            // Build tools source unit
            final SourceUnit toolsUnit = new SourceUnit();
            toolsUnit.Type = "JavaArtifact";
            toolsUnit.Name = "BuildTools";
            toolsUnit.Dir = directory;
            toolsUnit.Files = ScanUtil.scanFiles(directory);
            toolsUnit.Data.put("JDK", true);
            units.add(toolsUnit);
        }
    }
}
