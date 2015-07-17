package com.sourcegraph.javagraph;

import junit.framework.TestCase;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class TestGrapher extends TestCase {
	private GraphData graph(String name, String javaSource) {
		GraphData w = new GraphData();
		Grapher g = new Grapher(StringUtils.EMPTY, StringUtils.EMPTY, Project.DEFAULT_SOURCE_CODE_VERSION, w);
		List<JavaFileObject> files = new ArrayList<>();
		files.add(new StringJavaFileObject(name, javaSource));
		try {
			g.graphJavaFiles(files);
		} catch (IOException e) {
			fail("IOException in graphJavaFiles: " + name);
		}
		return w;
	}

	URI matchAnyOrigin;

	public void setUp() throws Exception {
		super.setUp();
		matchAnyOrigin = new URI("ANY");
	}

	@Test
	public void testGraph_PackageSymbol_Single() {
		GraphData w = graph("Foo.java", "package foo;");
		assertEquals(1, w.defs.size());
		assertEquals(1, w.refsTo(new DefKey(null, "foo")).size());
	}

	@Test
	public void testGraph_PackageSymbol_Qualified() {
		GraphData w = graph("Foo.java", "package foo.bar;");
		assertEquals(1, w.defs.size());
		assertEquals(1, w.refsTo(new DefKey(null, "foo")).size());
		assertEquals(1, w.refsTo(new DefKey(null, "foo.bar")).size());
	}

	@Test
	public void testGraph_PackageRef() {
		GraphData w = graph("Foo.java", "package foo; public class Foo { public java.lang.String s; }");
		assertEquals(1, w.refsTo(new DefKey(null, "java")).size());
		assertEquals(1, w.refsTo(new DefKey(matchAnyOrigin, "java.lang")).size());
		assertEquals(1, w.refsTo(new DefKey(matchAnyOrigin, "java.lang.String:type")).size());
	}

	@Test
	public void testGraph_ImportRef() {
		GraphData w = graph("Foo.java", "package foo; import java.lang.String;");
		assertEquals(1, w.refsTo(new DefKey(null, "java")).size());
		assertEquals(1, w.refsTo(new DefKey(matchAnyOrigin, "java.lang")).size());
		assertEquals(1, w.refsTo(new DefKey(matchAnyOrigin, "java.lang.String:type")).size());
	}

	@Test
	public void testGraph_ImportStarRef() {
		GraphData w = graph("Foo.java", "package foo; import java.lang.*;");
		assertEquals(1, w.refsTo(new DefKey(null, "java")).size());
		assertEquals(1, w.refsTo(new DefKey(matchAnyOrigin, "java.lang")).size());
	}

	@Test
	public void testGraph_ClassSymbol() {
		GraphData w = graph("Bar.java", "package foo;\npublic class Bar {}");
		assertEquals(3, w.defs.size());
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type")).size());
	}

	@Test
	public void testGraph_ClassRef() {
		GraphData w = graph("Bar.java", "package foo; public class Bar { private foo.Bar b; }");
		assertEquals(2, w.refsTo(new DefKey(null, "foo.Bar:type")).size());
	}

	@Test
	public void testGraph_ComplexRefs() {
		GraphData w = graph("Bar.java", "package foo; import java.lang.String; public class Bar { public Bar() { }\nstatic class Qux extends Bar { Qux() { super(); } } }");
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type.Qux:type")).size());
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type.Qux/:init")).size());
	}

	@Test
	public void testGraph_Duplicate0() {
		// This code snippet was outputting duplicates.
		graph("Bar.java", "package foo; public class Bar { static { new java.security.PrivilegedAction<Object>() { public Object run() { return null; } }; } }");
	}

	@Test
	public void testGraph_AnonClassInstance() {
		// Test that anon class instances are given synthesized names.
		GraphData w = graph("Bar.java", "package foo; public class Bar { interface I { public void F(); }; private I foo() { return new I() { @Override public void F() { F(); } }; } }");
		//System.err.println("======== Refs:\n" + StringUtils.join(w.refs, "\n") + "\n============\n");
		assertEquals(2, w.refsTo(new DefKey(null, "foo.Bar:type.foo.anon-p-Bar-99:type.F")).size());
		assertNotNull(w.getSymbolFromKey(new DefKey(null, "foo.Bar:type.foo.anon-p-Bar-99:type.F")));
	}


	// Test that def paths in unresolved blocks (that have compile errors, etc.) are still constructed.

	@Test
	public void testGraph_UnresolvedRef_ReturnType() {
		// When a method has a return type that can't be resolved.
		GraphData w = graph("Bar.java", "package foo; public class Bar { private invalid.pkg.name.MyType foo(String x) { foo(x); return null; } }");
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type")).size());
		assertEquals(2, w.refsTo(new DefKey(null, "foo.Bar:type.foo:java$lang$String")).size());
		assertEquals(2, w.refsTo(new DefKey(null, "foo.Bar:type.foo:java$lang$String.x")).size());
	}

	@Test
	public void testGraph_UnresolvedRef_ParamType() {
		// When a method has a parameter type that can't be resolved.
		GraphData w = graph("Bar.java", "package foo; public class Bar { private void foo(invalid.pkg.name.MyType x) { foo(x); } }");
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type")).size());
		assertEquals(2, w.refsTo(new DefKey(null, "foo.Bar:type.foo:invalid$pkg$name$MyType")).size());
		assertEquals(2, w.refsTo(new DefKey(null, "foo.Bar:type.foo:invalid$pkg$name$MyType.x")).size());
	}

	@Test
	public void testGraph_UnresolvedRef_AnonClassInstance() {
		// When a method has a parameter type that can't be resolved.
		GraphData w = graph("Bar.java", "package foo; public class Bar { private void foo() {  new invalid.pkg.name.MyType() { @Override public void bar() { bar(); } }; } }");
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type")).size());
		assertEquals(1, w.refsTo(new DefKey(null, "foo.Bar:type.foo")).size());
	}
}
