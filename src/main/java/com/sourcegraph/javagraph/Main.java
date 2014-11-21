package com.sourcegraph.javagraph;

import com.beust.jcommander.*;
import com.jcabi.manifests.Manifests;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

public class Main {
	public static void main(String[] args) throws IOException {
		String version = Manifests.read("Javagraph-Version");
		System.err.println("Using javagraph version '" + version + "'");

		JCommander jc = new JCommander();

		// Add subcommands
		ScanCommand scan = new ScanCommand();
		GraphCommand graph = new GraphCommand();
		DepresolveCommand depresolve = new DepresolveCommand();

		jc.addCommand("scan", scan);
		jc.addCommand("graph", graph);
		jc.addCommand("depresolve", depresolve);

		try {
			jc.parse(args);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}

		switch (jc.getParsedCommand()) {
		case "scan":
			scan.Execute();
			break;
		case "graph":
			graph.Execute();
			break;
		case "depresolve":
			depresolve.Execute();
			break;
		default:
			System.out.println("Unkown command");
			jc.usage();
			System.exit(1);
		}
	}
}
