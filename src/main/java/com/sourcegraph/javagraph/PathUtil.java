package com.sourcegraph.javagraph;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Path utilities
 */
public class PathUtil {

    /**
     * Current working directory
     */
    public static Path CWD = SystemUtils.getUserDir().toPath().toAbsolutePath().normalize();

    /**
     * Normalizes path string by translating it to Unix-style (foo\bar => foo/bar)
     * @param path path to normalize
     * @return normalized path
     */
    public static String normalize(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return path.replace('\\', '/');
        } else {
            return path;
        }
    }

    /**
     * Produces path relative to current working directory
     * @param path path to process
     * @return path translated relative to current directory, if path is located inside current directory or
     * absolute path otherwise. For example, for path /foo/bar/baz and CWD /foo/bar result will be bar;
     * for path /foo/bar/baz and CWD /kaboom/bar result will be /foo/bar/baz
     */
    public static String relativizeCwd(String path) {
        return relativizeCwd(Paths.get(path).toAbsolutePath());
    }

    /**
     * Produces path relative to current working directory
     * @param p path to process
     * @return path translated relative to current directory, if path is located inside current directory or
     * absolute path otherwise. For example, for path /foo/bar/baz and CWD /foo/bar result will be bar;
     * for path /foo/bar/baz and CWD /kaboom/bar result will be /foo/bar/baz
     */
    public static String relativizeCwd(Path p) {
        if (p.startsWith(CWD)) {
            Path rel = CWD.relativize(p);
            if (rel.toString().isEmpty()) {
                return StringUtils.EMPTY;
            }
            return normalize(rel.normalize().toString());
        } else {
            // for example, Android projects may refer to a directory on other drive
            return normalize(p.normalize().toString());
        }
    }

    /**
     * Concatenates two paths
     * @param parent parent path
     * @param child child path
     * @return child resolved to parent if child is not absolute, child otherwise
     */
    public static Path concat(Path parent, Path child) {
        if (child.isAbsolute()) {
            return child;
        } else {
            return parent.resolve(child);
        }
    }

    /**
     * Concatenates two paths
     * @param parent parent path
     * @param child child path
     * @return child resolved to parent if child is not absolute, child otherwise
     */
    public static Path concat(Path parent, String child) {
        return concat(parent, Paths.get(child));
    }

    /**
     * Concatenates two files
     * @param parent parent file
     * @param child child file
     * @return child resolved to parent if child is not absolute, child otherwise
     */
    public static File concat(File parent, File child) {
        if (child.isAbsolute()) {
            return child;
        } else {
            return new File(parent, child.getPath());
        }
    }

    /**
     * Concatenates two files
     * @param parent parent file
     * @param child child file
     * @return child resolved to parent if child is not absolute, child otherwise
     */
    public static File concat(File parent, String child) {
        return concat(parent, new File(child));
    }


}
