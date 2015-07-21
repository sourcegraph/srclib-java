package com.sourcegraph.javagraph;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreeScanner extends TreePathScanner<Void, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeScanner.class);

    private final Trees trees;
    private final GraphWriter emit;
    private final SourcePositions srcPos;
    // We sometimes emit defs or refs multiple times because Spans will
    // output a ref that we've already visited normally. I don't know why we
    // emit duplicate defs.
    private final Set<DefKey> seenDefs = new HashSet<>();
    private final Set<Ref> seenRefs = new HashSet<>();
    private Spans spans;
    private CompilationUnitTree compilationUnit;

    public TreeScanner(GraphWriter emit, Trees trees) {
        this.emit = emit;
        this.srcPos = trees.getSourcePositions();
        this.trees = trees;
    }

    public void emitRef(int[] span, boolean def) {
        if (span == null) {
            error("Ref span is null");
            return;
        }
        DefKey defKey = currentDefKey();
        if (defKey == null) {
            error("Ref DefKey is null");
            return;
        }
        emitRef(span, defKey, def);
    }

    public void emitRef(int[] span, DefKey defKey, boolean def) {
        Ref r = new Ref();
        r.defKey = defKey;
        r.file = compilationUnit.getSourceFile().getName();
        r.start = span[0];
        r.end = span[1];
        r.def = def;

        if (seenRefs.contains(r))
            return;
        seenRefs.add(r);
        try {
            emit.writeRef(r);
        } catch (IOException e) {
            LOGGER.warn("I/O error", e);
        }
    }

    public void emitDef(Tree node, int[] nameSpan, List<String> modifiers) {
        int[] defSpan = treeSpan(node);
        emitDef(nameSpan, defSpan, modifiers);
    }

    public void emitDef(int[] nameSpan, int[] defSpan, List<String> modifiers) {
        Def s = new Def();
        s.defKey = currentDefKey();
        if (s.defKey == null) {
            error("def defKey is null");
            return;
        }

        if (seenDefs.contains(s.defKey))
            return;
        seenDefs.add(s.defKey);

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
            emit.writeDef(s);
        } catch (IOException e) {
            LOGGER.warn("I/O error", e);
        }
    }

    public boolean verbose = false;

    private void error(String message) {
        if (!verbose) return;
        Tree node = getCurrentPath().getLeaf();

        LOGGER.warn("{}:{} {} [node {}]",
                compilationUnit.getSourceFile().getName(),
                srcPos.getStartPosition(compilationUnit, node),
                message,
                node.getKind());
    }

    private DefKey currentDefKey() {
        Element cur = currentElement();
        if (cur == null) {
            error("currentElement is null, currentPath is " + getCurrentPath().toString());
            return null;
        }

        ElementPath path = ElementPath.get(compilationUnit, trees, cur);
        if (path == null) {
            error("path is null");
            return null;
        }

        URI defOrigin = null;
        JavaFileObject f = Origins.forElement(cur);
        if (f != null) {
            defOrigin = f.toUri();
        }

        return new DefKey(defOrigin, path.toString());
    }

    private Element currentElement() {
        TreePath curPath = getCurrentPath();
        if (curPath == null) {
            error("currentPath is null");
            return null;
        }
        return trees.getElement(curPath);
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
            emitRef(nameSpan, true);
        }
        emitDef(node, nameSpan, modifiersList(node.getModifiers()));
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
                if (currentElement() == null) {
                    LOGGER.warn("currentElement() == null (synthetic)");
                    return null;
                }
                if (currentElement().getEnclosingElement() == null) {
                    LOGGER.warn("currentElement().getEnclosingElement() == null (synthetic)");
                    return null;
                }
                if (trees.getPath(currentElement().getEnclosingElement()) == null) {
                    LOGGER.warn("trees.getPath(currentElement().getEnclosingElement()) == null (synthetic)");
                    return null;
                }

                ClassTree klass = (ClassTree) trees.getPath(
                        currentElement().getEnclosingElement()).getLeaf();
                if (klass.getSimpleName().toString().isEmpty()) {
                    // TODO(sqs): why is there an anonymous synthetic node? what
                    // does that even mean?
                    return null;
                }

                defSpan = nameSpan = spans.name(klass);

            } else {
                if (spans == null) {
                    LOGGER.warn("spans == null (non-synthetic)");
                    return null;
                }
                if (currentElement() == null) {
                    LOGGER.warn("currentElement() == null (non-synthetic)");
                    return null;
                }
                if (currentElement().getEnclosingElement() == null) {
                    LOGGER.warn("currentElement().getEnclosingElement() == null (non-synthetic)");
                    return null;
                }

                nameSpan = spans.name(currentElement().getEnclosingElement()
                        .getSimpleName().toString(), node);
                defSpan = treeSpan(node);
            }
        } else {
            nameSpan = spans.name(node);
            defSpan = treeSpan(node);
        }
        emitDef(nameSpan, defSpan, modifiersList(node.getModifiers()));
        if (!isSynthetic) {
            emitRef(nameSpan, true);
        }
        super.visitMethod(node, p);
        return null;
    }

    @Override
    public Void visitVariable(VariableTree node, Void p) {
        int[] nameSpan = spans.name(node);
        emitDef(node, nameSpan, modifiersList(node.getModifiers()));
        emitRef(nameSpan, true);
        super.visitVariable(node, p);
        return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getName())) {
            emitRef(treeSpan(node), false);
        }
        super.visitIdentifier(node, p);
        return null;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree node, Void p) {
        scanPackageName(node.getPackageName());

        for (ImportTree t : node.getImports()) {
            scanPackageName(t);
        }
        for (Tree t : node.getTypeDecls()) {
            scan(t, p);
        }
        return null;
    }

    public void scanPackageName(Tree node) {
        if (getCurrentPath() == null) {
            LOGGER.warn("Current path is null");
            return;
        }
        if (node == null) {
            // no package
            return;
        }

        TreePath pkgName = new TreePath(getCurrentPath(), node);
        new PackageNameScanner() {
            @Override
            public void writePackageName(String qualName, String simpleName,
                                         Tree node) {
// TODO(sqs): set origin to the JAR this likely came from (it's hard because it could be from multiple JARs)
                TreePath p = getCurrentPath();
                if (p == null) return;
                Element e = trees.getElement(p);
                if (e == null) return;
                JavaFileObject f = Origins.forElement(e);
                URI defOrigin = null;
                if (f != null) {
                    defOrigin = f.toUri();
                }
                emitRef(spans.name(simpleName, node), new DefKey(defOrigin, qualName + ":type"), false);
            }
        }.scan(pkgName, null);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getIdentifier())) {
            try {
                emitRef(spans.name(node), false);
            } catch (Spans.SpanException e) {
                LOGGER.warn("Span exception", e);
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
