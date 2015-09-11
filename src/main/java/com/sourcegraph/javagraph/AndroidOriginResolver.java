package com.sourcegraph.javagraph;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves Android libcore and frameworks/base origins to proper target.
 * libcore and frameworks/base combined produce android.jar thus for each class file we should check if it belongs to
 * libcore or frameworks/base
 */
public class AndroidOriginResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(AndroidOriginResolver.class);

    private static List<String> libcoreClasses;

    static {
        InputStream is = AndroidOriginResolver.class.getResourceAsStream("/android-libcore.dat");
        if (is != null) {
            try {
                libcoreClasses = IOUtils.readLines(is);
            } catch (IOException e) {
                LOGGER.warn("Failed to load Android libcore classes list", e);
                libcoreClasses = new ArrayList<>();
            }
        } else {
            libcoreClasses = new ArrayList<>();
        }
    }

    /**
     * Resolves jar URI either to libcore or to frameworks/base resolved target
     * @param origin URI to resolve
     * @return resolved target or null if resolution failed
     */
    public static ResolvedTarget resolve(URI origin) {
        String topClassName = extractTopClassName(origin);
        if (topClassName == null) {
            return null;
        }
        int index = Collections.binarySearch(libcoreClasses, topClassName);
        if (index >= 0) {
            return ResolvedTarget.androidCore();
        } else {
            return ResolvedTarget.androidSDK();
        }
    }

    /**
     * Extracts top-level class name from jar URI (supposed to be in form jar:file..!path/to/classname.class)
     * @param origin jar URI
     * @return extracted path to top-level class in form foo/bar/bazz or null
     */
    private static String extractTopClassName(URI origin) {
        String path = origin.toString();
        // retrieve part after !/ (path to class file)
        int i = path.lastIndexOf('!');
        if (i != -1) {
            path = path.substring(i + 2);
        } else {
            return null;
        }
        i = path.indexOf('$');
        if (i != -1) {
            // inner class, leaving only top-level class element
            return path.substring(0, i);
        }
        i = path.indexOf('.');
        if (i != -1) {
            // removing everything after . (stripping '.class')
            return path.substring(0, i);
        }
        return null;
    }
}
