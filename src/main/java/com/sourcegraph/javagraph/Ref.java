package com.sourcegraph.javagraph;

public class Ref  {
    Symbol.Key symbol;

    String file;
    int start;
    int end;

    public Ref(Symbol.Key symbol, String file, int start, int end) {
        this.symbol = symbol;
        this.file = file;
        this.start = start;
        this.end = end;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + end;
        result = prime * result + ((file == null) ? 0 : file.hashCode());
        result = prime * result + start;
        result = prime * result + ((symbol == null) ? 0 : symbol.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Ref other = (Ref) obj;
        if (end != other.end)
            return false;
        if (file == null) {
            if (other.file != null)
                return false;
        } else if (!file.equals(other.file))
            return false;
        if (start != other.start)
            return false;
        if (symbol == null) {
            if (other.symbol != null)
                return false;
        } else if (!symbol.equals(other.symbol))
            return false;
        return true;
    }
}
