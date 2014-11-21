package com.sourcegraph.javagraph;

import java.io.IOException;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

public final class Spans {
	private final CompilationUnitTree compilationUnit;
	private final Trees trees;
	private final SourcePositions srcPos;

	public Spans(CompilationUnitTree compilationUnit, Trees trees) {
		this.compilationUnit = compilationUnit;
		this.srcPos = trees.getSourcePositions();
		this.trees = trees;
	}

	public int[] name(ClassTree c) throws SpanException {
		return name(c.getSimpleName().toString(), c);
	}

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

	public int[] name(CompilationUnitTree file) throws SpanException {
		String pkgName = file.getPackageName().toString();
		String rightName = pkgName.substring(pkgName.lastIndexOf('.') + 1);
		return name(rightName, file);
	}

	public int[] name(VariableTree var) throws SpanException {
		return name(var.getName().toString(), var);
	}

	public int[] name(MemberSelectTree mst) throws SpanException {
		// TODO(sqs): specify offset in case the identifier name is repeated in
		// the MemberSelect LHS expression.
		return name(mst.getIdentifier().toString(), mst);
	}

	public int[] name(String name, Tree t) throws SpanException {
		if (!SourceVersion.isIdentifier(name)) {
			throw new SpanException("Name '" + name.toString()
					+ "' is not an identifier");
		}

		String src;
		try {
			src = compilationUnit.getSourceFile().getCharContent(true)
					.toString();
		} catch (IOException e) {
			return null;
		}

		int treeStart = (int) srcPos.getStartPosition(compilationUnit, t);
		int treeEnd = (int) srcPos.getEndPosition(compilationUnit, t);
		if (treeStart == -1)
			throw new SpanException("No treeStart found for " + t.toString());
		if (treeEnd == -1)
			throw new SpanException("No treeEnd found for " + t.toString()
					+ " (name: '" + name + "')" + src.substring(treeStart));

		String treeSrc = src.substring(treeStart, treeEnd);
		int nameStart = treeSrc.indexOf(name);
		if (nameStart == -1)
			throw new SpanException("No nameStart found for " + t.toString());
		return new int[] { treeStart + nameStart,
				treeStart + nameStart + name.length() };
	}

	public static class SpanException extends RuntimeException {
		public SpanException(String message) {
			super(message);
		}

		private static final long serialVersionUID = 1L;
	}
}
