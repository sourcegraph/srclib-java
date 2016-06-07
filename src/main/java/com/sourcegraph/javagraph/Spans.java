package com.sourcegraph.javagraph;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

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
        return name(c.getSimpleName().toString(), c, 0);
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

        int offset;
        if (e.getKind() == ElementKind.CONSTRUCTOR) {
            Element klass = e.getEnclosingElement();
            if (klass == null || !klass.getKind().isClass()) {
                return null;
            }
            name = klass.getSimpleName().toString();
            offset = 0;
        } else {
            name = method.getName().toString();
            offset = (int) srcPos.getEndPosition(compilationUnit, method.getReturnType());
        }
        return name(name, method, offset);
    }

    /**
     * @param file compilation unit node
     * @return name span of compilation unit node in current compilation unit
     */
    public int[] name(CompilationUnitTree file) {
        String pkgName = file.getPackageName().toString();
        String rightName = pkgName.substring(pkgName.lastIndexOf('.') + 1);
        return name(rightName, file, 0);
    }

    /**
     * @param var variable node
     * @return name span of variable node in current compilation unit
     */
    public int[] name(VariableTree var) {
        // alexsaveliev: there may be the following caveats
        // Foobar bar
        // Foo bar = Foobar()
        // regular search for "bar" in the tree won't work

        if (src == null) {
            return null;
        }

        int treeStart = (int) srcPos.getStartPosition(compilationUnit, var);
        int treeEnd = (int) srcPos.getEndPosition(compilationUnit, var);
        if (treeStart == -1 || treeEnd == -1) {
            return null;
        }

        String treeSrc = src.substring(treeStart, treeEnd);
        // cut type prefix
        String name = var.getName().toString();
        String type = var.getType().toString();
        int pos = treeSrc.indexOf(type);
        if (pos < 0) {
            // fallback
            return name(name, var, 0);
        }
        pos = treeSrc.indexOf(name, pos + type.length());
        if (pos < 0) {
            // fallback
            return name(name, var, 0);
        }
        return new int[] {treeStart + pos, treeStart + pos + name.length()};
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

        int treeStart = (int) srcPos.getEndPosition(compilationUnit, mst.getExpression());
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
     * @param offset optional offset to start looking at.
     *               By default we are looking for a name span in [start(t), end(t)], however if offset is not 0,
     *               then lookup will be performed in [offset, end(t)]. This can be used to ensure we don't taking
     *               into account chunk of code which may cause conflict (voiD D)
     * @return name span of a given name inside span defined by given tree node
     */
    public int[] name(String name, Tree t, int offset) {

        if (src == null) {
            return null;
        }

        int treeStart = offset == 0 ? (int) srcPos.getStartPosition(compilationUnit, t) : offset;
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
