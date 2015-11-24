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
 * Adds basic support of Codehaus build helper plugin
 * Updates compile source roots
 */
public class CodehausBuildHelperMavenPlugin extends AbstractMavenPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(CodehausBuildHelperMavenPlugin.class);

    @Override
    public String getGroupId() {
        return "org.codehaus.mojo";
    }

    @Override
    public String getArtifactId() {
        return "build-helper-maven-plugin";
    }

    @Override
    public boolean isStandard() {
        return false;
    }

    /**
     * Updates project compile source roots with "sources" of Codehaus buold helper plugin configuration
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
                continue;
            }
            Xpp3Dom xmlConfiguration = (Xpp3Dom) configuration;
            Xpp3Dom sourcesList[] = xmlConfiguration.getChildren("sources");
            if (sourcesList == null) {
                continue;
            }
            for (Xpp3Dom sources : sourcesList) {
                Xpp3Dom sourceList[] = sources.getChildren("source");
                if (sourceList == null) {
                    continue;
                }
                for (Xpp3Dom source : sourceList) {
                    project.getCompileSourceRoots().add(PathUtil.CWD.resolve(source.getValue()).toString());
                }
            }
        }
    }

}
