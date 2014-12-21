package com.sourcegraph.javagraph;

import junit.framework.TestCase;
import org.junit.Test;

import javax.tools.JavaFileObject;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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

	URI matchAnyOrigin;

	public void setUp() throws Exception {
		super.setUp();
		matchAnyOrigin = new URI("ANY");
	}

	@Test
	public void testGraph_PackageSymbol_Single() {
		GraphData w = graph("Foo.java", "package foo;");
		assertEquals(1, w.symbols.size());
		assertEquals(1, w.refsTo(new Symbol.Key(null, "foo")).size());
	}

	@Test
	public void testGraph_PackageSymbol_Qualified() {
		GraphData w = graph("Foo.java", "package foo.bar;");
		assertEquals(1, w.symbols.size());
		assertEquals(1, w.refsTo(new Symbol.Key(null, "foo")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(null, "foo.bar")).size());
	}

	@Test
	public void testGraph_PackageRef() {
		GraphData w = graph("Foo.java", "package foo; public class Foo { public java.lang.String s; }");
		assertEquals(1, w.refsTo(new Symbol.Key(null, "java")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(matchAnyOrigin, "java.lang")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(matchAnyOrigin, "java.lang.String:type")).size());
	}

	@Test
	public void testGraph_ImportRef() {
		GraphData w = graph("Foo.java", "package foo; import java.lang.String;");
		assertEquals(1, w.refsTo(new Symbol.Key(null, "java")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(matchAnyOrigin, "java.lang")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(matchAnyOrigin, "java.lang.String:type")).size());
	}

	@Test
	public void testGraph_ImportStarRef() {
		GraphData w = graph("Foo.java", "package foo; import java.lang.*;");
		assertEquals(1, w.refsTo(new Symbol.Key(null, "java")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(matchAnyOrigin, "java.lang")).size());
	}

	@Test
	public void testGraph_ClassSymbol() {
		GraphData w = graph("Bar.java", "package foo;\npublic class Bar {}");
		assertEquals(3, w.symbols.size());
		assertEquals(1, w.refsTo(new Symbol.Key(null, "foo.Bar:type")).size());
	}

	@Test
	public void testGraph_ClassRef() {
		GraphData w = graph("Bar.java", "package foo; public class Bar { private foo.Bar b; }");
		assertEquals(2, w.refsTo(new Symbol.Key(null, "foo.Bar:type")).size());
	}

	@Test
	public void testGraph_ComplexRefs() {
		GraphData w = graph("Bar.java", "package foo; import java.lang.String; public class Bar { public Bar() { }\nstatic class Qux extends Bar { Qux() { super(); } } }");
		assertEquals(1, w.refsTo(new Symbol.Key(null, "foo.Bar:type.Qux:type")).size());
		assertEquals(1, w.refsTo(new Symbol.Key(null, "foo.Bar:type.Qux/:init")).size());
	}

	@Test
	public void testGraph_Duplicate0() {
		// This code snippet was outputting duplicates.
		graph("Bar.java", "package foo; public class Bar { static { new java.security.PrivilegedAction<Object>() { public Object run() { return null; } }; } }");
	}
}
