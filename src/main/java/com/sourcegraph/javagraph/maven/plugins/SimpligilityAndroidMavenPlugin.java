package com.sourcegraph.javagraph.maven.plugins;

import com.sourcegraph.javagraph.PathUtil;
import com.sourcegraph.javagraph.Project;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Adds basic support of http://simpligility.github.io/android-maven-plugin/index.html
 * Generates source files and updates compile source roots to include directories with generated files
 */
public class SimpligilityAndroidMavenPlugin extends AbstractMavenPlugin {

    @Override
    public String getGroupId() {
        return "com.simpligility.maven.plugins";
    }

    @Override
    public String getArtifactId() {
        return "android-maven-plugin";
    }

    @Override
    public void apply(MavenProject project,
                      File repoDir) {
        // Let's create generated source file such as R.java, BuildConfig.java and AIDL-based ones
        runMavenGoal(project.getModel().getPomFile(), repoDir, "generate-test-sources");
        project.getCompileSourceRoots().add(getGeneratedSourceDirectory(project));
        project.getProperties().setProperty(Project.ANDROID_PROPERTY, StringUtils.EMPTY);
    }

    private String getGeneratedSourceDirectory(MavenProject project) {
        String genDirectory = project.getProperties().getProperty("android.genDirectory");
        if (genDirectory == null) {
            return getDefaultGeneratedSourceDirectory(project);
        } else {
            return PathUtil.concat(project.getModel().getProjectDirectory(), genDirectory).toString();
        }
    }

    private static String getDefaultGeneratedSourceDirectory(MavenProject project) {
        File root = PathUtil.concat(project.getModel().getProjectDirectory(), project.getBuild().getDirectory());
        return PathUtil.concat(root, "/generated-sources/r").toString();
    }

}
