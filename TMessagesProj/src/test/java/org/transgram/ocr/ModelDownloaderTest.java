package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Runs the real downloader against a local HTTP server (plain sockets), no Android classes involved. */
public class ModelDownloaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private volatile Runnable beforeHeaders;
    private ServerSocket server;
    private String base;
    private final byte[] data = new byte[300_000];
    private String sha;
    /** Request log: "path|Range header or -". */
    private final List<String> log = new CopyOnWriteArrayList<>();
    private final Set<String> notFound = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> ignoreRange = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> dropHalfway = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> corrupt = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> wrongRange = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> oversized = Collections.synchronizedSet(new HashSet<String>());

    @Before public void start() throws Exception {
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31 + 7);
        sha = hex(MessageDigest.getInstance("SHA-256").digest(data));
        server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        final Thread acceptor = new Thread(new Runnable() {
            @Override public void run() {
                while (!server.isClosed()) {
                    final Socket s;
                    try {
                        s = server.accept();
                    } catch (IOException e) {
                        return; // closed by stop()
                    }
                    new Thread(new Runnable() {
                        @Override public void run() { serve(s); }
                    }).start();
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        base = "http://127.0.0.1:" + server.getLocalPort();
    }

    @After public void stop() throws IOException {
        server.close();
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xff));
        return sb.toString();
    }

    /** Reads one CRLF-terminated header line (ASCII). */
    private static String readLine(InputStream in) throws IOException {
        final StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        return sb.toString();
    }

    /**
     * A minimal HTTP/1.1 server (one request per connection). The JDK's com.sun.net.httpserver is not on the
     * Android unit-test compile classpath, so the test speaks HTTP over a plain socket.
     */
    private void serve(Socket s) {
        try {
            final InputStream in = s.getInputStream();
            final String requestLine = readLine(in); // "GET /a HTTP/1.1"
            final String path = requestLine.split(" ")[1];
            String range = null;
            String line;
            while (!(line = readLine(in)).isEmpty()) {
                final int colon = line.indexOf(':');
                if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("Range")) {
                    range = line.substring(colon + 1).trim();
                }
            }
            log.add(path + "|" + (range == null ? "-" : range));
            if (beforeHeaders != null) beforeHeaders.run();
            final OutputStream o = s.getOutputStream();
            if (notFound.contains(path)) {
                o.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                o.flush();
                return;
            }
            byte[] body = data;
            if (oversized.contains(path)) body = java.util.Arrays.copyOf(data, data.length + 100_000);
            if (corrupt.contains(path)) {
                body = data.clone();
                body[100] ^= 0x55;
            }
            int from = 0;
            String status = "200 OK";
            String extra = "";
            if (range != null && !ignoreRange.contains(path)) {
                from = Integer.parseInt(range.substring("bytes=".length(), range.indexOf('-')));
                status = "206 Partial Content";
                extra = "Content-Range: bytes " + from + "-" + (body.length - 1) + "/" + body.length + "\r\n";
                if (wrongRange.contains(path)) extra = "Content-Range: bytes 0-" + (body.length - 1) + "/" + body.length + "\r\n";
            }
            final int len = body.length - from;
            o.write(("HTTP/1.1 " + status + "\r\nContent-Length: " + len + "\r\n" + extra
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            if (dropHalfway.contains(path)) {
                o.write(body, from, len / 2); // fewer bytes than announced: the client sees an early end
                o.flush();
                return;
            }
            o.write(body, from, len);
            o.flush();
        } catch (IOException ignored) {
            // the client hung up (e.g. after a cancel)
        } finally {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private ModelManifest.Pack pack(String... sourceIdsAndPaths) {
        ModelManifest.Pack p = new ModelManifest.Pack();
        p.id = "t";
        p.version = "1";
        p.kind = "ocr";
        ModelManifest.FileEntry f = new ModelManifest.FileEntry();
        f.path = "model.bin";
        f.size = data.length;
        f.sha256 = sha;
        f.sources = new ArrayList<>();
        for (int i = 0; i < sourceIdsAndPaths.length; i += 2) {
            ModelManifest.Source s = new ModelManifest.Source();
            s.id = sourceIdsAndPaths[i];
            s.url = base + sourceIdsAndPaths[i + 1];
            f.sources.add(s);
        }
        p.files = new ArrayList<>();
        p.files.add(f);
        return p;
    }

    private ModelDownloader downloader(ModelStore store, boolean wifiOnly, final boolean metered) {
        return new ModelDownloader(store, "auto", wifiOnly, new ModelDownloader.Network() {
            @Override public boolean isMetered() { return metered; }
        });
    }

    private static void expectReason(ModelDownloader d, ModelManifest.Pack p, boolean allowMetered, ModelDownloader.Reason r) {
        try {
            d.download(p, allowMetered, null, null);
            fail("expected " + r);
        } catch (ModelDownloader.DownloadException e) {
            assertEquals(r, e.reason);
        }
    }

    @Test public void downloadsVerifiesAndInstalls() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        final List<Long> seen = new ArrayList<>();
        downloader(store, false, false).download(p, false, new ModelDownloader.Listener() {
            @Override public void onProgress(long done, long total) { seen.add(done); }
        }, null);
        assertTrue(store.isInstalled(p));
        assertTrue(java.util.Arrays.equals(data, Files.readAllBytes(store.file(p, "model.bin").toPath())));
        assertEquals(Long.valueOf(data.length), seen.get(seen.size() - 1));
        assertEquals(0, store.partialBytes(p));
    }

    @Test public void anInstalledPackIsNotDownloadedAgain() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        ModelDownloader d = downloader(store, true, true);
        d.download(p, true, null, null);
        int requests = log.size();
        d.download(p, false, null, null); // would throw NEEDS_UNMETERED if it tried to download
        assertEquals(requests, log.size());
    }

    @Test public void fallsBackToTheNextSourceAndResumesWithRange() throws Exception {
        dropHalfway.add("/a");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a", "hf-mirror", "/b");
        downloader(store, false, false).download(p, false, null, null);
        assertTrue(store.isInstalled(p));
        // the second source was asked to continue after the bytes the first one delivered
        String second = log.get(log.size() - 1);
        assertTrue(second, second.startsWith("/b|bytes="));
        assertFalse(second, second.equals("/b|bytes=0-"));
    }

    @Test public void restartsWhenTheServerIgnoresRange() throws Exception {
        dropHalfway.add("/a");
        ignoreRange.add("/b");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a", "hf-mirror", "/b");
        downloader(store, false, false).download(p, false, null, null);
        assertTrue(store.isInstalled(p));
        assertTrue(java.util.Arrays.equals(data, Files.readAllBytes(store.file(p, "model.bin").toPath())));
    }

    @Test public void resumesAcrossRunsFromAPartFile() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        File part = new File(store.stagingDir(p), "model.bin.part");
        part.getParentFile().mkdirs();
        Files.write(part.toPath(), java.util.Arrays.copyOf(data, 120_000));
        downloader(store, false, false).download(p, false, null, null);
        assertTrue(store.isInstalled(p));
        assertEquals("/a|bytes=120000-", log.get(0));
    }

    @Test public void skipsUnavailableSources() throws Exception {
        notFound.add("/a");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        downloader(store, false, false).download(pack("modelscope", "/a", "hf", "/b"), false, null, null);
        assertTrue(store.isInstalled(pack("modelscope", "/a", "hf", "/b")));
    }

    @Test public void failsWithTheListOfTriedSourcesWhenAllAreDown() throws Exception {
        notFound.add("/a");
        notFound.add("/b");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        try {
            downloader(store, false, false).download(pack("modelscope", "/a", "hf", "/b"), false, null, null);
            fail();
        } catch (ModelDownloader.DownloadException e) {
            assertEquals(ModelDownloader.Reason.ALL_SOURCES_FAILED, e.reason);
            assertEquals(java.util.Arrays.asList("modelscope", "hf"), e.tried);
        }
    }

    @Test public void corruptDataIsRejectedAndNeverInstalled() throws Exception {
        corrupt.add("/a");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        expectReason(downloader(store, false, false), p, false, ModelDownloader.Reason.VERIFY_FAILED);
        assertFalse(store.isInstalled(p));
        assertFalse(new File(store.stagingDir(p), "model.bin.part").exists());
    }

    @Test public void aBadMirrorIsSkippedWhenAnotherOneIsGood() throws Exception {
        corrupt.add("/a");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a", "hf", "/b");
        downloader(store, false, false).download(p, false, null, null);
        assertTrue(store.isInstalled(p));
    }

    @Test public void wifiOnlyBlocksMeteredUntilTheUserAllowsIt() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        ModelDownloader d = downloader(store, true, true);
        expectReason(d, p, false, ModelDownloader.Reason.NEEDS_UNMETERED);
        assertTrue(log.isEmpty());
        d.download(p, true, null, null);
        assertTrue(store.isInstalled(p));
    }

    @Test public void notEnoughSpaceIsReportedBeforeAnyRequest() throws Exception {
        ModelStore tiny = new ModelStore(tmp.newFolder("m")) {
            @Override public long usableSpace() { return 1000; }
        };
        expectReason(downloader(tiny, false, false), pack("modelscope", "/a"), false, ModelDownloader.Reason.NO_SPACE);
        assertTrue(log.isEmpty());
    }

    @Test public void cancelStopsAndKeepsThePartFile() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        final boolean[] cancelled = {false};
        try {
            downloader(store, false, false).download(p, false, new ModelDownloader.Listener() {
                @Override public void onProgress(long done, long total) { cancelled[0] = true; }
            }, new ModelDownloader.Cancel() {
                @Override public boolean isCancelled() { return cancelled[0]; }
            });
            fail("expected cancel");
        } catch (ModelDownloader.DownloadException e) {
            assertEquals(ModelDownloader.Reason.CANCELLED, e.reason);
        }
        assertFalse(store.isInstalled(p));
        assertTrue(new File(store.stagingDir(p), "model.bin.part").length() > 0);
    }

    @Test public void switchingToMeteredStopsWithoutTryingOtherSources() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a", "hf", "/b");
        final boolean[] metered = {false};
        ModelDownloader d = new ModelDownloader(store, "auto", true, () -> metered[0]);
        try {
            d.download(p, false, (done, total) -> metered[0] = true, null);
            fail("must stop when Wi-Fi becomes metered");
        } catch (ModelDownloader.DownloadException e) {
            assertEquals(ModelDownloader.Reason.NEEDS_UNMETERED, e.reason);
        }
        assertFalse(store.isInstalled(p));
        assertEquals(1, log.size());
        assertTrue(store.partialBytes(p) > 0);
    }

    @Test public void cancellationBeforeStartNeverCreatesInstalledPack() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        // All files may already be present after an interrupted verification.
        File staged = new File(store.stagingDir(p), "model.bin");
        staged.getParentFile().mkdirs();
        Files.write(staged.toPath(), data);
        try {
            downloader(store, false, false).download(p, false, null, () -> true);
            fail("cancelled job must not install staged files");
        } catch (ModelDownloader.DownloadException e) {
            assertEquals(ModelDownloader.Reason.CANCELLED, e.reason);
        }
        assertFalse(store.isInstalled(p));
    }

    @Test public void rejectsOversizedResponseWithoutGrowingPartPastManifestSize() throws Exception {
        oversized.add("/a");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        expectReason(downloader(store, false, false), p, false, ModelDownloader.Reason.VERIFY_FAILED);
        // A dishonest server must not fill the disk beyond the model's declared length.
        assertTrue(store.partialBytes(p) <= p.totalBytes());
        assertFalse(store.isInstalled(p));
    }

    @Test public void rejectsMismatchedResumeRangeBeforeAppending() throws Exception {
        wrongRange.add("/a");
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        File part = new File(store.stagingDir(p), "model.bin.part");
        part.getParentFile().mkdirs();
        Files.write(part.toPath(), java.util.Arrays.copyOf(data, 120_000));
        expectReason(downloader(store, false, false), p, false, ModelDownloader.Reason.ALL_SOURCES_FAILED);
        assertEquals(120_000, part.length());
        assertFalse(store.isInstalled(p));
    }
    @Test public void cancelWhileWaitingForHeadersKeepsExistingPartial() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        File part = new File(store.stagingDir(p), "model.bin.part");
        part.getParentFile().mkdirs();
        Files.write(part.toPath(), java.util.Arrays.copyOf(data, 120_000));
        ignoreRange.add("/a");
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        beforeHeaders = () -> cancelled.set(true);
        try { downloader(store, false, false).download(p, false, null, cancelled::get); fail(); }
        catch (ModelDownloader.DownloadException e) { assertEquals(ModelDownloader.Reason.CANCELLED, e.reason); }
        assertEquals(120_000, part.length());
    }
    @Test public void corruptPrefixRetriesHealthySourceFromZero() throws Exception {
        ModelStore store = new ModelStore(tmp.newFolder("m"));
        ModelManifest.Pack p = pack("modelscope", "/a");
        File part = new File(store.stagingDir(p), "model.bin.part");
        part.getParentFile().mkdirs();
        Files.write(part.toPath(), new byte[120_000]);
        downloader(store, false, false).download(p, false, null, null);
        assertTrue(store.isInstalled(p));
        assertEquals(2, log.size());
    }

}
