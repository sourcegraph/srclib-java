package com.sourcegraph.javagraph;

/**
 * A Raw, unresolved Maven Dependency.
 */
public class RawDependency {

    String groupID;
    String artifactID;
    String version;
    String scope;

    public RawDependency(String groupID, String artifactID, String version, String scope) {
        this.groupID = groupID;
        this.artifactID = artifactID;
        this.version = version;
        this.scope = scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RawDependency that = (RawDependency) o;

        if (artifactID != null ? !artifactID.equals(that.artifactID) : that.artifactID != null) return false;
        if (groupID != null ? !groupID.equals(that.groupID) : that.groupID != null) return false;
        if (scope != null ? !scope.equals(that.scope) : that.scope != null) return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = groupID != null ? groupID.hashCode() : 0;
        result = 31 * result + (artifactID != null ? artifactID.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (scope != null ? scope.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "RawDependency{" +
                "groupID='" + groupID + '\'' +
                ", artifactID='" + artifactID + '\'' +
                ", version='" + version + '\'' +
                ", scope='" + scope + '\'' +
                '}';
    }
}
