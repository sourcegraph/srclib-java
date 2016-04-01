package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.plexus.util.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.*;

public class GraphCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphCommand.class);

    @Parameter(names = {"--debug-unit-file"}, description = "The path to a source unit input file, which will be read as though it came from stdin. Used to mimic stdin when you can't actually pipe to stdin (e.g., in IntelliJ run configurations).")
    String debugUnitFile;

    /**
     * The Source Unit that is read in from STDIN. Defined here, so that it can be
     * accessed within the anonymous classes below.
     */
    public static SourceUnit unit;

    /**
     * Main method
     */
    @SuppressWarnings("unchecked")
    public void Execute() {

        final Graph graph = new Graph(); // Final graphJavaFiles object that is serialized to stdout
        final GraphData rawGraph = new GraphData(); // Raw graphJavaFiles from the tree traversal
        Reader r = null;
        try {
            if (!StringUtils.isEmpty(debugUnitFile)) {
                LOGGER.debug("Reading source unit JSON data from {}", debugUnitFile);
                r = Files.newBufferedReader(FileSystems.getDefault().getPath(debugUnitFile));
            } else {
                r = new InputStreamReader(System.in);
            }
            unit = new Gson().fromJson(r, SourceUnit.class);
            r.close();
        } catch (IOException e) {
            LOGGER.error("Failed to read source unit data", e);
            System.exit(1);
        } finally {
            IOUtils.closeQuietly(r);
        }
        LOGGER.info("Building graph for {}", unit.Name);

        Project proj = unit.getProject();
        Resolver rs = new Resolver(proj, unit);
        try {
            Grapher grapher = new Grapher(unit,
                    rawGraph);
            LOGGER.debug("Starting graph collection");
            Collection<String> files = new ArrayList<>();
            if (unit.Files != null) {
                files.addAll(unit.Files);
            }
            files.addAll(collectFilesUsingGlobs(unit.Globs));
            Collection<String> extraFiles = (Collection<String>) unit.Data.get("ExtraSourceFiles");
            if (extraFiles != null) {
                files.addAll(extraFiles);
            }
            grapher.graphFilesAndDirs(files);
            LOGGER.debug("Graph collection complete");
            grapher.close();

            LOGGER.debug("Collecting defs");
            graph.Defs = rawGraph.defs;
            for (Def def : rawGraph.defs) {
                // Ignore empty docstrings.
                if (def.doc != null) {
                    graph.Docs.add(new Doc(def));
                }
            }
            LOGGER.debug("Collecting refs");
            for (Ref ref : rawGraph.refs) {
                ResolvedTarget target = rs.resolveOrigin(ref.defKey.getOrigin());
                if (target != null) {
                    ref.setDefTarget(target);
                }
            }
            graph.Refs = rawGraph.refs;
        } catch (Exception e) {
            LOGGER.error("Unexpected error occurred while building graph", e);
            System.exit(1);
        }

        JSONUtil.writeJSON(graph);
    }

    /**
     * Collects files using globs if any
     * @param globs globs
     * @return list of files matching given globs
     */
    private Collection<String> collectFilesUsingGlobs(List<String> globs) {
        if (globs == null || globs.isEmpty()) {
            return Collections.emptyList();
        }
        LOGGER.info("Collecting files using globs");
        final DirectoryScanner directoryScanner = new DirectoryScanner();
        String globsArray[] = new String[globs.size()];
        globs.toArray(globsArray);
        directoryScanner.setIncludes(globsArray);
        directoryScanner.setExcludes(new String[] {".gradle-srclib/**", ".m2-srclib/**"});
        directoryScanner.setBasedir(PathUtil.CWD.toString());
        directoryScanner.scan();
        Collection<String> files = new LinkedList<>();
        for (String fileName : directoryScanner.getIncludedFiles()) {
            files.add(PathUtil.concat(PathUtil.CWD, fileName).toString());
        }
        return files;
    }

    /**
     * Javadoc object
     */
    static class Doc {

        /**
         * Path to java doc element (definition key)
         */
        String Path;
        /**
         * Format
         */
        String Format;
        /**
         * Javadoc content
         */
        String Data;
        /**
         * Source file
         */
        String File;
        /**
         * Source unit
         */
        String Unit;

        public Doc(Def def) {
            Path = def.defKey.formatPath();

            //TODO(rameshvarun): Render javadoc string?
            Format = "text/html";
            Data = def.doc;
            File = PathUtil.relativizeCwd(def.file);
            Unit = def.unit;
        }
    }

    /**
     * Graph object that keeps definitions, references, and docs
     */
    static class Graph {
        List<Def> Defs = new LinkedList<>();
        List<Ref> Refs = new LinkedList<>();
        List<Doc> Docs = new LinkedList<>();
    }
}
