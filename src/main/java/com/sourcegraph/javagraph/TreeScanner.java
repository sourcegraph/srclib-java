package com.sourcegraph.javagraph;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeScanner extends TreePathScanner<Void, Void> {
    private final Trees trees;
    private final GraphWriter emit;
    private final SourcePositions srcPos;
    // We sometimes emit symbols or refs multiple times because Spans will
    // output a ref that we've already visited normally. I don't know why we
    // emit duplicate symbols.
    private final Set<Symbol.Key> seenSymbols = new HashSet<>();
    private final Set<Ref> seenRefs = new HashSet<>();
    private Spans spans;
    private CompilationUnitTree compilationUnit;

    public TreeScanner(GraphWriter emit, Trees trees) {
        this.emit = emit;
        this.srcPos = trees.getSourcePositions();
        this.trees = trees;
    }

    public void emitRef(int[] span) {
        if (span == null) {
            error("Ref span is null");
            return;
        }
        Symbol.Key symbol = currentSymbolKey();
        if (symbol == null) {
            error("Ref SymbolKey is null");
            return;
        }
        emitRef(span, symbol);
    }

    public void emitRef(int[] span, Symbol.Key symbol) {
        Ref r = new Ref(symbol, compilationUnit.getSourceFile().getName(),
                span[0], span[1]);
        if (seenRefs.contains(r))
            return;
        seenRefs.add(r);
        try {
            emit.writeRef(r);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void emitSymbol(Tree node, int[] nameSpan, List<String> modifiers) {
        int[] defSpan = treeSpan(node);
        emitSymbol(nameSpan, defSpan, modifiers);
    }

    public void emitSymbol(int[] nameSpan, int[] defSpan, List<String> modifiers) {
        Symbol s = new Symbol();
        s.key = currentSymbolKey();
        if (s.key == null) {
            error("Symbol Key is null");
            return;
        }

        if (seenSymbols.contains(s.key))
            return;
        seenSymbols.add(s.key);

        s.name = currentElement().getSimpleName().toString();
        s.kind = currentElement().getKind().toString();
        if (nameSpan != null) {
            s.identStart = nameSpan[0];
            s.identEnd = nameSpan[1];
        }
        s.defStart = defSpan[0];
        s.defEnd = defSpan[1];
        s.file = compilationUnit.getSourceFile().getName();
        s.pkg = compilationUnit.getPackageName().toString();
        s.typeExpr = currentTypeMirror().toString();
        s.modifiers = modifiers;
        s.doc = trees.getDocComment(getCurrentPath());

        try {
            emit.writeSymbol(s);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void error(String message) {
        Tree node = getCurrentPath().getLeaf();
        System.err.println("Error: " + message + " [node " + node.getKind()
                + "]");
    }

    private Symbol.Key currentSymbolKey() {
        Element cur = currentElement();
        if (cur == null) {
            error("currentElement is null");
            return null;
        }

        ElementPath path = ElementPath.get(cur);
        if (path == null) {
            error("path is null");
            return null;
        }

        Symbol.Key key = new Symbol.Key("", path.toString());

        JavaFileObject f = Origins.forElement(cur);
        if (f != null) {
            key.origin = f.toUri().toString();
        }

        return key;
    }

    private Element currentElement() {
        return trees.getElement(getCurrentPath());
    }

    private TypeMirror currentTypeMirror() {
        return trees.getTypeMirror(getCurrentPath());
    }

    @Override
    public Void scan(TreePath root, Void p) {
        this.compilationUnit = root.getCompilationUnit();
        this.spans = new Spans(this.compilationUnit, this.trees);
        return super.scan(root, p);
    }

    @Override
    public Void visitClass(ClassTree node, Void p) {
        int[] nameSpan = {0, 0};
        if (!node.getSimpleName().toString().isEmpty()) {
            nameSpan = spans.name(node);
            emitRef(nameSpan);
        }
        emitSymbol(node, nameSpan, modifiersList(node.getModifiers()));
        super.visitClass(node, p);
        return null;
    }

    @Override
    public Void visitMethod(MethodTree node, Void p) {
        boolean isSynthetic = srcPos.getEndPosition(compilationUnit, node) == -1;
        boolean isCtor = TreeInfo.isConstructor((JCTree) node);
        int[] nameSpan, defSpan;
        if (isCtor) {
            if (isSynthetic) {
                ClassTree klass = (ClassTree) trees.getPath(
                        currentElement().getEnclosingElement()).getLeaf();
                if (klass.getSimpleName().toString().isEmpty()) {
                    // TODO(sqs): why is there an anonymous synthetic node? what
                    // does that even mean?
                    return null;
                }

                defSpan = nameSpan = spans.name(klass);

            } else {
                nameSpan = spans.name(currentElement().getEnclosingElement()
                        .getSimpleName().toString(), node);
                defSpan = treeSpan(node);
            }
        } else {
            nameSpan = spans.name(node);
            defSpan = treeSpan(node);
        }
        emitSymbol(nameSpan, defSpan, modifiersList(node.getModifiers()));
        if (!isSynthetic) {
            emitRef(nameSpan);
        }
        super.visitMethod(node, p);
        return null;
    }

    @Override
    public Void visitVariable(VariableTree node, Void p) {
        int[] nameSpan = spans.name(node);
        emitSymbol(node, nameSpan, modifiersList(node.getModifiers()));
        emitRef(nameSpan);
        super.visitVariable(node, p);
        return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getName())) {
            emitRef(treeSpan(node));
        }
        super.visitIdentifier(node, p);
        return null;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
        scanPackageName(node.getPackageName());

        for (Tree t : node.getImports()) {
            scan(t, p);
        }
        for (Tree t : node.getTypeDecls()) {
            scan(t, p);
        }
        return null;
    }

    public void scanPackageName(Tree node) {
        TreePath pkgName = new TreePath(getCurrentPath(), node);
        new PackageNameScanner() {
            @Override
            public void writePackageName(String qualName, String simpleName,
                                         Tree node) {
                emitRef(spans.name(simpleName, node), new Symbol.Key("",
                        qualName));
            }
        }.scan(pkgName, null);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getIdentifier())) {
            try {
                emitRef(spans.name(node));
            } catch (Spans.SpanException e) {
                System.err.println("SpanException: " + e.getMessage());
            }
        }
        super.visitMemberSelect(node, p);
        return null;
    }

    private int[] treeSpan(Tree node) {
        int[] span = new int[]{
                (int) srcPos.getStartPosition(compilationUnit, node),
                (int) srcPos.getEndPosition(compilationUnit, node)};
        if (span[1] == -1)
            return null;
        return span;
    }

    private List<String> modifiersList(ModifiersTree node) {
        List<String> mods = new ArrayList<>();
        for (Modifier m : node.getFlags()) {
            mods.add(m.toString());
        }
        return mods;
    }
}
