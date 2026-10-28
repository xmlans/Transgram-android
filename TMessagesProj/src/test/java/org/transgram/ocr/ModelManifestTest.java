package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

public class ModelManifestTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static String file(String path, String sha, String sources) {
        return "{\"path\":\"" + path + "\",\"size\":100,\"sha256\":\"" + sha + "\",\"sources\":" + sources + "}";
    }

    private static final String SRC2 = "[{\"id\":\"hf\",\"url\":\"https://huggingface.co/a\"},"
            + "{\"id\":\"modelscope\",\"url\":\"https://www.modelscope.cn/a\"}]";

    private static String manifest(String packBody) {
        return "{\"schema\":1,\"packs\":[" + packBody + "]}";
    }

    private static String pack(String id, String files) {
        return "{\"id\":\"" + id + "\",\"version\":\"1\",\"kind\":\"ocr\",\"title\":\"t\",\"license\":\"l\",\"files\":[" + files + "]}";
    }

    private static void rejects(String json, String fragment) {
        try {
            ModelManifest.parse(json);
            fail("expected rejection containing: " + fragment);
        } catch (ModelManifest.ManifestException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(fragment));
        }
    }

    @Test public void parsesValidManifest() throws Exception {
        ModelManifest m = ModelManifest.parse(manifest(pack("p1", file("a/b.onnx", SHA, SRC2) + "," + file("c.txt", SHA, SRC2))));
        ModelManifest.Pack p = m.pack("p1");
        assertNotNull(p);
        assertEquals(2, p.files.size());
        assertEquals(200, p.totalBytes());
        assertEquals("p1/1", p.relativeDir());
    }

    @Test public void rejectsBadJsonAndSchema() {
        rejects("{not json", "not valid JSON");
        rejects("{\"schema\":2,\"packs\":[]}", "schema");
        rejects("{\"schema\":1,\"packs\":[]}", "no packs");
    }

    @Test public void rejectsUnsafePaths() {
        rejects(manifest(pack("p", file("../x", SHA, SRC2))), "unsafe path");
        rejects(manifest(pack("p", file("/x", SHA, SRC2))), "unsafe path");
        rejects(manifest(pack("p", file("a\\\\b", SHA, SRC2))), "unsafe path");
    }

    @Test public void rejectsBadHashSizeSources() {
        rejects(manifest(pack("p", file("a", "ABC", SRC2))), "sha256");
        rejects(manifest(pack("p", file("a", SHA.toUpperCase(), SRC2))), "sha256");
        rejects(manifest(pack("p", file("a", SHA, "[]"))), "no sources");
        rejects(manifest(pack("p", file("a", SHA, "[{\"id\":\"hf\",\"url\":\"http://x\"}]"))), "bad source");
        rejects(manifest(pack("p", "{\"path\":\"a\",\"size\":0,\"sha256\":\"" + SHA + "\",\"sources\":" + SRC2 + "}")), "size");
    }

    @Test public void rejectsDuplicatesAndBadKind() {
        rejects(manifest(pack("p", file("a", SHA, SRC2)) + "," + pack("p", file("a", SHA, SRC2))), "duplicate pack");
        rejects(manifest(pack("p", file("a", SHA, SRC2) + "," + file("a", SHA, SRC2))), "duplicate path");
        rejects(manifest(pack("p", file("a", SHA, SRC2)).replace("\"ocr\"", "\"zzz\"")), "bad kind");
        // TransGram only ships OCR: speech-recognition and image-label packs are not accepted
        rejects(manifest(pack("p", file("a", SHA, SRC2)).replace("\"ocr\"", "\"asr\"")), "bad kind");
    }

    @Test public void sourceOrderAutoAndPreferred() throws Exception {
        ModelManifest m = ModelManifest.parse(manifest(pack("p", file("a", SHA, SRC2))));
        ModelManifest.FileEntry f = m.pack("p").files.get(0);
        List<ModelManifest.Source> auto = ModelManifest.orderedSources(f, "auto");
        assertEquals("modelscope", auto.get(0).id);
        assertEquals("hf", auto.get(1).id);
        List<ModelManifest.Source> hf = ModelManifest.orderedSources(f, "hf");
        assertEquals("hf", hf.get(0).id);
        assertEquals(2, hf.size());
        assertEquals(2, ModelManifest.orderedSources(f, "github").size());
    }
}
