package com.sourcegraph.javagraph;

import com.beust.jcommander.JCommander;
import com.jcabi.manifests.Manifests;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;

public class Main {

    private static final String VERSION_ENTRY = "Javagraph-Version";

    public static void main(String[] args) throws IOException {
        String version = "development";
        if (Manifests.exists(VERSION_ENTRY)) {
            version = Manifests.read(VERSION_ENTRY);
        }
        System.err.println("Using srclib-java version '" + version + "'");

        if (SystemUtils.IS_OS_WINDOWS) {
            args = adjustWindowsArgs(args);
        }

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

    /**
     * The purpose of this function is to convert Windows-style options produced by go-flags (/SHORTNAME, /LONGNAME) to
     * POSIX-style to be able to parse them using JCommander. Each /NAME is replaced with --NAME
     * @param args arguments to convert to POSIX-style
     * @return arguments converted to POSIX-style
     */
    private static String[] adjustWindowsArgs(String args[]) {
        String ret[] = new String[args.length];
        int i = 0;
        for (String arg: args) {
            if (arg.length() > 1 && arg.startsWith("/")) {
                arg = "--" + arg.substring(1);
            }
            ret[i++] = arg;
        }
        return ret;
    }
}
