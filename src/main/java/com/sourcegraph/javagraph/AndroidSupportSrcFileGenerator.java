package com.sourcegraph.javagraph;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

/**
 * This class generates Sourcefile for Android Support framework because it needs special treatment, such as
 * - there is no need to run scanners, everything is included into Srcfile's units
 * - source unit should contain special markers indicating that we are working with Android Support framework project
 * Usage: AndroidSupportSrcFileGenerator [DIRECTORY]
 * Expected scenario
 * - clone Android source code
 * - build Android source code
 * - generate Sourcefile for Support framework
 * - run `srclib make`
 */
public class AndroidSupportSrcFileGenerator {

    public static void main(String[] args) throws IOException {

        if (args.length > 0) {
            PathUtil.CWD = Paths.get(args[0]);
        }

        JSONUtil.writeJSON(getSrcfile());
    }

    /**
     * Generates Srcfile for Android libcore
     * @return generated Srcfile
     * @throws IOException
     */
    private static Srcfile getSrcfile() throws IOException {
        Collection<SourceUnit> units = new LinkedList<>();

        units.add(AndroidSupportProject.createSourceUnit());

        Srcfile srcfile = new Srcfile();
        srcfile.SourceUnits = units;
        srcfile.Scanners = Collections.emptyList();
        return srcfile;
    }
}
