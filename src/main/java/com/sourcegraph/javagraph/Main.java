package com.sourcegraph.javagraph;

import com.beust.jcommander.JCommander;
import com.jcabi.manifests.Manifests;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        String version = Manifests.read("Javagraph-Version");
        System.err.println("Using srclib-java version '" + version + "'");

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
            System.exit(1);
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
                System.out.println("Unknown command");
                jc.usage();
                System.exit(1);
        }
    }
}
