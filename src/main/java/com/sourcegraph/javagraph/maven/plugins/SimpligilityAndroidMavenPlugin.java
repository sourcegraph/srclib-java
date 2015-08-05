package com.sourcegraph.javagraph.maven.plugins;

import com.sourcegraph.javagraph.PathUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

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
    public void apply(MavenProject project,
                      File repoDir) {
        // Let's create generated source file such as R.java, BuildConfig.java and AIDL-based ones
        doGenerateSources(project.getModel().getPomFile(), repoDir);
        project.getCompileSourceRoots().add(getGeneratedSourceDirectory(project));
    }

    private static void doGenerateSources(File pomFile, File repoDir) {

        String mvnCmd;
        if (SystemUtils.IS_OS_WINDOWS) {
            mvnCmd = "mvn.bat";
        } else {
            mvnCmd = "mvn";
        }
        String cmd[] = new String[] {
                mvnCmd,
                "-Dmaven.repo.local=" + repoDir,
                "-f", pomFile.getAbsolutePath(),
                "generate-test-sources"
        };

        // let's execute "mvn generate-test-sources" that will do the job
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(pomFile.getParentFile());
        pb.redirectErrorStream(true);
        File log = null;
        try {
            log = File.createTempFile("srclib-mvn", "log");
            pb.redirectOutput(log);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Starting {} and logging results into {}", (Object[]) cmd, log);
            }
            Process p = pb.start();
            int status = p.waitFor();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Maven output was {}", FileUtils.readFileToString(log));
            }
            if (status != 0) {
                LOGGER.warn("Exit status of {} was {}", cmd, status);
            }
        } catch (IOException | InterruptedException e) {
            LOGGER.warn("Unable to compile Maven project using command {}", cmd, e);
        } finally {
            FileUtils.deleteQuietly(log);
        }
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
