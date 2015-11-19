package com.sourcegraph.javagraph;

import com.beust.jcommander.JCommander;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

        if (System.getenv().get("IN_DOCKER_CONTAINER") != null) {
            File sourceDir = SystemUtils.getUserDir();
            // in Docker mode copying current directory to temporary location to clead readonly flag
            Path tempDir = Files.createTempDirectory(FileUtils.getTempDirectory().toPath(), "srclib-java");
            File destDir = new File(tempDir.toFile(), sourceDir.getName());
            LOGGER.debug("Copying {} to {}", sourceDir, destDir);
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        FileUtils.deleteDirectory(tempDir.toFile());
                    } catch (IOException e) {
                        // ignore
                    }
                }
            });
            FileUtils.copyDirectory(sourceDir, destDir, new FileFilter() {
                @Override
                public boolean accept(File f) {
                    // excluding .srclib-cache and unreadable entries
                    if (f.isDirectory()) {
                        return !f.getName().equals(".srclib-cache") && f.canRead();
                    }
                    return f.canRead();
                }
            });
            LOGGER.debug("Copied {} to {}", sourceDir, destDir);
            // updating CWD
            PathUtil.CWD = destDir.toPath();
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
