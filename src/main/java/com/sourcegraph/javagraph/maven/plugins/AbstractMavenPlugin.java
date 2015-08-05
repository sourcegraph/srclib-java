package com.sourcegraph.javagraph.maven.plugins;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import java.util.List;

public abstract class AbstractMavenPlugin implements MavenPlugin {

    public abstract String getGroupId();

    public abstract String getArtifactId();

    public boolean isApplicable(MavenProject project) {
        return getPlugin(project) != null;
    }

    protected Plugin getPlugin(MavenProject project) {
        List<Plugin> plugins = project.getBuildPlugins();
        if (plugins == null) {
            return null;
        }
        for (Plugin plugin : plugins) {
            if (plugin.getGroupId().equals(getGroupId()) &&
                    plugin.getArtifactId().equals(getArtifactId())) {
                return plugin;
            }
        }
        return null;
    }

}
