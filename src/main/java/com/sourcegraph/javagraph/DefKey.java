package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;

public class DefKey {
    private final URI origin;
    private final String path;


    public DefKey(URI origin, String path) {
        this.origin = origin;
        this.path = path;
    }

    public String formatPath() {
        return getPath().replace('.', '/').replace('$', '.');
    }

    public String formatTreePath() {
        return formatPath();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefKey defKey = (DefKey) o;

        if (origin != null ? !origin.equals(defKey.origin) : defKey.origin != null) return false;
        if (path != null ? !path.equals(defKey.path) : defKey.path != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = origin != null ? origin.hashCode() : 0;
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }

    /**
     * Origin is the JAR or class file that this def is defined in. It is null for defs defined in the current source unit.
     */
    public URI getOrigin() {
        return origin;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "DefKey{" +
                (origin != null ? "origin=" + origin + ", " : StringUtils.EMPTY) +
                "path='" + path + '\'' +
                '}';
    }
}
