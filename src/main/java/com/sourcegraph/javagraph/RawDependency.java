package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;

/**
 * A Raw, unresolved source unit dependency.
 */
public class RawDependency {

    /**
     * Artifact group ID
     */
    String groupID;
    /**
     * Artifact ID
     */
    String artifactID;
    /**
     * Artifact version
     */
    String version;
    /**
     * Dependency scope
     */
    String scope;
    /**
     * Artifact file
     */
    String file;
    /**
     * Classifier
     */
    String classifier;
    /**
     * Type (jar, pom, etc)
     */
    String type;
    /**
     * SCM URI
     */
    String repoURI;

    public RawDependency(String groupID, String artifactID, String version, String scope, String file) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.version = version;
        this.scope = scope;
        this.file = file;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawDependency that = (RawDependency) o;

        if (!StringUtils.equals(artifactID, that.artifactID)) {
            return false;
        }
        if (!StringUtils.equals(groupID, that.groupID)) {
            return false;
        }
        if (!StringUtils.equals(scope, that.scope)) {
            return false;
        }
        if (!StringUtils.equals(version, that.version)) {
            return false;
        }
        if (!StringUtils.equals(type, that.type)) {
            return false;
        }
        if (!StringUtils.equals(classifier, that.classifier)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = groupID != null ? groupID.hashCode() : 0;
        result = 31 * result + (artifactID != null ? artifactID.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (classifier != null ? classifier.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RawDependency{" +
                "groupID='" + groupID + '\'' +
                ", artifactID='" + artifactID + '\'' +
                ", version='" + version + '\'' +
                ", scope='" + scope + '\'' +
                ", type='" + type + '\'' +
                ", classifier='" + classifier + '\'' +
                '}';
    }
}
