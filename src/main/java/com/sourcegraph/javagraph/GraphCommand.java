package com.sourcegraph.javagraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class GraphCommand {
	
	public static String[] buildClasspathArgs = {"mvn", "dependency:build-classpath", "-Dmdep.outputFile=/dev/stderr"};
	
	static class Doc {
		public Doc(Symbol symbol) {
			Path = symbol.getPath();
			
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
	
	static class Graph {
		List<Symbol> Defs = new LinkedList<Symbol>();
		List<Ref> Refs = new LinkedList<Ref>();
		List<Doc> Docs = new LinkedList<Doc>();
	}
	
	public void Execute() {
		// Final graph object that is serialized to stdout
		final Graph graph = new Graph();
		
		final GraphData rawGraph = new GraphData();
		
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
				
				return object;
			}
			
		});
		
		// Serializing Refs
		gsonBuilder.registerTypeAdapter(Ref.class, new JsonSerializer<Ref> () {
			@Override
			public JsonElement serialize(Ref ref, Type arg1,
					JsonSerializationContext arg2) {
				JsonObject object = new JsonObject();
				
				if(ref.symbol.origin != "") {
					/*object.add("DefRepo", new JsonPrimitive());
					object.add("DefUnitType", new JsonPrimitive());
					object.add("DefUnit", new JsonPrimitive());*/
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
		
		SourceUnit unit = null;
		try {
			InputStreamReader reader = new InputStreamReader(System.in);
			unit = gson.fromJson(reader, SourceUnit.class);
			reader.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
		
		
		// Get dependency classpaths
		ProcessBuilder pb = new ProcessBuilder(buildClasspathArgs);
		pb.directory(new File(unit.Dir));
		String classpath = "";
		try {
			Process process = pb.start();
			process.waitFor();
			
			classpath = IOUtils.toString(process.getErrorStream());
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			System.exit(1);
		}
		
		try{
			Grapher grapher = new Grapher(classpath, "src/", rawGraph );
			
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
		
		System.out.println(gson.toJson(graph));
	}
}
