package com.sourcegraph.javagraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.tools.JavaFileObject;

import junit.framework.TestCase;

import org.junit.Test;

public class TestGrapher extends TestCase {
	private GraphData graph(String name, String javaSource) {
		GraphData w = new GraphData();
		Grapher g = new Grapher("", "", w);
		List<JavaFileObject> files = new ArrayList<>();
		files.add(new StringJavaFileObject(name, javaSource));
		try {
			g.graph(files);
		} catch (IOException e) {
			fail("IOException in graph: " + name);
		}
		return w;
	}

	@SuppressWarnings("unused")
	private void printGraph(GraphData w) {
		try {
			w.printRefs();
			w.printSymbols();
		} catch (IOException e) {
			fail("IOException in printGraph");
		}
	}

	@Test
	public void testGraph_PackageSymbol_Single() {
		GraphData w = graph("Foo.java", "package foo;");
		assertEquals(1, w.symbols.size());
		assertEquals(1, w.refsTo(new Symbol.Key("", "foo")).size());
	}

	@Test
	public void testGraph_PackageSymbol_Qualified() {
		GraphData w = graph("Foo.java", "package foo.bar;");
		assertEquals(1, w.symbols.size());
		assertEquals(1, w.refsTo(new Symbol.Key("", "foo")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("", "foo.bar")).size());
	}

	@Test
	public void testGraph_PackageRef() {
		GraphData w = graph("Foo.java", "package foo; public class Foo { public java.lang.String s; }");
		assertEquals(1, w.refsTo(new Symbol.Key("", "java")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("*", "java.lang")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("*", "java.lang.String:type")).size());
	}

	@Test
	public void testGraph_ImportRef() {
		GraphData w = graph("Foo.java", "package foo; import java.lang.String;");
		assertEquals(1, w.refsTo(new Symbol.Key("", "java")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("*", "java.lang")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("*", "java.lang.String:type")).size());
	}

	@Test
	public void testGraph_ImportStarRef() {
		GraphData w = graph("Foo.java", "package foo; import java.lang.*;");
		assertEquals(1, w.refsTo(new Symbol.Key("", "java")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("*", "java.lang")).size());
	}

	@Test
	public void testGraph_ClassSymbol() {
		GraphData w = graph("Bar.java", "package foo;\npublic class Bar {}");
		assertEquals(3, w.symbols.size());
		assertEquals(1, w.refsTo(new Symbol.Key("", "foo.Bar:type")).size());
	}

	@Test
	public void testGraph_ClassRef() {
		GraphData w = graph("Bar.java", "package foo; public class Bar { private foo.Bar b; }");
		assertEquals(2, w.refsTo(new Symbol.Key("", "foo.Bar:type")).size());
	}

	@Test
	public void testGraph_ComplexRefs() {
		GraphData w = graph("Bar.java", "package foo; import java.lang.String; public class Bar { public Bar() { }\nstatic class Qux extends Bar { Qux() { super(); } } }");
		assertEquals(1, w.refsTo(new Symbol.Key("", "foo.Bar:type.Qux:type")).size());
		assertEquals(1, w.refsTo(new Symbol.Key("", "foo.Bar:type.Qux/:init")).size());
	}

	@Test
	public void testGraph_Duplicate0() {
		// This code snippet was outputting duplicates.
		graph("Bar.java", "package foo; public class Bar { static { new java.security.PrivilegedAction<Object>() { public Object run() { return null; } }; } }");
	}
}
