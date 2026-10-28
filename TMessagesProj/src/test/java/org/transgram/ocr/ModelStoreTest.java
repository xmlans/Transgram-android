package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ModelStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static ModelManifest.Pack pack(String id, String version, long... sizes) {
        ModelManifest.Pack p = new ModelManifest.Pack();
        p.id = id;
        p.version = version;
        p.kind = "ocr";
        p.files = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            ModelManifest.FileEntry f = new ModelManifest.FileEntry();
            f.path = (i == 0 ? "sub/" : "") + "f" + i + ".bin";
            f.size = sizes[i];
            p.files.add(f);
        }
        return p;
    }

    private static void write(File f, int bytes) throws Exception {
        f.getParentFile().mkdirs();
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(new byte[bytes]);
        }
    }

    @Test public void notInstalledUntilEveryFileAndTheMarkerExist() throws Exception {
        ModelStore s = new ModelStore(tmp.newFolder("models"));
        ModelManifest.Pack p = pack("a", "1", 10, 20);
        assertFalse(s.isInstalled(p));
        write(s.file(p, "sub/f0.bin"), 10);
        write(s.file(p, "f1.bin"), 20);
        assertFalse("marker missing", s.isInstalled(p));
        write(new File(s.packDir(p), ModelStore.READY), 0);
        assertTrue(s.isInstalled(p));
        assertEquals(30, s.installedBytes(p));
    }

    @Test public void wrongSizeMeansNotInstalled() throws Exception {
        ModelStore s = new ModelStore(tmp.newFolder("models"));
        ModelManifest.Pack p = pack("a", "1", 10);
        write(s.file(p, "sub/f0.bin"), 9);
        write(new File(s.packDir(p), ModelStore.READY), 0);
        assertFalse(s.isInstalled(p));
        assertEquals(0, s.installedBytes(p));
    }

    @Test public void partialBytesCountsStagingFiles() throws Exception {
        ModelStore s = new ModelStore(tmp.newFolder("models"));
        ModelManifest.Pack p = pack("a", "1", 100, 100);
        write(new File(s.stagingDir(p), "sub/f0.bin.part"), 40);
        write(new File(s.stagingDir(p), "f1.bin"), 100);
        assertEquals(140, s.partialBytes(p));
    }

    @Test public void partialCreditNeverCountsUnrelatedOrDuplicateFiles() throws Exception {
        ModelStore s = new ModelStore(tmp.newFolder("models"));
        ModelManifest.Pack p = pack("a", "1", 100);
        write(new File(s.stagingDir(p), "sub/f0.bin"), 100);
        write(new File(s.stagingDir(p), "sub/f0.bin.part"), 200);
        write(new File(s.stagingDir(p), "unrelated"), 500);
        assertEquals(100, s.partialBytes(p));
    }

    @Test public void deleteRemovesAllVersionsAndStaging() throws Exception {
        ModelStore s = new ModelStore(tmp.newFolder("models"));
        ModelManifest.Pack v1 = pack("a", "1", 5);
        ModelManifest.Pack v2 = pack("a", "2", 5);
        write(s.file(v1, "sub/f0.bin"), 5);
        write(s.file(v2, "sub/f0.bin"), 5);
        write(new File(s.stagingDir(v2), "sub/f0.bin.part"), 3);
        s.delete(v2);
        assertEquals(0, s.totalBytesOnDisk());
    }

    @Test public void pruneKeepsOnlyTheCurrentVersion() throws Exception {
        ModelStore s = new ModelStore(tmp.newFolder("models"));
        ModelManifest.Pack v1 = pack("a", "1", 5);
        ModelManifest.Pack v2 = pack("a", "2", 5);
        write(s.file(v1, "sub/f0.bin"), 5);
        write(s.file(v2, "sub/f0.bin"), 5);
        s.pruneOldVersions(v2);
        assertFalse(s.file(v1, "sub/f0.bin").exists());
        assertTrue(s.file(v2, "sub/f0.bin").exists());
    }

    @Test public void usableSpaceWorksBeforeTheRootExists() throws Exception {
        ModelStore s = new ModelStore(new File(tmp.getRoot(), "not/yet/created"));
        assertTrue(s.usableSpace() > 0);
    }
}
