package com.sourcegraph.javagraph;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementKindVisitor8;

import org.apache.commons.lang3.StringUtils;

public class ElementPath {
	private final List<String> components = new ArrayList<>(5);

	public static ElementPath get(Element e) {
		return new Visitor().visit(e, new ElementPath());
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
		@Override
		public ElementPath visitPackage(PackageElement e, ElementPath p) {
			p.unshift(e.getQualifiedName().toString());
			return p;
		}

		@Override
		public ElementPath visitType(TypeElement e, ElementPath p) {
			String name = e.getSimpleName().toString();
			Element enclosing = e.getEnclosingElement();
			// TODO(sqs): handle multiple anonymous names at same level
			if (name.isEmpty())
				name = "anon";

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
			final List<String> result = new ArrayList<String>();
			for (VariableElement ve : e.getParameters()) {
				result.add(ve.asType().toString().replaceAll("\\.", "\\$"));
			}
			return result;
		}
	}
}
