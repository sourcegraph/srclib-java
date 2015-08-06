package com.sourcegraph.javagraph;

import org.apache.commons.lang3.SystemUtils;

import java.io.File;
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
        Path p = Paths.get(path).toAbsolutePath();
        if (p.startsWith(CWD)) {
            return normalize(CWD.relativize(p).normalize().toString());
        } else {
            // for example, Android projects may refer to a directory on other drive
            return normalize(p.normalize().toString());
        }
    }

    public static Path concat(Path parent, Path child) {
        if (child.isAbsolute()) {
            return child;
        } else {
            return parent.resolve(child);
        }
    }

    public static File concat(File parent, File child) {
        if (child.isAbsolute()) {
            return child;
        } else {
            return new File(parent, child.getPath());
        }
    }

    public static File concat(File parent, String child) {
        return concat(parent, new File(child));
    }


}
