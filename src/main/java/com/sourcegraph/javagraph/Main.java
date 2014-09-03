package com.sourcegraph.javagraph;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import com.beust.jcommander.*;


public class Main {

	public static void main(String[] args) throws IOException {

		/*
		String classpath = args[0];
		String sourcepath = args[1];
		String[] filePaths = Arrays.copyOfRange(args, 2, args.length);

		PrintWriter w = new PrintWriter(System.out);
		GraphWriter emit = new GraphPrinter(w);

		Grapher g = new Grapher(classpath, sourcepath, emit);
		g.graph(filePaths);
		g.close();*/
		
		JCommander jc = new JCommander();
		
		// Add subcommands
		ScanCommand scan = new ScanCommand();
		jc.addCommand("scan", scan);
		
		GraphCommand graph = new GraphCommand();
		jc.addCommand("graph", graph);
		
		DepresolveCommand depresolve = new DepresolveCommand();
		jc.addCommand("depresolve", depresolve);
		
		try {
			jc.parse(args);
		} catch (Exception e){
			System.out.println(e.getMessage());
		}
		
		switch (jc.getParsedCommand()) {
			case "scan":
				scan.Execute();
				return;
			case "graph":
				graph.Execute();
				return;
			case "depresolve":
				depresolve.Execute();
				return;
			default:
				System.out.println("Unkown command");
		}
		
		jc.usage();
		System.exit(1);
	}
}
