package com.sourcegraph.javagraph;

import com.beust.jcommander.JCommander;
import org.apache.commons.lang3.StringUtils;
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
            LOGGER.debug("Command line arguments [{}]", StringUtils.join(args, ' '));
        }

        LOGGER.debug("Current working directory [{}]", PathUtil.CWD);

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
