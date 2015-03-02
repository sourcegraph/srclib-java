package com.sourcegraph.javagraph;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;

public abstract class PackageNameScanner extends TreePathScanner<Void, Void> {
    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {
        writePackageName(node.toString(), node.toString(), node);
        super.visitIdentifier(node, p);
        return null;
    }

    public abstract void writePackageName(String qualName, String simpleName,
                                          Tree node);
}
