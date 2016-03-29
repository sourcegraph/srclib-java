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
 * Adds basic support of Codehaus templating plugin
 * Updates compile source roots
 */
public class CodehausTemplatingMavenPlugin extends AbstractMavenPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodehausTemplatingMavenPlugin.class);

    @Override
    public String getGroupId() {
        return "org.codehaus.mojo";
    }

    @Override
    public String getArtifactId() {
        return "templating-maven-plugin";
    }

    @Override
    public boolean isStandard() {
        return false;
    }

    /**
     * Updates project compile source roots with "sources" of Codehaus templating plugin configuration
     */
    @Override
    public void apply(MavenProject project,
                      File repoDir) {

        Plugin buildHelper = getPlugin(project);
        if (buildHelper == null) {
            return;
        }

        File root = project.getModel().getProjectDirectory();
        for (PluginExecution pluginExecution : buildHelper.getExecutions()) {
            Object configuration = pluginExecution.getConfiguration();
            if (configuration == null || !(configuration instanceof Xpp3Dom)) {
                project.getCompileSourceRoots().add(PathUtil.concat(project.getModel().getProjectDirectory(),
                        "src/main/java-templates").getPath());
                continue;
            }
            Xpp3Dom xmlConfiguration = (Xpp3Dom) configuration;
            Xpp3Dom sourceDirDom = xmlConfiguration.getChild("sourceDirectory");
            if (sourceDirDom == null) {
                project.getCompileSourceRoots().add(PathUtil.concat(project.getModel().getProjectDirectory(),
                        "src/main/java-templates").getPath());
            } else {
                project.getCompileSourceRoots().add(PathUtil.CWD.resolve(sourceDirDom.getValue()).toString());
            }
        }
    }

}
