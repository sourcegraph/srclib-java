package com.sourcegraph.javagraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GraphData implements GraphWriter {
	public final List<Ref> refs = new ArrayList<>();
	public final List<Symbol> symbols = new ArrayList<>();

	public List<Ref> refsTo(Symbol.Key symbol) {
		List<Ref> symrefs = new ArrayList<>();
		for (Ref r : refs) {
			if (r.symbol.equals(symbol) || (symbol.origin.equals("*") && r.symbol.path.equals(symbol.path))) {
				symrefs.add(r);
			}
		}
		return symrefs;
	}

	@Override
	public void writeRef(Ref r) throws IOException {
		refs.add(r);
	}

	@Override
	public void writeSymbol(Symbol s) throws IOException {
		symbols.add(s);
	}

	@Override
	public void flush() throws IOException {
	}

	public void printRefs() throws IOException {
		System.err.println("## Printing " + refs.size() + " refs");
		for (Ref r : refs) {
			System.err.println(r.toJSONString());
		}
		System.err.flush();
	}

	public void printSymbols() throws IOException {
		System.err.println("## Printing " + symbols.size() + " symbols");
		for (Symbol s : symbols) {
			System.err.println(s.toJSONString());
		}
		System.err.flush();
	}
}
