package com.sourcegraph.javagraph.maven.plugins;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;

/**
 * Adds basic support of Maven compiler plugin.
 * Extracts source code version and encoding
 */
public class MavenCompilerPlugin extends AbstractMavenPlugin {

    @Override
    public String getGroupId() {
        return "org.apache.maven.plugins";
    }

    @Override
    public String getArtifactId() {
        return "maven-compiler-plugin";
    }

    @Override
    public boolean isStandard() {
        return true;
    }

    /**
     * Extracts source code version and encoding from compiler plugin configuration
     */
    @Override
    public void apply(MavenProject project,
                      File repoDir) {
        Plugin compile = getPlugin(project);
        Object configuration = compile.getConfiguration();
        if (configuration == null || !(configuration instanceof Xpp3Dom)) {
            return;
        }
        Xpp3Dom xmlConfiguration = (Xpp3Dom) configuration;

        extractSourceCodeEncoding(project, xmlConfiguration);
        extractSourceCodeVersion(project, xmlConfiguration);
    }

    /**
     * Extracts source code version from plugin configuration
     * @param project Maven project
     * @param xmlConfiguration XML configuration object to search for data
     */
    private static void extractSourceCodeVersion(MavenProject project, Xpp3Dom xmlConfiguration) {
        Xpp3Dom source = xmlConfiguration.getChild("source");
        if (source == null) {
            String propertyValue = project.getProperties().getProperty("maven.compiler.source");
            if (propertyValue != null) {
                project.getProperties().setProperty(
                        com.sourcegraph.javagraph.MavenProject.SOURCE_CODE_VERSION_PROPERTY,
                        propertyValue);
            }
            return;
        }
        project.getProperties().setProperty(
                com.sourcegraph.javagraph.MavenProject.SOURCE_CODE_VERSION_PROPERTY,
                source.getValue());
    }

    /**
     * Extracts source code encoding from plugin configuration
     * @param project Maven project
     * @param xmlConfiguration XML configuration object to search for data
     */
    private static void extractSourceCodeEncoding(MavenProject project, Xpp3Dom xmlConfiguration) {
        Xpp3Dom source = xmlConfiguration.getChild("encoding");
        if (source == null) {
            return;
        }
        project.getProperties().setProperty(
                com.sourcegraph.javagraph.MavenProject.SOURCE_CODE_ENCODING_PROPERTY,
                source.getValue());
    }

}
