package com.sourcegraph.javagraph.maven.plugins;

import com.sourcegraph.javagraph.PathUtil;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Adds basic support of http://www.antlr.org/api/maven-plugin/latest/
 * Generates source files and updates compile source roots to include directories with generated files
 */
public class Antlr4MavenPlugin extends AbstractMavenPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(Antlr4MavenPlugin.class);

    @Override
    public String getGroupId() {
        return "org.antlr";
    }

    @Override
    public String getArtifactId() {
        return "antlr4-maven-plugin";
    }

    @Override
    public boolean isStandard() {
        return false;
    }

    /**
     * Invokes Maven goal generate-sources and updates project's source roots with generated source directories
     */
    @Override
    public void apply(MavenProject project,
                      File repoDir) {
        // Let's create generated source files from ANTLR grammar
        runMavenGoal(project.getModel().getPomFile(), repoDir, "generate-sources");
        project.getCompileSourceRoots().add(getGeneratedSourceDirectory(project));
    }

    /**
     * @param project Maven project
     * @return generated sources directory retrieved from ANTLR plugin's configuration
     */
    private String getGeneratedSourceDirectory(MavenProject project) {
        Plugin buildHelper = getPlugin(project);
        if (buildHelper == null) {
            return getDefaultGeneratedSourceDirectory(project);
        }

        for (PluginExecution pluginExecution : buildHelper.getExecutions()) {
            Object configuration = pluginExecution.getConfiguration();
            if (configuration == null || !(configuration instanceof Xpp3Dom)) {
                continue;
            }
            Xpp3Dom xmlConfiguration = (Xpp3Dom) configuration;
            Xpp3Dom outputDirectory = xmlConfiguration.getChild("outputDirectory");
            if (outputDirectory != null) {
                return new File(outputDirectory.getValue()).getAbsolutePath();
            }
        }
        return getDefaultGeneratedSourceDirectory(project);
    }

    /**
     * @param project Maven project
     * @return default location of generated source files
     */
    private static String getDefaultGeneratedSourceDirectory(MavenProject project) {
        File root = PathUtil.concat(project.getModel().getProjectDirectory(), project.getBuild().getDirectory());
        return PathUtil.concat(root, "generated-sources/antlr4").toString();
    }

}
