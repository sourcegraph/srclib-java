package com.sourcegraph.javagraph.maven.plugins;

import com.google.common.reflect.ClassPath;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Holds list of registered maven plugins, applies them for a given Maven project with the aim to extract some
 * properties, update source locations and so on
 */
public class MavenPlugins {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenPlugins.class);

    private static MavenPlugins instance = new MavenPlugins();

    private Collection<MavenPlugin> plugins;

    /**
     * @return singleton instance
     */
    public static MavenPlugins getInstance() {
        return instance;
    }

    /**
     * Registers new plugin
     * @param plugin plugin to register
     */
    public void addPlugin(MavenPlugin plugin) {
        getPlugins().add(plugin);
    }

    /**
     * Applies all registered plugins to a project
     * @param project project to apply plugins for
     * @param repoDir Maven repository dir to use
     */
    public void apply(MavenProject project, File repoDir) {
        for (MavenPlugin plugin : getPlugins()) {
            if (plugin.isApplicable(project)) {
                plugin.apply(project, repoDir);
            }
        }
    }

    private MavenPlugins() {
    }

    private Collection<MavenPlugin> getPlugins() {
        if (plugins == null) {
            plugins = new ArrayList<>();

            try {
                ClassPath classPath = ClassPath.from(this.getClass().getClassLoader());
                // TODO alexsaveliev: add ability to search in custom packages
                for (ClassPath.ClassInfo info : classPath.getTopLevelClasses(this.getClass().getPackage().getName())) {
                    Class c = info.load();
                    if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
                        continue;
                    }
                    if (MavenPlugin.class.isAssignableFrom(c)) {
                        try {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("Registering Maven plugin " + info.getName());
                            }
                            plugins.add((MavenPlugin) c.newInstance());
                        } catch (InstantiationException | IllegalAccessException e) {
                            LOGGER.warn("Failed to register Maven plugin " + info.getName());
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Unable to initialize Maven plugins", e);
            }
        }
        return plugins;
    }


}
