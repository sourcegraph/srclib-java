package com.sourcegraph.javagraph;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import java.io.IOException;

/**
 * Produces spans (start, end) of expression tree nodes in current compilation unit
 */
public final class Spans {
    private final CompilationUnitTree compilationUnit;
    private final Trees trees;
    private final SourcePositions srcPos;
    private final TreeScanner scanner;

    private CharSequence src;

    /**
     * Constructs new span object
     * @param scanner expression tree scanner
     */
    public Spans(TreeScanner scanner) {
        this.scanner = scanner;
        this.compilationUnit = scanner.compilationUnit;
        this.srcPos = scanner.trees.getSourcePositions();
        this.trees = scanner.trees;

        try {
            src = compilationUnit.getSourceFile().getCharContent(true);
        } catch (IOException e) {
            src = null;
        }


    }

    /**
     * @param c class node
     * @return name span of class node in current compilation unit
     * @throws SpanException
     */
    public int[] name(ClassTree c) throws SpanException {
        return name(c.getSimpleName().toString(), c);
    }

    /**
     * @param method method node
     * @return name span of method node in current compilation unit
     * @throws SpanException
     */
    public int[] name(MethodTree method) throws SpanException {
        String name;

        TreePath path = trees.getPath(compilationUnit, method);
        if (path == null) {
            return null;
        }

        Element e = trees.getElement(path);
        if (e == null) {
            return null;
        }

        if (e.getKind() == ElementKind.CONSTRUCTOR) {
            Element klass = e.getEnclosingElement();
            if (klass == null || !klass.getKind().isClass()) {
                return null;
            }
            name = klass.getSimpleName().toString();
        } else {
            name = method.getName().toString();
        }
        return name(name, method);
    }

    /**
     * @param file compilation unit node
     * @return name span of compilation unit node in current compilation unit
     * @throws SpanException
     */
    public int[] name(CompilationUnitTree file) throws SpanException {
        String pkgName = file.getPackageName().toString();
        String rightName = pkgName.substring(pkgName.lastIndexOf('.') + 1);
        return name(rightName, file);
    }

    /**
     * @param var variable node
     * @return name span of variable node in current compilation unit
     * @throws SpanException
     */
    public int[] name(VariableTree var) throws SpanException {
        return name(var.getName().toString(), var);
    }

    /**
     * @param mst member select node
     * @return name span of member select node in current compilation unit
     * @throws SpanException
     */
    public int[] name(MemberSelectTree mst) throws SpanException {
        // TODO(sqs): specify offset in case the identifier name is repeated in
        // the MemberSelect LHS expression.
        return name(mst.getIdentifier().toString(), mst);
    }

    /**
     * @param name name to produce span for
     * @param t tree node to look for name span
     * @return name span of a given name inside span defined by given tree node
     * @throws SpanException
     */
    public int[] name(String name, Tree t) throws SpanException {

        if (src == null) {
            return null;
        }

        if (!SourceVersion.isIdentifier(name)) {
            throw new SpanException("Name '" + name + "' is not an identifier");
        }

        int treeStart = (int) srcPos.getStartPosition(compilationUnit, t);
        int treeEnd = (int) srcPos.getEndPosition(compilationUnit, t);
        if (treeStart == -1)
            throw new SpanException("No treeStart found for " + t.toString() + " in " + compilationUnit.getSourceFile().getName());
        if (treeEnd == -1)
            throw new SpanException("No treeEnd found for " + t.toString() + " (name: '" + name + "') at " + compilationUnit.getSourceFile().getName() + ":+" + treeStart);

        String treeSrc = src.subSequence(treeStart, treeEnd).toString();
        int nameStart = treeSrc.indexOf(name);
        if (nameStart == -1) {
            // alexsaveliev. the following guava's TypeTokenResolutionTest.java code
            // new Owner<Integer>().new Inner<String>() {}.getOwnerType());
            // gives treeSrc = "<String>() {}"
            // let's try to resolve it using stacked positions
            if (!scanner.parameterizedPositions.isEmpty()) {
                treeStart = scanner.parameterizedPositions.peek().intValue();
                treeSrc = src.subSequence(treeStart, treeEnd).toString();
                nameStart = treeSrc.indexOf(name);
            }
            if (nameStart == -1) {
                throw new SpanException("No nameStart found for " + t.toString() + " at " + compilationUnit.getSourceFile().getName() + ":+" + treeStart + "-" + treeEnd);
            }
        }
        return new int[]{treeStart + nameStart, treeStart + nameStart + name.length()};
    }

    public static class SpanException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SpanException(String message) {
            super(message);
        }
    }
}
