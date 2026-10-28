package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

/** NOTICE and its in-app copy must stay identical and must name every bundled or downloadable OCR component. */
public class NoticeTest {
    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    @Test public void noticeAndAssetCopyAreIdenticalAndComplete() throws Exception {
        String notice = read("../NOTICE");
        String asset = read("src/main/assets/third_party_licenses.txt");
        assertEquals("keep src/main/assets/third_party_licenses.txt identical to NOTICE", notice, asset);
        for (String name : new String[]{"ONNX Runtime", "1.28.0", "MIT License", "PaddleOCR", "PP-OCRv5", "RapidOCR",
                "Apache License 2.0", "GPL-2.0-only"}) {
            assertTrue("NOTICE does not mention " + name, notice.contains(name));
        }
        assertFalse("sherpa-onnx is not used by TransGram", notice.contains("sherpa"));
    }

    @Test public void everyManifestPackHasALicense() throws Exception {
        ModelManifest m = ModelManifest.parse(read("src/main/assets/models/manifest.json"));
        for (ModelManifest.Pack p : m.packs) {
            assertTrue(p.id + " has no license text", p.license != null && !p.license.trim().isEmpty());
        }
    }
}
