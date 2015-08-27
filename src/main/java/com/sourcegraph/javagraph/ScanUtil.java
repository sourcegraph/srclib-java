package com.sourcegraph.javagraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * File scan utilities
 */
public class ScanUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanUtil.class);

    /**
     * Retrieves all matching files in current working directory
     * @param fileName file name to match against
     * @return set of found files
     * @throws IOException
     */
    public static HashSet<Path> findMatchingFiles(String fileName) throws IOException {
        String pat = "glob:**/" + fileName;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pat);
        HashSet<Path> result = new HashSet<>();

        Files.walkFileTree(Paths.get("."), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (matcher.matches(file))
                    result.add(file.toAbsolutePath().normalize());

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // Skip common build data directories and dot-directories.
                String dirName = dir.getFileName().normalize().toString();
                if (dirName.equals("build") || dirName.equals("target") || dirName.startsWith(".")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    /**
     * Recursively finds all java files in a given source directory
     * @param sourcePath source directory to scan for java files
     * @return list of found java files
     */
    public static List<String> scanFiles(String sourcePath) throws IOException {
        final List<String> files = new LinkedList<>();

        if (Files.exists(Paths.get(sourcePath))) {
            Files.walkFileTree(Paths.get(sourcePath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String filename = file.toString();
                    if (filename.endsWith(".java")) {
                        filename = PathUtil.normalize(filename);
                        if (filename.startsWith("./"))
                            filename = filename.substring(2);
                        files.add(filename);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            LOGGER.warn("{} does not exist, skipping", sourcePath);
        }

        return files;
    }

    /**
     * Recursively scans all provided source path elements for java files
     * @param sourcePaths list of directories to search in
     * @return list of found java files
     * @throws IOException
     */
    public static List<String> scanFiles(Collection<String> sourcePaths) throws IOException {
        final LinkedList<String> files = new LinkedList<>();
        for (String sourcePath : sourcePaths)
            files.addAll(scanFiles(sourcePath));
        return files;
    }
}
