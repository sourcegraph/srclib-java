package com.sourcegraph.javagraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.Error;
import java.lang.reflect.Type;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.beust.jcommander.Parameter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.stream.JsonWriter;
import com.sourcegraph.javagraph.DepresolveCommand.Resolution;

public class GraphCommand {

	@Parameter
	private List<String> files = new ArrayList<String>();

	static class Doc {
		public Doc(Symbol symbol) {
			Path = symbol.key.formatPath();

			//TODO(rameshvarun): Render javadoc string?
			Format = "text/html";
			Data = symbol.doc;
			File = symbol.file;
		}

		String Path;
		String Format;
		String Data;

		String File;
	}


	/**
	 * The Source Unit that is read in from STDIN. Defined here, so that it can be
	 * accessed within the anonymous classes below.
	 */
	public static SourceUnit unit = null;

	static class Graph {
		List<Symbol> Defs = new LinkedList<Symbol>();
		List<Ref> Refs = new LinkedList<Ref>();
		List<Doc> Docs = new LinkedList<Doc>();
	}

	public static String getGradleClassPath(Path gradleFile)
		throws IOException
	{
		return ScanCommand.getGradleClassPath(gradleFile);
	}

	public static String getMavenClassPath(Path pomFile) {

		String[] buildClasspathArgs = {
			"mvn", "dependency:build-classpath", "-Dmdep.outputFile=/dev/stderr"};

		ProcessBuilder pb = new ProcessBuilder(buildClasspathArgs);
		pb.directory(pomFile.getParent().toFile());

		try {
			Process process = pb.start();
			IOUtils.copy(process.getInputStream(), System.err);
			return IOUtils.toString(process.getErrorStream());
		} catch (Exception e1) {
			e1.printStackTrace();
			System.exit(1);
			return null;
		}
	}


	public void Execute() {
		final Graph graph = new Graph(); // Final graph object that is serialized to stdout
		final GraphData rawGraph = new GraphData(); // Raw graph from the tree traversal

		GsonBuilder gsonBuilder = new GsonBuilder().serializeNulls();

		// Serializing Symbols
		gsonBuilder.registerTypeAdapter(Symbol.class, new JsonSerializer<Symbol> () {
			@Override
			public JsonElement serialize(Symbol sym, Type arg1,
					JsonSerializationContext arg2) {
				JsonObject object = new JsonObject();

				if(sym.file != null)
					object.add("File", new JsonPrimitive(sym.file));

				object.add("Name", new JsonPrimitive(sym.name));

				object.add("DefStart", new JsonPrimitive(sym.defStart));
				object.add("DefEnd", new JsonPrimitive(sym.defEnd));

				if(sym.modifiers != null)
					object.add("Exported", new JsonPrimitive(sym.modifiers.contains("public")));
				else
					object.add("Exported", new JsonPrimitive(false));

				switch(sym.kind) {
					case "ENUM": case "CLASS": case "INTERFACE": case "ANNOTATION_TYPE":
						object.add("Kind", new JsonPrimitive("type"));
						break;
					case "METHOD": case "CONSTRUCTOR":
						object.add("Kind", new JsonPrimitive("func"));
						break;
					case "PACKAGE":
						object.add("Kind", new JsonPrimitive("package"));
						break;
					default:
						object.add("Kind", new JsonPrimitive("var"));
						break;
				}

				object.add("Path", new JsonPrimitive(sym.key.formatPath()));
				object.add("TreePath", new JsonPrimitive(sym.key.formatTreePath()));

				// Populate extra data field
				JsonObject data = new JsonObject();
				data.addProperty("JavaKind", sym.kind);
				data.addProperty("TypeExpression", sym.typeExpr);
				data.addProperty("Package", sym.pkg);

				if(sym.modifiers != null) {
					JsonArray modifiers = new JsonArray();
					for(String modifier : sym.modifiers) modifiers.add(new JsonPrimitive(modifier));
					data.add("Modifiers", modifiers);
				}

				object.add("Data", data);

				return object;
			}

		});

		// Serializing Refs
		gsonBuilder.registerTypeAdapter(Ref.class, new JsonSerializer<Ref> () {
			@Override
			public JsonElement serialize(Ref ref, Type arg1,
					JsonSerializationContext arg2) {
				JsonObject object = new JsonObject();

				object.addProperty("Origin", ref.symbol.origin);

				boolean remoteSymbol = !ref.symbol.origin.isEmpty() && !ref.symbol.origin.startsWith("file:");
				if(remoteSymbol) {
					Resolution resolution = ref.symbol.resolveOrigin(unit.Dependencies);

					if(resolution != null && resolution.Error == null) {
						object.add("DefRepo", new JsonPrimitive(resolution.Target.ToRepoCloneURL));
						object.add("DefUnitType", new JsonPrimitive(resolution.Target.ToUnitType));
						object.add("DefUnit", new JsonPrimitive(resolution.Target.ToUnit));
					}
					else {
						System.err.println("Could not resolve origin: " + ref.file + ":" + ref.start + "-" +  ref.end + " => " + ref.symbol.origin);
						return new JsonPrimitive("unresolved");
					}
				}

				object.add("DefPath", new JsonPrimitive(ref.symbol.formatPath()));

				Symbol symbol = rawGraph.getSymbolFromKey(ref.symbol);
				if(symbol != null && symbol.identStart == ref.start && symbol.identEnd == ref.end) {
					object.add("Def", new JsonPrimitive(true));
				} else {
					object.add("Def", new JsonPrimitive(false));
				}

				object.add("File", new JsonPrimitive(ref.file));
				object.add("Start", new JsonPrimitive(ref.start));
				object.add("End", new JsonPrimitive(ref.end));

				return object;
			}

		});

		Gson gson = gsonBuilder.create();

		System.err.println("Reading in SourceUnit from StdIn...");
		try {
			InputStreamReader reader = new InputStreamReader(System.in);
			unit = gson.fromJson(reader, SourceUnit.class);
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}

		String classPath = "";
		String sourcePath = "";
		if(!unit.isStdLib()) {
			// Get dependency classpaths if this is not the stdlib
			System.err.println("Getting classpath...");

			try {
				if (unit.Data.containsKey("GradleFile")) {
					String filename = (String)unit.Data.get("GradleFile");
					classPath = getGradleClassPath(Paths.get(filename));
				} else if (unit.Data.containsKey("POMFile")) {
					String filename = (String)unit.Data.get("POMFile");
					classPath = getMavenClassPath(Paths.get(filename));
				} else {
					throw new Error("Malformed source unit! Expected Data.POMFile or Data.GradleFile");
				}

			} catch(IOException e) {
				e.printStackTrace();
				System.exit(1);
			}


			sourcePath = unit.Dir + "/src/";
		}
		else {
			if(unit.Type.equals("Java")) {
				sourcePath = String.join(":", ScanCommand.getSourcePaths());
			} else {
				sourcePath = unit.Dir;
			}
		}

		try{
			Grapher grapher = new Grapher(classPath, sourcePath, rawGraph );

			String[] paths = unit.Files.toArray(new String[unit.Files.size()]);

			grapher.graph(paths);
			grapher.close();

			for(Symbol symbol : rawGraph.symbols) {
				graph.Defs.add(symbol);

				// Ignore empty docstrings
				if(symbol.doc != null)
					graph.Docs.add(new Doc(symbol));
			}

			graph.Refs = rawGraph.refs;
		}
		catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		// Print out Defs
		System.out.print("{\"Defs\": [");
		for(Symbol def : graph.Defs) {
			if(def != graph.Defs.get(0)) System.out.print(",");
			System.out.print(gson.toJson(def));
		}

		// Print out Refs
		System.out.print("], \"Refs\": [");
		boolean firstRef = true;
		for(Ref ref : graph.Refs) {
			String refString = gson.toJson(ref);
			if(!refString.equals("\"unresolved\"")) {
				if(firstRef) firstRef = false;
				else System.out.print(",");
				System.out.print(refString);
			}
		}

		// Print out Docs
		System.out.print("], \"Docs\": [");
		for(Doc doc : graph.Docs) {
			if(doc != graph.Docs.get(0)) System.out.print(",");
			System.out.print(gson.toJson(doc));
		}

		System.out.print("]}");
	}
}
