package com.sourcegraph.javagraph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of graph writer that collects references and definitions and then writes them as JSON
 */
public class GraphData implements GraphWriter {
    public final List<Ref> refs = new ArrayList<>();
    public final List<Def> defs = new ArrayList<>();

    public final Map<DefKey, Def> keyToSymbol = new HashMap<DefKey, Def>();

    /**
     * @param defKey definition key
     * @return list of references to given definition key
     */
    public List<Ref> refsTo(DefKey defKey) {
        List<Ref> refs = new ArrayList<>();
        for (Ref r : this.refs) {
            boolean exactMatch = r.defKey.equals(defKey);
            boolean fuzzyMatch = defKey.getPath().equals(r.defKey.getPath()) && (
                    (defKey.getOrigin() == null && r.defKey.getOrigin() == null) ||
                    (defKey.getOrigin() != null && defKey.getOrigin().getPath().equals("ANY"))
            );
            if (exactMatch || fuzzyMatch) {
                refs.add(r);
            }
        }
        return refs;
    }

    @Override
    public void writeRef(Ref r) throws IOException {
        refs.add(r);
    }

    @Override
    public void writeDef(Def s) throws IOException {
        defs.add(s);
        keyToSymbol.put(s.defKey, s);
    }

    public Def getSymbolFromKey(DefKey defKey) {
        return keyToSymbol.get(defKey);
    }

    @Override
    public void flush() throws IOException {
    }

}
