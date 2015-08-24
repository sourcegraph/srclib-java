package com.sourcegraph.javagraph;

import com.sun.tools.javac.code.Symbol.ClassSymbol;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

public class Origins {

    private static JavaFileObject lastElementObject;

    public static JavaFileObject forElement(Element e) {
        switch (e.getKind()) {
            case CLASS:
            case INTERFACE:
            case ENUM:
            case ANNOTATION_TYPE:
                return forClass((ClassSymbol) e);
            case PACKAGE:
                return lastElementObject;
            default:
                return forElement(e.getEnclosingElement());
        }
    }

    public static JavaFileObject forClass(ClassSymbol s) {
        lastElementObject = s.classfile;
        return s.classfile;
    }

}
