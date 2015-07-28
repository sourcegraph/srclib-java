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

public class ScanUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScanUtil.class);

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

    // Recursively find .java files under a given source path
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

    public static List<String> scanFiles(Collection<String> sourcePaths) throws IOException {
        final LinkedList<String> files = new LinkedList<>();
        for (String sourcePath : sourcePaths)
            files.addAll(scanFiles(sourcePath));
        return files;
    }

    public static List<String> findAllJavaFiles(Path resolve) throws IOException {
        return scanFiles(resolve.toString());
    }
}
