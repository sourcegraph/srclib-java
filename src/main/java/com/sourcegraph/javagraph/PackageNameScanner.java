package com.sourcegraph.javagraph;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.SourceVersion;

public abstract class PackageNameScanner extends TreePathScanner<Void, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PackageNameScanner.class);

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getIdentifier())) {
            try {
                writePackageName(node.toString(), node.getIdentifier().toString(), node);
            } catch (Spans.SpanException e) {
                LOGGER.warn("Span exception", e);
            }
        }
        return null;
    }

    public abstract void writePackageName(String qualName, String simpleName,
                                          Tree node);
}
