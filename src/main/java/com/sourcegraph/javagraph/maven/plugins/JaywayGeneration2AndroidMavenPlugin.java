package com.sourcegraph.javagraph.maven.plugins;

/**
 * Adds basic support of com.jayway.maven.plugins.android.generation2:android-maven-plugin (obsolete)
 * Generates source files and updates compile source roots to include directories with generated files
 */
public class JaywayGeneration2AndroidMavenPlugin extends SimpligilityAndroidMavenPlugin {

    @Override
    public String getGroupId() {
        return "com.jayway.maven.plugins.android.generation2";
    }

    @Override
    public String getArtifactId() {
        return "android-maven-plugin";
    }

}
