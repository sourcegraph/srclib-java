package com.sourcegraph.javagraph;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONValue;

public class GraphPrinter implements GraphWriter {

	private final PrintWriter out;

	private final List<Symbol> symbols = new ArrayList<>();
	private int nrefs = 0;

	public GraphPrinter(PrintWriter w) throws IOException {
		this.out = w;
		w.write("{\"refs\":[\n");
	}

	@Override
	synchronized public void writeRef(Ref r) throws IOException {
		if (nrefs > 0)
			out.write(',');
		r.writeJSONString(out);
		out.write('\n');
		nrefs++;
	}

	@Override
	public void writeSymbol(Symbol s) throws IOException {
		symbols.add(s);
	}

	@Override
	public void flush() throws IOException {
		out.write("],\n\"symbols\":[\n");
		int i = 0;
		for (Symbol s : symbols) {
			if (i != 0)
				out.write(',');
			JSONValue.writeJSONString(s, out);
			out.write('\n');
			i++;
		}
		out.write("]}\n");
		out.flush();
	}
}
