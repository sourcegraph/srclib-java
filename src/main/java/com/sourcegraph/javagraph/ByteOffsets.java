package com.sourcegraph.javagraph;

import com.sun.source.tree.CompilationUnitTree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * Provides mapping between character positions and bytes positions inside compilation unit.
 * Used to determine byte offset of node span
 */
public class ByteOffsets {

    int offsets[];

    public ByteOffsets(CompilationUnitTree compilationUnit, Charset charset) {
        try {
            CharSequence src = compilationUnit.getSourceFile().getCharContent(true);
            int length = src.length();
            offsets = new int[length + 1];
            int offset = 0;
            CharsetEncoder encoder = charset.newEncoder();
            ByteBuffer out = ByteBuffer.allocate(4);
            CharBuffer in = CharBuffer.allocate(1);
            for (int i = 0; i < length; i++) {
                char c = src.charAt(i);
                in.put(c).rewind();
                encoder.encode(in, out, true);
                in.rewind();
                offsets[i] = offset;
                offset += out.position();
                out.clear();
            }
            offsets[length] = offset;
        } catch (IOException e) {
        }
    }
}
