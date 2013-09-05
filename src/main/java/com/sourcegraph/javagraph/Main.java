package com.sourcegraph.javagraph;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class Main {

	public static void main(String[] args) throws IOException {
		String classpath = args[0];
		String sourcepath = args[1];
		String[] filePaths = Arrays.copyOfRange(args, 2, args.length);

		PrintWriter w = new PrintWriter(System.out);
		GraphWriter emit = new GraphPrinter(w);

		Grapher g = new Grapher(classpath, sourcepath, emit);
		g.graph(filePaths);
		g.close();
	}
}
