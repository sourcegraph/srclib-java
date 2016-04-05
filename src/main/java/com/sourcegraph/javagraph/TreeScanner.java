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
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans expression tree and emits references and definitions
 */
class TreeScanner extends TreePathScanner<Void, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeScanner.class);

    private final GraphWriter emit;
    private final SourceUnit unit;

    private final SourcePositions srcPos;
    // We sometimes emit defs or refs multiple times because Spans will
    // output a ref that we've already visited normally. I don't know why we
    // emit duplicate defs.
    private final Set<DefKey> seenDefs = new HashSet<>();
    private final Set<Ref> seenRefs = new HashSet<>();
    private Spans spans;

    CompilationUnitTree compilationUnit;
    final Trees trees;
    Stack<Long> parameterizedPositions = new Stack<>();

    /**
     * Constructs new scanner
     * @param emit graph writer that will process all refs and defs encountered
     * @param trees trees object
     * @param unit current source unit
     */
    TreeScanner(GraphWriter emit, Trees trees, SourceUnit unit) {
        this.emit = emit;
        this.srcPos = trees.getSourcePositions();
        this.trees = trees;
        this.unit = unit;
    }

    /**
     * Emits reference
     * @param span name span
     * @param def true if current ref is a definition as well
     */
    private void emitRef(int[] span, boolean def) {
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

    /**
     * Emits reference
     * @param span name span
     * @param defKey definition key
     * @param def true if current ref is a definition as well
     */
    private void emitRef(int[] span, DefKey defKey, boolean def) {
        Ref r = new Ref(this.unit.Name);
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

    /**
     * Emits definition
     * @param node current node of expression tree
     * @param nameSpan name span
     * @param modifiers definition modifiers (for example, public static final)
     */
    private void emitDef(Tree node, int[] nameSpan, List<String> modifiers) {
        int[] defSpan = treeSpan(node);
        emitDef(nameSpan, defSpan, modifiers);
    }

    /**
     * Emits definition
     * @param nameSpan name span
     * @param defSpan definition span
     * @param modifiers definition modifiers (for example, public static final)
     */
    private void emitDef(int[] nameSpan, int[] defSpan, List<String> modifiers) {
        Def s = new Def(unit.Name);
        s.defKey = currentDefKey();
        if (s.defKey == null) {
            error("def defKey is null");
            return;
        }

        if (seenDefs.contains(s.defKey))
            return;
        seenDefs.add(s.defKey);

        Element current = currentElement();
        s.name = current.getSimpleName().toString();
        s.kind = current.getKind().toString();
        if (nameSpan != null) {
            s.identStart = nameSpan[0];
            s.identEnd = nameSpan[1];
        }
        s.defStart = defSpan[0];
        s.defEnd = defSpan[1];
        s.file = compilationUnit.getSourceFile().getName();
        s.pkg = compilationUnit.getPackageName().toString();
        TypeMirror typeMirror = currentTypeMirror();
        if (typeMirror != null) {
            s.typeExpr = typeMirror.toString();
        }
        s.modifiers = modifiers;
        s.doc = trees.getDocComment(getCurrentPath());

        try {
            emit.writeDef(s);
        } catch (IOException e) {
            LOGGER.warn("I/O error", e);
        }
    }

    private boolean verbose = false;

    /**
     * Reports error
     * @param message error message
     */
    private void error(String message) {
        if (!verbose) return;
        Tree node = getCurrentPath().getLeaf();

        LOGGER.warn("{}:{} {} [node {}]",
                compilationUnit.getSourceFile().getName(),
                srcPos.getStartPosition(compilationUnit, node),
                message,
                node.getKind());
    }

    /**
     * @return current definition key
     */
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

    /**
     * @return current java program element
     */
    private Element currentElement() {
        TreePath curPath = getCurrentPath();
        if (curPath == null) {
            error("currentPath is null");
            return null;
        }
        return trees.getElement(curPath);
    }

    /**
     * @return current type mirror
     */
    private TypeMirror currentTypeMirror() {
        return trees.getTypeMirror(getCurrentPath());
    }

    /**
     * Scans given expression tree path
     * @param root expression tree path to scan
     */
    @Override
    public Void scan(TreePath root, Void p) {
        this.compilationUnit = root.getCompilationUnit();
        this.spans = new Spans(this);
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
        boolean isSynthetic = srcPos.getEndPosition(compilationUnit, node) == Diagnostic.NOPOS;
        boolean isCtor = TreeInfo.isConstructor((JCTree) node);
        int[] nameSpan, defSpan;
        if (isCtor) {
            Element current = currentElement();
            if (isSynthetic) {
                if (current == null) {
                    LOGGER.warn("currentElement() == null (synthetic)");
                    return null;
                }
                if (current.getEnclosingElement() == null) {
                    LOGGER.warn("currentElement().getEnclosingElement() == null (synthetic)");
                    return null;
                }
                if (trees.getPath(current.getEnclosingElement()) == null) {
                    LOGGER.warn("trees.getPath(currentElement().getEnclosingElement()) == null (synthetic)");
                    return null;
                }

                ClassTree klass = (ClassTree) trees.getPath(
                        current.getEnclosingElement()).getLeaf();
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
                if (current == null) {
                    LOGGER.warn("currentElement() == null (non-synthetic)");
                    return null;
                }
                if (current.getEnclosingElement() == null) {
                    LOGGER.warn("currentElement().getEnclosingElement() == null (non-synthetic)");
                    return null;
                }

                nameSpan = spans.name(current.getEnclosingElement()
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

        node.getImports().forEach(this::scanPackageName);
        node.getTypeDecls().forEach(t -> scan(t, p));
        return null;
    }

    private void scanPackageName(Tree node) {
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
    public Void visitParameterizedType(ParameterizedTypeTree node, Void p) {
        if (node instanceof JCTree.JCTypeApply) {
            long pos = ((JCTree.JCTypeApply) node).pos;
            parameterizedPositions.push(pos);
        } else {
            parameterizedPositions.push(srcPos.getStartPosition(compilationUnit, node));
        }
        super.visitParameterizedType(node, p);
        parameterizedPositions.pop();
        return null;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree node, Void p) {
        if (SourceVersion.isIdentifier(node.getIdentifier())) {
            if (srcPos.getEndPosition(compilationUnit, node) != Diagnostic.NOPOS) {
                // TODO (alexsaveliev) otherwise fails on the following block (@result)
                    /*
                            callback = (result,processorId)->{
                                outputQueue.put(result.id, result.item);
                                idleProcessors.add(processorId);
                            };
                     */
                emitRef(spans.name(node), false);
            }
        }
        super.visitMemberSelect(node, p);
        return null;
    }

    /**
     * @param node expression tree node
     * @return node span in current compilation unit
     */
    private int[] treeSpan(Tree node) {
        int[] span = new int[]{
                (int) srcPos.getStartPosition(compilationUnit, node),
                (int) srcPos.getEndPosition(compilationUnit, node)};
        if (span[1] == Diagnostic.NOPOS)
            return null;
        return span;
    }

    /**
     * @param node expression tree
     * @return list of node modifiers as a string (for example, "public", "static", "final"
     */
    private List<String> modifiersList(ModifiersTree node) {
        return node.getFlags().stream().map(Modifier::toString).collect(Collectors.toList());
    }
}
