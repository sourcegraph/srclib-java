package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;

public class PathUtil {
    public static String normalize(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return path.replace('\\', '/');
        } else {
            return path;
        }
    }
}
