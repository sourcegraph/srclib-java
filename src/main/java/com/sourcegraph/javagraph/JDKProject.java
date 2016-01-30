package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.building.ModelBuildingException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Set of rules to compile OpenJDK projects (hg.openjdk.java.net/jdk8/jdk8/*)
 * - no bootstrap classpath for OpenJDK's JDK
 * - include tools.jar into classpath
 * - include generated files if found into source path
 */
public class JDKProject implements Project {

    private static final String MARKER_JDK = "JDK";
    private static final String MARKER_JDK_BASED = "JDKBased";

    private static final String JDK_PROJECT_NAME = "JDKProjectName";

    public static final String OPENJDK_REPO_ROOT = "hg.openjdk.java.net/jdk8/jdk8/";

    public static final String JDK_REPO = OPENJDK_REPO_ROOT + "jdk";
    public static final String TOOLS_JAR_REPO = OPENJDK_REPO_ROOT + "langtools";
    public static final String NASHORN_REPO = OPENJDK_REPO_ROOT + "nashorn";


    private SourceUnit unit;

    public JDKProject(SourceUnit unit) {
        this.unit = unit;
    }

    /**
     * @return empty list (no boot class path) when graphing OpenJDK's JDK
     */
    @Override
    public List<String> getBootClassPath() {
        if (isJDK(unit)) {
            return Collections.emptyList();
        }
        return null;
    }

    /**
     * @return tools.jar
	 * TODO(sqs): tools.jar was removed in java9, update this to account for that
     */
    @Override
    public List<String> getClassPath() {
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
    public List<String> getSourcePath() {
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
        String project = (String) unit.Data.get(JDK_PROJECT_NAME);
        if (project != null) {
            // TODO (alexsaveliev) support other build configurations?
            sourcePaths.add("../build/linux-x86_64-normal-server-release/" + project + "/gensrc");
            sourcePaths.add("../build/linux-x86_64-normal-server-release/" + project + "/impsrc");
        }

        return sourcePaths.stream().filter(element -> PathUtil.CWD.resolve(element).toFile().isDirectory()).
                collect(Collectors.toList());
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

    /**
     * @param unit source unit to check
     * @return true if given unit contains OpenJDK source code or related OpenJDK project such as nashorn
     */
    public static boolean is(SourceUnit unit) {
        return isJDK(unit) || isJDKBased(unit);
    }

    /**
     * @param unit source unit to check
     * @return true if given unit contains OpenJDK source code
     */
    protected static boolean isJDK(SourceUnit unit) {
        return MARKER_JDK.equals(unit.Data.get(SourceUnit.TYPE));
    }

    /**
     * @param unit source unit to check
     * @return true if given unit contains related OpenJDK project (such as nashorn) code
     */
    protected static boolean isJDKBased(SourceUnit unit) {
        return MARKER_JDK_BASED.equals(unit.Data.get(SourceUnit.TYPE));
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

        return sourcePaths.stream().filter(element -> PathUtil.CWD.resolve(element).toFile().isDirectory()).
                collect(Collectors.toList());
    }

}
