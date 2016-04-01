package com.sourcegraph.javagraph;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Representation of Srcfile
 */
class Srcfile {

    Collection<SourceUnit> SourceUnits;

    Collection<Toolref> Scanners;
}
