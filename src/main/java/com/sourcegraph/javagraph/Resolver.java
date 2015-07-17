package com.sourcegraph.javagraph;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Resolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(Resolver.class);

    private final Project proj;

    private Map<String, DepResolution> depsCache;

    public Resolver(Project proj) {
        this.proj = proj;
        this.depsCache = new HashMap<>();
    }

    private Map<URI,ResolvedTarget> resolvedOrigins = new HashMap<>();

    public ResolvedTarget resolveOrigin(URI origin) throws Exception {
        if (origin == null) return null;
        if (resolvedOrigins.containsKey(origin)) {
            return resolvedOrigins.get(origin);
        }

        Path jarFile;
        try {
            jarFile = getOriginJARFilePath(origin);
        } catch (URISyntaxException e) {
            LOGGER.warn("Error getting origin file path for origin: {}", origin, e);
            resolvedOrigins.put(origin, null);
            return null;
        }

        ResolvedTarget target = procesSpecialJar(origin, jarFile);
        if (target != null) {
            return target;
        }

        RawDependency rawDep = null;
        try {
            rawDep = proj.getDepForJAR(jarFile);
        } catch (Exception e) {
            LOGGER.warn("Error resolving JAR file path {} to dependency", jarFile, e);
        }
        if (rawDep == null) {
            resolvedOrigins.put(origin, null);
            return null;
        }

        DepResolution res = resolveRawDep(rawDep);
        if (res.Error != null) {
            LOGGER.warn("Error resolving raw dependency {} to dep taget", rawDep, res.Error);
            resolvedOrigins.put(origin, null);
            return null;
        }
        resolvedOrigins.put(origin, res.Target);
        return res.Target;
    }

    /**
     * Detects special JAR files
     * @param origin origin to check
     * @param jarFile jar file we are working with
     * @return resolved target if jar file matches known one
     */
    private ResolvedTarget procesSpecialJar(URI origin, Path jarFile) {
        if (PathUtil.normalize(jarFile.toString()).contains("jre/lib/")) {
            ResolvedTarget target = ResolvedTarget.jdk();
            resolvedOrigins.put(origin, target);
            return target;
        } else if (jarFile.getFileName().toString().equals("tools.jar")) {
            ResolvedTarget target = ResolvedTarget.langtools();
            resolvedOrigins.put(origin, target);
            return target;
        } else if (jarFile.getFileName().toString().equals("nashorn.jar")) {
            ResolvedTarget target = ResolvedTarget.nashorn();
            resolvedOrigins.put(origin, target);
            return target;
        }
        return null;
    }

    /**
     * @return the origin JAR file as a Path if its URI is a "jar:file:" or "file:" URI. For "jar:file:" URIs, the path inside the JAR after the "!" is stripped.
     */
    private static Path getOriginJARFilePath(URI origin) throws URISyntaxException {
        if (origin == null) return null;
        if (origin.getScheme().equals("jar")) {
            URI fileURI = new URI(origin.getRawSchemeSpecificPart());
            if (!fileURI.getScheme().equals("file")) {
                throw new URISyntaxException(origin.toString(), "def origin URI must be a jar:file: URI, not jar:" + fileURI.getScheme());
            }
            File localFile = new File(fileURI);

            // Split on the "!" (in, e.g., "jar:file:/path/to/my.jar!/path/to/class/file.class").
            String path = localFile.getPath();
            int i = path.indexOf('!');
            if (i != -1) {
                path = path.substring(0, i);
            }
            return FileSystems.getDefault().getPath(path);
        }
        throw new URISyntaxException(origin.toString(), "def origin URI must be a jar:file: URI");
    }

    /**
     * Provide Clone URL overrides for different groupid/artifactid source
     * units
     */
    static HashMap<String, String> overrides = new HashMap<String, String>() {
        {
            put("org.hamcrest/", "https://github.com/hamcrest/JavaHamcrest");
            put("com.badlogicgames.gdx/",
                    "https://github.com/libgdx/libgdx");
            put("com.badlogicgames.jglfw/",
                    "https://github.com/badlogic/jglfw");
            put("org.json/json",
                    "https://github.com/douglascrockford/JSON-java");
            put("junit/junit", "https://github.com/junit-team/junit");
            put("org.apache.commons/commons-csv", "https://github.com/apache/commons-csv");
            put("org.slf4j/slf4j-api", "https://github.com/qos-ch/slf4j");
            put("org.mongodb.morphia/morphia", "https://github.com/mongodb/morphia");
            put("com.google.guava/guava", "https://github.com/google/guava");
        }
    };

    /**
     * @param lookup GroupID + "/" + ArtifactID
     * @return A VCS url, if an override was found, null if not.
     */
    public static String checkOverrides(String lookup) {
        for (String key : overrides.keySet()) {
            if (lookup.startsWith(key))
                return overrides.get(key);
        }
        return null;
    }

    /**
     * Try to resolveOrigin this raw Dependency to its VCS target.
     *
     * @return The DepResolution Object. Error will be non-null if a DepResolution
     * could not be performed.
     */
    public DepResolution resolveRawDep(RawDependency d) {

        String key = d.groupID + ':' + d.artifactID + ':' + d.version + ':' + d.scope;
        DepResolution resolution = depsCache.get(key);
        if (resolution != null) {
            return resolution;
        }

        // HACK: Assume that if the groupID of the RawDependency equals the groupID of the current project, then it is from the same repo and shouldn't be resolved externally.
        if (this.proj instanceof MavenProject) {
            MavenProject mvnProj = (MavenProject)this.proj;
            String depGroupID = null;
            try {
                depGroupID = mvnProj.getMavenProject().getGroupId();
            } catch (ModelBuildingException e) {
                LOGGER.warn("Failed to build Maven model", e);
            }
            if (depGroupID != null && depGroupID.equals(d.groupID)) {
                ResolvedTarget target = new ResolvedTarget();
                target.ToUnit = d.groupID + "/" + d.artifactID;
                target.ToUnitType = "JavaArtifact";
                target.ToVersionString = d.version;
                resolution = new DepResolution(d, target);
                depsCache.put(key, resolution);
                return resolution;
            }
        }

        // Get the url to the POM file for this artifact
        String url = "http://central.maven.org/maven2/"
                + d.groupID.replace(".", "/") + "/" + d.artifactID + "/"
                + d.version + "/" + d.artifactID + "-" + d.version + ".pom";

        DepResolution res = new DepResolution(d, null);

        try {
            String cloneURL = checkOverrides(d.groupID + "/" + d.artifactID);

            if (cloneURL == null) {
                InputStream input = new BOMInputStream(new URL(url).openStream());

                MavenXpp3Reader xpp3Reader = new MavenXpp3Reader();
                Model model = xpp3Reader.read(input);
                input.close();

                Scm scm = model.getScm();
                if (scm != null) cloneURL = scm.getUrl();
            }

            if (cloneURL != null) {
                res.Raw = d;

                ResolvedTarget target = new ResolvedTarget();
                target.ToRepoCloneURL = cloneURL;
                target.ToUnit = d.groupID + "/" + d.artifactID;
                target.ToUnitType = "JavaArtifact";
                target.ToVersionString = d.version;

                res.Target = target;
            } else {
                res.Error = d.artifactID + " does not have an associated SCM repository.";
            }

        } catch (Exception e) {
            res.Error = "Could not download file " + e.getMessage();
        }

        if (res.Error != null)
            LOGGER.warn("Error in resolving dependency {} - {}", d, res.Error);
        depsCache.put(key, res);
        return res;
    }
}
