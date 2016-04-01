package com.sourcegraph.javagraph;

import java.util.Collection;

/**
 * tool descriptor
 */
class Toolref {

    /**
     * Toolchain is the toolchain path of the toolchain that contains this tool.
     */
    String Toolchain;

    /**
     * Subcmd is the name of the toolchain subcommand that runs this tool.
     */
    String Subcmd;
}
