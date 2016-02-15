package com.sourcegraph.javagraph;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.SourceVersion;

/**
 * Emits package names while traversing expression tree
 */
public abstract class PackageNameScanner extends TreePathScanner<Void, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PackageNameScanner.class);

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getIdentifier())) {
            writePackageName(node.toString(), node.getIdentifier().toString(), node);
        }
        return null;
    }

    /**
     * Emits package name
     * @param qualName fully qualified package name
     * @param simpleName simple package name
     * @param node current node in expression tree which produces package name
     */
    public abstract void writePackageName(String qualName, String simpleName,
                                          Tree node);
}
