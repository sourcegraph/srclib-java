package com.sourcegraph.javagraph;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.SourceVersion;

public abstract class PackageNameScanner extends TreePathScanner<Void, Void> {
    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getIdentifier())) {
            try {
                writePackageName(node.toString(), node.getIdentifier().toString(), node);
            } catch (Spans.SpanException e) {
                System.err.println("SpanException: " + e.getMessage());
            }
        }
        return null;
    }

    public abstract void writePackageName(String qualName, String simpleName,
                                          Tree node);
}
