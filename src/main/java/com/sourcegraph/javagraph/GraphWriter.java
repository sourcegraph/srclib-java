package com.sourcegraph.javagraph;

import java.io.IOException;

public interface GraphWriter {
    void writeRef(Ref r) throws IOException;

    void writeDef(Def s) throws IOException;

    void flush() throws IOException;
}
