package com.sourcegraph.javagraph;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class JDKSrcFileGeneratorTest {

    private File root;
    private Path cwd;

    @Before
    public void setUp() throws Exception {

        cwd = PathUtil.CWD;

        // Changing CWD
        root = Files.createTempDirectory("srcfile-test").toFile();
        PathUtil.CWD = root.toPath();

        // src/share/classes's content should be included
        File sourceRoot = new File(root, "src/share/classes");
        FileUtils.forceMkdir(sourceRoot);
        Files.createTempFile(sourceRoot.toPath(), "", ".java");
        Files.createTempFile(sourceRoot.toPath(), "", ".java");

        // this directory should be not included
        File noSourceRoot = new File(root, "nosrc/share/classes");
        FileUtils.forceMkdir(noSourceRoot);
        Files.createTempFile(noSourceRoot.toPath(), "", ".java");

    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(root);
        PathUtil.CWD = cwd;
    }

    @Test
    public void testCreateSrcfile() throws Exception {
        Srcfile srcfile = JDKSrcFileGenerator.getSrcfile("jdk");
        assertNotNull("Expected not null Srcfile", srcfile);
        assertNotNull("Expected not null Scanners", srcfile.Scanners);
        assertTrue("There should be no Scanners", srcfile.Scanners.isEmpty());
        assertNotNull("Expected not null SourceUnits", srcfile.SourceUnits);
        assertEquals("Single source unit is expected", 1, srcfile.SourceUnits.size());
        SourceUnit unit = srcfile.SourceUnits.iterator().next();
        assertEquals("Unexpected source unit name", "jdk", unit.Name);
        assertEquals("Unexpected source unit type", "Java", unit.Type);
        assertEquals("Unexpected source unit marker", "JDK", unit.Data.get(SourceUnit.TYPE));
        assertEquals("Unexpected source unit project name", "jdk", unit.Data.get(JDKProject.JDK_PROJECT_NAME));
        assertEquals("Unexpected number of source files", 2, unit.Files.size());
    }
}
