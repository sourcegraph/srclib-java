package com.sourcegraph.javagraph;

import com.sun.tools.javac.code.Symbol.ClassSymbol;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

/**
 * Resolves java file object for java program elements (classes and package)
 */
public class Origins {

    private static JavaFileObject lastElementObject;

    /**
     * resolves java file object for a given java program element
     * @param e java program element
     * @return resolved java file object
     */
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

    /**
     * resolves java file object for a given java class (interface, enum, annotation) element
     * @param s java program element
     * @return resolved java file object
     */
    public static JavaFileObject forClass(ClassSymbol s) {
        // alexsaveliev: we keeping last resolved java file object to use it when requested resolution of package's
        // java file object, because we can't reach forElement(package) without reaching forClass() first
        lastElementObject = s.classfile;
        return s.classfile;
    }

}
