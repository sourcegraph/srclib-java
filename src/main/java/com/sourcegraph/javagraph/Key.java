package com.sourcegraph.javagraph;


import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Base class for source unit and dependencies
 */
public class Key {

    /**
     * Repo is the URI of the repository containing this source unit, if any.
     * The scanner tool does not need to set this field - it can be left blank,
     * to be filled in by the `srclib` tool.
     * If Repo is empty, it indicates that the repository URI is
     * purposefully omitted and this field should be treated as if it
     * doesn't exist. If Repo is set to the unresolved repo sentinel
     * value, then it indicates that repository is unknown, but this
     * field value can be used.
     */
    public String Repo;

    /**
     * CommitID is the commit ID of the repository containing this
     * source unit, if any. The scanner tool need not fill this in; it
     * should be left blank, to be filled in by the `srclib` tool.
     */
    public String CommitID;

    /**
     * Version is the unresolved source unit version (e.g., "v1.2.3").
     */
    public String Version;

    /**
     * Type is the type of source unit this represents, such as "GoPackage".
     */
    public String Type;

    /**
     * Name is an opaque identifier for this source unit that MUST be unique
     * among all other source units of the same type in the same repository.
     * Two source units of different types in a repository may have the same name.
     * To obtain an identifier for a source unit that is guaranteed to be unique
     * repository-wide, use the ID method.
     */
    public String Name;

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return !(o == null || !(o instanceof Key)) && EqualsBuilder.reflectionEquals(o, this);
    }


}
