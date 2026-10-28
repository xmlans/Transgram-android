package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/** Guards the bundled assets/models/manifest.json (the unit-test working directory is the TMessagesProj module). */
public class ManifestAssetTest {
    private static Set<String> paths(ModelManifest.Pack p) {
        Set<String> s = new HashSet<>();
        for (ModelManifest.FileEntry f : p.files) s.add(f.path);
        return s;
    }

    @Test public void bundledManifestIsValidAndComplete() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/assets/models/manifest.json")), StandardCharsets.UTF_8);
        ModelManifest m = ModelManifest.parse(json);
        assertEquals("only the OCR pack is shipped", 1, m.packs.size());
        ModelManifest.Pack ocr = m.pack("ocr-ppocrv5-mobile");
        assertNotNull(ocr);
        assertEquals("ocr", ocr.kind);
        assertTrue(paths(ocr).containsAll(java.util.Arrays.asList("det.onnx", "cls.onnx", "rec.onnx", "dict.txt")));
    }
}
