package com.sourcegraph.javagraph;

import java.net.URI;

import javax.tools.SimpleJavaFileObject;

public class StringJavaFileObject extends SimpleJavaFileObject {
	final String javaSource;

	StringJavaFileObject(String name, String javaSource) {
		super(URI.create("string:///" + name), Kind.SOURCE);
		this.javaSource = javaSource;
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors) {
		return javaSource;
	}
}
