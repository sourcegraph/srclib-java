package com.sourcegraph.javagraph.maven.plugins;

import com.sourcegraph.javagraph.PathUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Adds basic support of http://simpligility.github.io/android-maven-plugin/index.html
 * Generates source files and updates compile source roots to include directories with generated files
 */
public class SimpligilityAndroidMavenPlugin extends AbstractMavenPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimpligilityAndroidMavenPlugin.class);

    @Override
    public String getGroupId() {
        return "com.simpligility.maven.plugins";
    }

    @Override
    public String getArtifactId() {
        return "android-maven-plugin";
    }

    @Override
    public boolean isStandard() {
        return false;
    }

    /**
     * Invokes 'generate-sources' Maven goal, updates project compile source roots with generated source directories,
     * and marks presence of Android SDK in current source unit
     */
    @Override
    public void apply(MavenProject project,
                      File repoDir) {
        // Let's create generated source file such as R.java, BuildConfig.java and AIDL-based ones
        runMavenGoal(project.getModel().getPomFile(), repoDir, "generate-sources");
        String sourceRoot = getGeneratedSourceDirectory(project);
        LOGGER.debug("Registering source root {}", sourceRoot);
        project.getCompileSourceRoots().add(sourceRoot);
        project.getProperties().setProperty(com.sourcegraph.javagraph.MavenProject.ANDROID_PROPERTY, StringUtils.EMPTY);
    }

    /**
     *
     * @param project Maven project
     * @return project's generated sources directory
     */
    private String getGeneratedSourceDirectory(MavenProject project) {
        String genDirectory = project.getProperties().getProperty("android.genDirectory");
        if (genDirectory == null) {
            return getDefaultGeneratedSourceDirectory(project);
        } else {
            return PathUtil.concat(project.getModel().getProjectDirectory(), genDirectory).toString();
        }
    }

    /**
     *
     * @param project Maven project
     * @return project's default generated sources directory
     */
    private static String getDefaultGeneratedSourceDirectory(MavenProject project) {
        File root = PathUtil.concat(project.getModel().getProjectDirectory(), project.getBuild().getDirectory());
        return PathUtil.concat(root, "generated-sources/r").toString();
    }

}
