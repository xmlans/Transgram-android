package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RecognitionResultStoreTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static RecognitionRequest req(int account, long dialog, int msg) {
        return new RecognitionRequest(RecognitionRequest.Kind.OCR, account, dialog, msg, "/unused");
    }

    @Test public void roundTripKeepsChineseTextAndNewlines() throws Exception {
        RecognitionResultStore s = new RecognitionResultStore(tmp.newFolder(), 10);
        RecognitionRequest r = req(0, -100123L, 5);
        assertNull(s.get(r));
        assertFalse(s.has(r));
        s.put(r, "今日特价\n红烧肉 38 元");
        assertTrue(s.has(r));
        assertEquals("今日特价\n红烧肉 38 元", s.get(r));
    }

    @Test public void accountsDialogsAndMessagesDoNotCollide() throws Exception {
        RecognitionResultStore s = new RecognitionResultStore(tmp.newFolder(), 10);
        s.put(req(0, 1, 1), "ocr");
        s.put(req(1, 1, 1), "other account");
        s.put(req(0, 2, 1), "other dialog");
        s.put(req(0, 1, 2), "other message");
        assertEquals("ocr", s.get(req(0, 1, 1)));
        assertEquals("other account", s.get(req(1, 1, 1)));
        assertEquals("other dialog", s.get(req(0, 2, 1)));
        assertEquals("other message", s.get(req(0, 1, 2)));
    }

    @Test public void oldestFilesAreDeletedBeyondTheLimit() throws Exception {
        File root = tmp.newFolder();
        RecognitionResultStore s = new RecognitionResultStore(root, 3);
        for (int i = 1; i <= 5; i++) {
            RecognitionRequest r = req(0, 1, i);
            s.put(r, "t" + i);
            // make the order of "last written" unambiguous on file systems with coarse timestamps
            new File(new File(root, "ocr"), r.fileName()).setLastModified(1_000_000L + i * 1000L);
        }
        assertEquals(3, new File(root, "ocr").list().length);
        assertNull(s.get(req(0, 1, 1)));
        assertEquals("t5", s.get(req(0, 1, 5)));
    }

    @Test public void removeAndClearAll() throws Exception {
        RecognitionResultStore s = new RecognitionResultStore(tmp.newFolder(), 10);
        RecognitionRequest a = req(0, 1, 1);
        RecognitionRequest b = req(0, 1, 2);
        s.put(a, "x");
        s.put(b, "y");
        s.remove(a);
        assertFalse(s.has(a));
        assertTrue(s.has(b));
        s.clearAll();
        assertFalse(s.has(b));
    }

    @Test public void rememberedMissesAndHitsStayCorrectAfterChanges() throws Exception {
        File root = tmp.newFolder();
        RecognitionResultStore s = new RecognitionResultStore(root, 10);
        RecognitionRequest r = req(0, 1, 1);
        assertNull(s.get(r));                       // a miss is remembered...
        s.put(r, "first");                          // ...but a put must replace it
        assertEquals("first", s.get(r));
        new File(new File(root, "ocr"), r.fileName()).delete();
        assertEquals("first", s.get(r));            // served from memory
        s.remove(r);
        assertNull(s.get(r));
        s.put(r, "second");
        assertEquals("second", s.get(r));
    }

    @Test public void requestKeyAndFileNameAreStable() {
        RecognitionRequest r = req(2, -1001L, 77);
        assertEquals("ocr:2_-1001_77", r.key());
        assertEquals("2_-1001_77.txt", r.fileName());
    }
}
