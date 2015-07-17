package com.sourcegraph.javagraph;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.jvm.ClassReader.BadClassFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.JavaFileObject;

public class Origins {

    private static final Logger LOGGER = LoggerFactory.getLogger(Origins.class);

    public static JavaFileObject forElement(Element e) {
        switch (e.getKind()) {
            case CLASS:
            case INTERFACE:
            case ENUM:
            case ANNOTATION_TYPE:
                return forClass((ClassSymbol) e);
            case PACKAGE:
                return forPackage((PackageElement) e);
            default:
                return forElement(e.getEnclosingElement());
        }
    }

    public static JavaFileObject forClass(ClassSymbol s) {
        return s.classfile;
    }

    public static JavaFileObject forPackage(PackageElement s) {
        // Packages can be defined by multiple JARs, but let's just take the JAR
        // that defines this package's first element.
        try {
            for (Element e : s.getEnclosedElements()) {
                JavaFileObject o = forElement(e);
                if (o != null) {
                    return o;
                }
            }
        } catch (BadClassFile ex) {
            LOGGER.warn("Bad class file", ex);
        }
        return null;
    }
}
