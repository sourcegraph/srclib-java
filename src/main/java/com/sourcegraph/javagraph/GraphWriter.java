package com.sourcegraph.javagraph;

import java.io.IOException;

/**
 * This interface is responsible for collecting and writing references and definitions produced by grapher
 */
public interface GraphWriter {

    /**
     * May modify encountered references before writing them. For example, writer may transform span offsets
     * computed based on character position to byte positions.
     * @param r refs to prepare
     */
    void prepareRef(Ref r);

    /**
     * Writes reference
     * @param r reference to write
     * @throws IOException
     */
    void writeRef(Ref r) throws IOException;

    /**
     * Writes definition
     * @param s definition to write
     * @throws IOException
     */
    void writeDef(Def s) throws IOException;

    /**
     * Flush underlying streams
     * @throws IOException
     */
    void flush() throws IOException;
}
