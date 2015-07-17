package com.sourcegraph.javagraph;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

public class GraphCommand {
    @Parameter(names = {"--debug-unit-file"}, description = "The path to a source unit input file, which will be read as though it came from stdin. Used to mimic stdin when you can't actually pipe to stdin (e.g., in IntelliJ run configurations).")
    String debugUnitFile;

    /**
     * The Source Unit that is read in from STDIN. Defined here, so that it can be
     * accessed within the anonymous classes below.
     */
    public static SourceUnit unit = null;

    public void Execute() {
        final Graph graph = new Graph(); // Final graphJavaFiles object that is serialized to stdout
        final GraphData rawGraph = new GraphData(); // Raw graphJavaFiles from the tree traversal

        try {
            Reader r;
            if (debugUnitFile != null && !debugUnitFile.isEmpty()) {
                System.err.println("Reading source unit JSON from --debug-unit-file " + debugUnitFile);
                r = Files.newBufferedReader(FileSystems.getDefault().getPath(debugUnitFile));
            } else {
                r = new InputStreamReader(System.in);
            }
            unit = new Gson().fromJson(r, SourceUnit.class);
            r.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        Project proj = unit.getProject();
        Resolver rs = new Resolver(proj);
        try {
            String classpath = StringUtils.join(proj.getClassPath(), SystemUtils.PATH_SEPARATOR);
            Grapher grapher = new Grapher(classpath, StringUtils.EMPTY, rawGraph);
            grapher.graphFilesAndDirs(unit.Dir, unit.Files);
            grapher.close();

            graph.Defs = rawGraph.defs;
            for (Def def : rawGraph.defs) {
                // Ignore empty docstrings.
                if (def.doc != null) {
                    graph.Docs.add(new Doc(def));
                }
            }

            for (Ref ref : rawGraph.refs) {
                ResolvedTarget target = rs.resolveOrigin(ref.defKey.getOrigin());
                if (target != null) {
                    ref.setDefTarget(target);
                }
            }
            graph.Refs = rawGraph.refs;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

        JSONUtil.writeJSON(graph);
    }

    static class Doc {
        String Path;
        String Format;
        String Data;
        String File;

        public Doc(Def def) {
            Path = def.defKey.formatPath();

            //TODO(rameshvarun): Render javadoc string?
            Format = "text/html";
            Data = def.doc;
            File = PathUtil.normalize(def.file);
        }
    }

    static class Graph {
        List<Def> Defs = new LinkedList<>();
        List<Ref> Refs = new LinkedList<>();
        List<Doc> Docs = new LinkedList<>();
    }
}
