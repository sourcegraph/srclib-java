package com.sourcegraph.javagraph;

import com.beust.jcommander.JCommander;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final String VERSION_ENTRY = "Javagraph-Version";

    public static void main(String[] args) throws IOException {
        String version = getVersion();

        LOGGER.info("srclib-java version {}", version);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Current working directory [{}]", SystemUtils.getUserDir());
            LOGGER.debug("Command line arguments [{}]", StringUtils.join(args, ' '));
        }

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
            LOGGER.error("Unable to parse command line arguments", e);
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
                LOGGER.error("Unknown command {}", jc.getParsedCommand());
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

    private static String getVersion() {
        String version = "development";
        try {
            InputStream manifestInputStream = Main.class.getResourceAsStream("/META-INF/MANIFEST.MF");
            if (manifestInputStream != null) {
                Properties properties = new Properties();
                properties.load(manifestInputStream);
                version = properties.getProperty(VERSION_ENTRY, version);
            }
        } catch (IOException e) {
            // ignore
        }
        return version;
    }
}
