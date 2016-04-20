package com.sourcegraph.javagraph;

import com.sun.source.tree.*;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeInfo;
import org.apache.commons.lang3.StringUtils;
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
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans expression tree and emits references and definitions
 */
class TreeScanner extends TreePathScanner<Void, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeScanner.class);

    /**
     * Special definition key for java.lang.Object
     * When working with classes implicitly inherited from java.lang.Object we need to emit refs to Object
     * that belongs to JDK repo
     */
    private static DefKey JAVA_LANG_OBJECT_DEF;

    static {
        try {
            // we using fake URL that matches real JDK's one
            JAVA_LANG_OBJECT_DEF = new DefKey(new URI("jar:file:/jre/lib/rt.jar"), "java.lang.Object:type");
        } catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

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
     * Keeps stack of visited clases
     */
    private Stack<ClassDef> classDef = new Stack<ClassDef>();

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
     * @return def emitted
     */
    private Def emitDef(Tree node, int[] nameSpan, List<String> modifiers) {
        int[] defSpan = treeSpan(node);
        return emitDef(nameSpan, defSpan, modifiers);
    }

    /**
     * Emits definition
     * @param nameSpan name span
     * @param defSpan definition span
     * @param modifiers definition modifiers (for example, public static final)
     * @return def emitted
     */
    private Def emitDef(int[] nameSpan, int[] defSpan, List<String> modifiers) {
        Def s = new Def(unit.Name, unit.Type);
        s.defKey = currentDefKey();
        if (s.defKey == null) {
            error("def defKey is null");
            return null;
        }

        if (seenDefs.contains(s.defKey))
            return null;
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
        return s;
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
        Def def = emitDef(node, nameSpan, modifiersList(node.getModifiers()));
        DefKey parentDef = extractParentDef(node);
        ClassDef classDef = new ClassDef();
        if (def != null) {
            classDef.def = def.defKey;
        }
        classDef.parentDef = parentDef;
        this.classDef.push(classDef);
        super.visitClass(node, p);
        this.classDef.pop();
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
        CharSequence name = node.getName();
        if (SourceVersion.isIdentifier(name)) {
            if (isThis(name)) {
                ClassDef currentClassDef = classDef.empty() ? null : classDef.peek();
                if (currentClassDef != null) {
                    int span[] = treeSpan(node);
                    if (span != null && currentClassDef.def != null) {
                        emitRef(span, currentClassDef.def, false);
                    }
                }
            } else if (isSuper(name)) {
                ClassDef currentClassDef = classDef.empty() ? null : classDef.peek();
                if (currentClassDef != null && currentClassDef.parentDef != null) {
                    int span[] = treeSpan(node);
                    if (span != null) {
                        emitRef(span, currentClassDef.parentDef, false);
                    }
                }
            } else if (!isClass(name)) {
                int span[] = treeSpan(node);
                if (span != null) {
                    emitRef(span, false);
                }
            }
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
                emitRef(spans.name(simpleName, node), new DefKey(defOrigin, getPath(node)), false);
            }

            /**
             * Constructs path to given MST. If MST component denotes class name, ":type" is added to path component
             * to distinguish them from packages.
             * @param node MST
             * @return path to given MST in form foo.bar.baz:type
             */
            private String getPath(Tree node) {
                if (node == null) {
                    return StringUtils.EMPTY;
                }
                if (!(node instanceof JCTree.JCFieldAccess)) {
                    return node.toString();
                }
                JCTree.JCFieldAccess jcFieldAccess = (JCTree.JCFieldAccess) node;
                if (jcFieldAccess.type instanceof Type.ClassType) {
                    StringBuilder path = new StringBuilder();
                    path.append(getPath(jcFieldAccess.selected));
                    if (path.length() > 0) {
                        path.append('.');
                    }
                    path.append(jcFieldAccess.getIdentifier()).append(":type");
                    return path.toString();
                }
                return node.toString();
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
        CharSequence name = node.getIdentifier();
        if (SourceVersion.isIdentifier(name)) {
            if (srcPos.getEndPosition(compilationUnit, node) != Diagnostic.NOPOS) {
                // TODO (alexsaveliev) otherwise fails on the following block (@result)
                    /*
                            callback = (result,processorId)->{
                                outputQueue.put(result.id, result.item);
                                idleProcessors.add(processorId);
                            };
                     */
                if (isThis(name)) {
                    // ClassName.this
                    DefKey defKey = extractCallerDef(node);
                    if (defKey != null) {
                        emitRef(spans.name(node), defKey, false);
                    }
                } else if (isSuper(name)) {
                    // ClassName.super
                    DefKey defKey = extractCallerParentDef(node);
                    if (defKey != null) {
                        emitRef(spans.name(node), defKey, false);
                    }
                } else if (!isClass(name)) {
                    emitRef(spans.name(node), false);
                }
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

    /**
     * Checks if given name denotes "class" Java keyword
     * @param name symbol to check
     * @return true if name denotes "class" keyword
     */
    private boolean isClass(CharSequence name) {
        return "class".contentEquals(name);
    }

    /**
     * Checks if given name denotes "this" Java keyword
     * @param name symbol to check
     * @return true if name denotes "this" keyword
     */
    private boolean isThis(CharSequence name) {
        return "this".contentEquals(name);
    }

    /**
     * Checks if given name denotes "super" Java keyword
     * @param name symbol to check
     * @return true if name denotes "super" keyword
     */
    private boolean isSuper(CharSequence name) {
        return "super".contentEquals(name);
    }

    /**
     * Extracts definition key of class's parent. For example, for expression 'class A extends B' extracts B's def key.
     * If class does not extend explicitly any class, extracts java.lang.Object's def key
     * @param node class node (class A extends B)
     * @return extracted def key (B) for a given class node
     */
    private DefKey extractParentDef(ClassTree node) {
        Tree extendsClause = node.getExtendsClause();
        if (extendsClause == null) {
            return JAVA_LANG_OBJECT_DEF;
        }
        TreePath extendsPath = trees.getPath(compilationUnit, extendsClause);
        if (extendsPath == null) {
            return JAVA_LANG_OBJECT_DEF;
        }
        Element extendsElement = trees.getElement(extendsPath);
        if (extendsElement == null) {
            return JAVA_LANG_OBJECT_DEF;
        }
        JavaFileObject f = Origins.forElement(extendsElement);
        URI defOrigin = null;
        if (f != null) {
            defOrigin = f.toUri();
        }
        return new DefKey(defOrigin, extendsElement.toString() + ":type");
    }

    /**
     * Extracts definition key of MST's caller. Assumes that caller always points to type
     * and does not support "foo().bar"
     * @param node MST (foo.bar)
     * @return extracted def key (foo) for a given node
     */
    private DefKey extractCallerDef(MemberSelectTree node) {
        TreePath path = trees.getPath(compilationUnit, node.getExpression());
        if (path == null) {
            return null;
        }
        Element element = trees.getElement(path);
        if (element == null) {
            return null;
        }
        JavaFileObject f = Origins.forElement(element);
        URI defOrigin = null;
        if (f != null) {
            defOrigin = f.toUri();
        }
        return new DefKey(defOrigin, element.toString() + ":type");
    }

    /**
     * Extracts definition key of MST's caller's parent. Assumes that caller always points to type
     * and does not support "foo().bar"
     * @param node MST (for foo.bar)
     * @return extracted def key (parent of foo) for a given node
     */
    private DefKey extractCallerParentDef(MemberSelectTree node) {
        TreePath path = trees.getPath(compilationUnit, node.getExpression());
        if (path == null) {
            return null;
        }
        Element element = trees.getElement(path);
        if (element == null) {
            return null;
        }
        if (!(element instanceof Symbol.ClassSymbol)) {
            return null;
        }
        Symbol.ClassSymbol symbol = (Symbol.ClassSymbol) element;
        Type type = symbol.getSuperclass();
        if (type == null) {
            return null;
        }
        if (!(type.tsym instanceof Symbol.ClassSymbol)) {
            return null;
        }
        JavaFileObject f = Origins.forClass((Symbol.ClassSymbol) type.tsym);
        URI defOrigin = null;
        if (f != null) {
            defOrigin = f.toUri();
        }
        return new DefKey(defOrigin, type.tsym.toString() + ":type");
    }

    /**
     * Class definition, used to keep current class's def and parent class's def
     */
    private static class ClassDef {
        /**
         * Current class's def key
         */
        DefKey def;
        /**
         * Parent class's (if any) def key
         */
        DefKey parentDef;
    }
}
