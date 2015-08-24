package com.sourcegraph.javagraph.maven.plugins;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class AbstractMavenPlugin implements MavenPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractMavenPlugin.class);

    public abstract String getGroupId();

    public abstract String getArtifactId();

    /**
     * Some plugins are standard ones and should be applied anyway, regardless if they were listed in subproject's POM
     * For example, compiler plugin may be omitted in plugins section
     * @return true if we should search for a given plugin in plugin management section as well
     */
    public abstract boolean isStandard();

    private static String mavenCmd;

    public boolean isApplicable(MavenProject project) {
        return getPlugin(project) != null;
    }

    protected Plugin getPlugin(MavenProject project) {
        List<Plugin> plugins = project.getBuildPlugins();
        if (plugins == null) {
            return isStandard() ? getPluginFromManagement(project) : null;
        }
        Plugin p = getMatching(project.getBuildPlugins());
        if (p == null) {
            return isStandard() ? getPluginFromManagement(project) : null;
        }
        return p;
    }

    protected Plugin getPluginFromManagement(MavenProject project) {
        PluginManagement management = project.getPluginManagement();
        if (management == null) {
            return null;
        }
        return getMatching(management.getPlugins());
    }

    protected Plugin getMatching(List<Plugin> plugins) {
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

    protected static void runMavenGoal(File pomFile, File repoDir, String goal) {

        String cmd[] = new String[] {
                getMavenCmd(),
                "-Dmaven.repo.local=" + repoDir,
                "-f", pomFile.getAbsolutePath(),
                goal
        };

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(pomFile.getParentFile());
        pb.redirectErrorStream(true);
        File log = null;
        try {
            log = File.createTempFile("srclib-mvn", "log");
            pb.redirectOutput(log);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Starting [{}] and logging results into {}", StringUtils.join(cmd, ' '), log);
            }
            Process p = pb.start();
            int status = p.waitFor();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Maven output was {}", FileUtils.readFileToString(log));
            }
            if (status != 0) {
                LOGGER.warn("Exit status of [{}] was {}", StringUtils.join(cmd, ' '), status);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("Unable to compile Maven project using command [{}]", StringUtils.join(cmd, ' '), e);
        } finally {
            FileUtils.deleteQuietly(log);
        }
    }

    protected static String getMavenCmd() {
        if (mavenCmd == null) {
            if (SystemUtils.IS_OS_WINDOWS) {
                // since 3.3 command name is mvn.cmd, before - mvn.bat
                // let's try to run mvn -v and see if mvn.cmd works
                try {
                    Runtime.getRuntime().exec(new String[] {"mvn.cmd",  "-v"});
                    mavenCmd = "mvn.cmd";
                } catch (IOException e) {
                    mavenCmd = "mvn.bat";
                }
            } else {
                mavenCmd = "mvn";
            }
        }
        return mavenCmd;
    }


}
