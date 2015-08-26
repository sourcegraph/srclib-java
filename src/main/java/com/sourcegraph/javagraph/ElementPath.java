package com.sourcegraph.javagraph;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.*;
import javax.lang.model.util.ElementKindVisitor8;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Path to java program element
 */
public class ElementPath {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElementPath.class);

    private final List<String> components = new ArrayList<>(5);

    /**
     * Traverses tree to produce a path to a given program element
     *
     * @param compilationUnit compilation unit
     * @param trees           trees object
     * @param e               target element
     * @return path to target element
     */
    public static ElementPath get(CompilationUnitTree compilationUnit, Trees trees, Element e) {
        return new Visitor(compilationUnit, trees).visit(e, new ElementPath());
    }

    @Override
    public String toString() {
        return StringUtils.join(components, ".");
    }

    public void unshift(String name) {
        components.add(0, name);
    }

    private static class Visitor extends
            ElementKindVisitor8<ElementPath, ElementPath> {
        private final Trees trees;
        private final CompilationUnitTree compilationUnit;

        public Visitor(CompilationUnitTree compilationUnit, Trees trees) {
            this.trees = trees;
            this.compilationUnit = compilationUnit;
        }

        @Override
        public ElementPath visitPackage(PackageElement e, ElementPath p) {
            String name = e.getQualifiedName().toString();
            if (name.isEmpty()) {
                return null;
            }
            p.unshift(name);
            return p;
        }

        private String getUniqueID(Element e) {
            String name;
            TreePath tp = trees.getPath(e);
            SourcePositions sp = trees.getSourcePositions();
            if (tp != null) {
                String filename = tp.getCompilationUnit().getSourceFile().getName();
                String fileBasename = new File(filename).getName().replace(".java", StringUtils.EMPTY);
                name = "p-" + fileBasename + "-" + sp.getStartPosition(tp.getCompilationUnit(), tp.getLeaf());
            } else {
                return null;
            }
            return name;
        }

        private String getSourcePos(Element e) {
            TreePath tp = trees.getPath(e);
            SourcePositions sp = trees.getSourcePositions();
            if (tp != null) {
                return tp.getCompilationUnit().getSourceFile().getName() + sp.getStartPosition(tp.getCompilationUnit(), tp.getLeaf());
            }
            return compilationUnit.getSourceFile().getName();
        }

        @Override
        public ElementPath visitType(TypeElement e, ElementPath p) {
            String name = e.getSimpleName().toString();
            Element enclosing = e.getEnclosingElement();
            if (name.isEmpty() || name.equals("<any?>")) {
                String uniqID = getUniqueID(e);
                if (uniqID == null) return null;
                name = "anon-" + uniqID;
            }

            // Except for top-level package scope, a type and a variable with
            // the same name may exist in the same scope. We must disambiguate
            // them.
            name += ":type";

            p.unshift(name);
            if (enclosing != null && enclosing.getKind() != ElementKind.OTHER) {
                return visit(enclosing, p);
            } else {
                return p;
            }
        }

        @Override
        public ElementPath visitVariable(VariableElement e, ElementPath p) {
            String name = e.getSimpleName().toString();
            p.unshift(name);
            return visit(e.getEnclosingElement(), p);
        }

        @Override
        public ElementPath visitUnknown(Element e, ElementPath p) {

            LOGGER.warn("Element visitor: unknown element {} of type {} at {} while processing [{}]",
                    e.getSimpleName(),
                    e.getKind(),
                    getSourcePos(e),
                    p.toString());
            String name = e.getSimpleName().toString();
            if (name.isEmpty()) {
                name = "u-" + getUniqueID(e);
            }
            p.unshift(name);
            return visit(e.getEnclosingElement(), p);
        }

        @Override
        public ElementPath visitExecutableAsMethod(ExecutableElement e,
                                                   ElementPath p) {
            String methodName = e.getSimpleName().toString();
            final List<String> params = getParameters(e);
            String name = methodName;
            if (!params.isEmpty())
                name += ":" + StringUtils.join(params, ":");
            p.unshift(name);
            return visit(e.getEnclosingElement(), p);
        }

        @Override
        public ElementPath visitExecutableAsConstructor(ExecutableElement e,
                                                        ElementPath p) {
            final List<String> params = getParameters(e);
            String name = e.getEnclosingElement().getSimpleName().toString()
                    + "/:init";
            if (!params.isEmpty())
                name += ":" + StringUtils.join(params, ":");
            p.unshift(name);
            return visit(e.getEnclosingElement().getEnclosingElement(), p);
        }

        private List<String> getParameters(ExecutableElement e) {
            return e.getParameters().
                    stream().
                    map(ve -> ve.asType().toString().replaceAll("\\.", "\\$")).
                    collect(Collectors.toList());
        }
    }
}
