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

    private String src;

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
            src = compilationUnit.getSourceFile().getCharContent(true).toString();
        } catch (IOException e) {
            src = null;
        }


    }

    /**
     * @param c class node
     * @return name span of class node in current compilation unit
     */
    public int[] name(ClassTree c) {
        return name(c.getSimpleName().toString(), c);
    }

    /**
     * @param method method node
     * @return name span of method node in current compilation unit
     */
    public int[] name(MethodTree method) {
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
     */
    public int[] name(CompilationUnitTree file) {
        String pkgName = file.getPackageName().toString();
        String rightName = pkgName.substring(pkgName.lastIndexOf('.') + 1);
        return name(rightName, file);
    }

    /**
     * @param var variable node
     * @return name span of variable node in current compilation unit
     */
    public int[] name(VariableTree var) {
        return name(var.getName().toString(), var);
    }

    /**
     * @param mst member select node
     * @return name span of member select node in current compilation unit
     */
    public int[] name(MemberSelectTree mst) {
        // alexsaveliev: searching after dot to deal with the cases such as "xxFOOxx.FOO"
        // another case to consider "Collections.<Type> emptyList"

        if (src == null) {
            return null;
        }

        int treeStart = (int) srcPos.getStartPosition(compilationUnit, mst);
        int treeEnd = (int) srcPos.getEndPosition(compilationUnit, mst);
        if (treeStart == -1 || treeEnd == -1) {
            return null;
        }

        String treeSrc = src.substring(treeStart, treeEnd);
        int offset = memberSelectOffset(treeSrc);
        if (offset == -1) {
            return null;
        }

        String ident = mst.getIdentifier().toString();
        int pos = treeSrc.indexOf(ident, offset);
        if (pos == -1) {
            return null;
        }
        return new int[]{treeStart + pos, treeStart + pos + ident.length()};

    }

    /**
     * @param name name to produce span for
     * @param t tree node to look for name span
     * @return name span of a given name inside span defined by given tree node
     */
    public int[] name(String name, Tree t) {

        if (src == null) {
            return null;
        }

        int treeStart = (int) srcPos.getStartPosition(compilationUnit, t);
        int treeEnd = (int) srcPos.getEndPosition(compilationUnit, t);
        if (treeStart == -1 || treeEnd == -1) {
            return null;
        }

        String treeSrc = src.substring(treeStart, treeEnd);
        int nameStart = treeSrc.indexOf(name);
        if (nameStart == -1) {
            // alexsaveliev. the following guava's TypeTokenResolutionTest.java code
            // new Owner<Integer>().new Inner<String>() {}.getOwnerType());
            // gives treeSrc = "<String>() {}"
            // let's try to resolve it using stacked positions
            if (!scanner.parameterizedPositions.isEmpty()) {
                treeStart = scanner.parameterizedPositions.peek().intValue();
                treeSrc = src.substring(treeStart, treeEnd);
                nameStart = treeSrc.indexOf(name);
            }
            if (nameStart == -1) {
                return null;
            }
        }
        return new int[]{treeStart + nameStart, treeStart + nameStart + name.length()};
    }

    /**
     * Computers member select start in a given source code
     * @param code source code
     * @return member select start in a given source code or -1. Member select start is
     * the first non-whitespace character's position after dot and angle brackets
     */
    private int memberSelectOffset(String code) {
        int state = 0; // before dot
        int pos = 0;
        int angleBrackets = 0;
        int len = code.length();
        while (pos < len) {
            char c = code.charAt(pos);
            switch (state) {
                case 0:
                    if (c == '.') {
                        state = 1;
                    }
                    break;
                case 1: // after dot
                    if (c == '<') {
                        angleBrackets++;
                    } else if (c == '>') {
                        angleBrackets--;
                    } else if (!Character.isWhitespace(c)) {
                        if (angleBrackets == 0) {
                            return pos;
                        }
                    }
            }
            pos++;
        }
        return -1;
    }
}
