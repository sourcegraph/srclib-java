package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtil {

    public static final Path CWD = SystemUtils.getUserDir().toPath().toAbsolutePath().normalize();

    public static String normalize(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return path.replace('\\', '/');
        } else {
            return path;
        }
    }

    public static String relativizeCwd(String path) {
        return normalize(CWD.relativize(Paths.get(path).toAbsolutePath()).normalize().toString());
    }
}
