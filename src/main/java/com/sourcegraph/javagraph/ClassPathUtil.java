package com.sourcegraph.javagraph;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ClassPathUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathUtil.class);

    public static Collection<String> transformClassPath(Collection<String> classPath) {
        Collection<String> ret = new ArrayList<>();
        for (String classPathElement : classPath) {
            ret.addAll(processClassPathElement(classPathElement));
        }
        return ret;
    }

    private static Collection<String> processClassPathElement(String classPathElement) {
        File file = new File(classPathElement);
        if (!file.isFile() || !file.getName().endsWith(".aar")) {
            return Collections.singletonList(classPathElement);
        } else {
            return unpackAar(file);
        }
    }

    private static Collection<String> unpackAar(File aarFile) {
        // TODO: use environment variable that points to SRCLIB temp directory to keep files there
        Collection<String> ret = new ArrayList<>();
        File target = Files.createTempDir();
        try (ZipFile file = new ZipFile(aarFile)) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".jar")) {
                    continue;
                }
                File dest = new File(target, entry.getName());
                FileUtils.forceMkdir(dest.getParentFile());
                FileOutputStream fos = new FileOutputStream(dest);
                IOUtils.copy(file.getInputStream(entry), fos);
                fos.close();
                ret.add(dest.getAbsolutePath());
            }
        } catch (IOException e) {
            LOGGER.warn("Unable to extract AAR file {}", aarFile, e);
        }
        return ret;
    }
}
