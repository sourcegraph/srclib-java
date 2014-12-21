package com.sourcegraph.javagraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphData implements GraphWriter {
    public final List<Ref> refs = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();

    public final Map<Symbol.Key, Symbol> keyToSymbol = new HashMap<Symbol.Key, Symbol>();

    public List<Ref> refsTo(Symbol.Key symbol) {
        List<Ref> symrefs = new ArrayList<>();
        for (Ref r : refs) {
            if (r.symbol.equals(symbol) || (symbol.getOrigin().getPath().equals("ANY") && r.symbol.getPath().equals(symbol.getPath()))) {
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
        keyToSymbol.put(s.key, s);
    }

    public Symbol getSymbolFromKey(Symbol.Key key) {
        return keyToSymbol.get(key);
    }

    @Override
    public void flush() throws IOException {
    }

}
