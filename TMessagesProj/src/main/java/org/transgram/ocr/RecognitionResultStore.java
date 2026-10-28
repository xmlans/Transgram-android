package org.transgram.ocr;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recognition results kept on disk: root/ocr/account_dialog_message.txt, one small text file per message. When a kind
 * holds more than maxFiles files, the least recently written ones are deleted. The last few hundred lookups (hits and
 * misses) are remembered in memory, because the screen-reader text of a message asks for its OCR text every time it
 * is built. Only java.io. Thread-safe (every method is synchronized).
 */
public class RecognitionResultStore {
    public static final int DEFAULT_MAX_FILES = 500;
    private static final int MEMO_SIZE = 256;
    private static final String MISS = new String("\u0000miss");

    private final File root;
    private final int maxFiles;
    private final Map<String, String> memo = new LinkedHashMap<String, String>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MEMO_SIZE;
        }
    };

    /** In the app: RecognitionHub.results(), rooted at files/transgram_ocr. */
    public RecognitionResultStore(File root, int maxFiles) {
        this.root = root;
        this.maxFiles = maxFiles;
    }

    private File file(RecognitionRequest r) {
        return new File(new File(root, r.kind.dirName), r.fileName());
    }

    private static String memoKey(RecognitionRequest r) {
        return r.kind.dirName + "/" + r.fileName();
    }

    /** @return the stored text, or null when there is none */
    public synchronized String get(RecognitionRequest r) {
        final String key = memoKey(r);
        final String remembered = memo.get(key);
        if (remembered != null) return remembered == MISS ? null : remembered;
        final String text = read(file(r));
        memo.put(key, text == null ? MISS : text);
        return text;
    }

    private static String read(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new FileInputStream(f)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public synchronized boolean has(RecognitionRequest r) {
        return get(r) != null;
    }

    public synchronized void put(RecognitionRequest r, String text) {
        final File f = file(r);
        f.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            f.delete();
            memo.remove(memoKey(r));
            return;
        }
        memo.put(memoKey(r), text);
        trim(f.getParentFile());
    }

    public synchronized void remove(RecognitionRequest r) {
        file(r).delete();
        memo.remove(memoKey(r));
    }

    public synchronized void clearAll() {
        for (RecognitionRequest.Kind k : RecognitionRequest.Kind.values()) {
            final File[] files = new File(root, k.dirName).listFiles();
            if (files != null) for (File f : files) f.delete();
        }
        memo.clear();
    }

    private void trim(File dir) {
        final File[] files = dir.listFiles();
        if (files == null || files.length <= maxFiles) return;
        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (int i = 0; i < files.length - maxFiles; i++) files[i].delete();
        memo.clear();   // some remembered entries may point at deleted files
    }
}
