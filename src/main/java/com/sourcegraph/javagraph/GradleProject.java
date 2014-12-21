package com.sourcegraph.javagraph;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by sqs on 12/21/14.
 */
public class GradleProject implements Project {
    private SourceUnit unit;
    public GradleProject(SourceUnit unit) {
        this.unit=unit;
    }


    public static String getGradleClassPath(Path build) throws IOException {
        return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).classPath;
    }

    // TODO Merge this function with ‘getGradleDependencies’.
    public static BuildAnalysis.POMAttrs getGradleAttrs(String repoURI, Path build) throws IOException {
        BuildAnalysis.POMAttrs attrs = BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).attrs;

        // HACK: fix the project name inside docker containers. By default, the name of a Gradle project is the name
        // of its containing directory. srclib checks out code to /src inside Docker containers, which makes the name of
        // every Gradle project rooted at the VCS root directory "src". This hack could erroneously change the project
        // name if the name is actually supposed to be "src" (e.g., if the name is set manually).
        if (System.getenv().get("IN_DOCKER_CONTAINER") != null && attrs.artifactID.equals("src")) {
            String[] parts = repoURI.split("/");
            attrs.artifactID = parts[parts.length - 1];
        }

        return attrs;
    }

    public static Path getWrapper() {
        Path result = Paths.get("./gradlew").toAbsolutePath();
        File tmp = new File(result.toString());
        if (tmp.exists() && !tmp.isDirectory()) {
            return result;
        }

        return null;
    }

    public static HashSet<RawDependency> getGradleDependencies(Path build) throws IOException {
        return BuildAnalysis.Gradle.collectMetaInformation(getWrapper(), build).dependencies;
    }

    @Override
    public Set<RawDependency> listDeps() throws Exception {
        return null;
    }

    @Override
    public List<String> getClassPath() throws Exception {
        return null;
    }

    @Override
    public RawDependency getDepForJAR(Path jarFile) throws Exception {
        return null;
    }

    private static SourceUnit createSourceUnit(Path gradleFile, String repoURI) throws IOException, XmlPullParserException {
        BuildAnalysis.POMAttrs attrs = getGradleAttrs(repoURI, gradleFile);

        final SourceUnit unit = new SourceUnit();
        unit.Type = "JavaArtifact";
        unit.Name = attrs.groupID + "/" + attrs.artifactID;
        unit.Dir = gradleFile.getParent().toString();
        unit.Data.put("GradleFile", gradleFile.toString());
        unit.Data.put("Description", attrs.description);

        // TODO: Java source files can be other places besides ‘./src’
        unit.Files = ScanUtil.findAllJavaFiles(gradleFile.getParent().resolve("src"));
        unit.sortFiles();

        // This will list all dependencies, not just direct ones.
        unit.Dependencies = new ArrayList<>(getGradleDependencies(gradleFile));

return unit;
    }


    public static Collection<SourceUnit> findAllSourceUnits(String repoURI) throws IOException {
        HashSet<Path> gradleFiles = ScanUtil.findMatchingFiles("build.gradle");
        List<SourceUnit> units = new ArrayList<>();
        for (Path gradleFile : gradleFiles) {
            try {
                SourceUnit unit = createSourceUnit(gradleFile, repoURI);
                units.add(unit);
            } catch (Exception e) {
                System.err.println("Error processing Gradle build file " + gradleFile + ": " + e.toString());
            }
        }
        return units;
    }
}
