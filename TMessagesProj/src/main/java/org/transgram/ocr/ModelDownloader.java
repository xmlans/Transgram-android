package org.transgram.ocr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads one model pack into its staging directory, file by file: sources are tried in order, an interrupted file
 * resumes with a Range request (the .part file survives restarts), every file is checked against its SHA-256, and the
 * staging directory is renamed into place only when the whole pack is verified. Blocking: run it on a worker thread.
 */
public final class ModelDownloader {
    public enum Reason { NEEDS_UNMETERED, NO_SPACE, VERIFY_FAILED, ALL_SOURCES_FAILED, CANCELLED }

    public static final class DownloadException extends IOException {
        public final Reason reason;
        /** Ids of the sources that were tried (for "tried: modelscope, hf-mirror" in the error message). */
        public final List<String> tried;

        DownloadException(Reason reason, String message, List<String> tried) {
            super(message);
            this.reason = reason;
            this.tried = tried;
        }
    }

    public interface Network {
        boolean isMetered();
    }

    public interface Listener {
        void onProgress(long doneBytes, long totalBytes);
    }

    public interface Cancel {
        boolean isCancelled();
    }

    /** Extra room kept free after the download. */
    static final long SPACE_MARGIN = 32L << 20;
    private static final int BUFFER = 64 * 1024;
    private static final long PROGRESS_EVERY_BYTES = 256 * 1024;

    private final ModelStore store;
    private final String preferredSource;
    private final boolean wifiOnly;
    private final Network network;

    /**
     * @param preferredSource source id or "auto" (see ModelManifest.orderedSources)
     * @param wifiOnly        refuse to start on a metered connection unless allowMetered is passed to download()
     */
    public ModelDownloader(ModelStore store, String preferredSource, boolean wifiOnly, Network network) {
        this.store = store;
        this.preferredSource = preferredSource;
        this.wifiOnly = wifiOnly;
        this.network = network;
    }

    public void download(ModelManifest.Pack pack, boolean allowMetered, Listener listener, Cancel cancel) throws DownloadException {
        final List<String> tried = new ArrayList<>();
        if (store.isInstalled(pack)) {
            if (listener != null) listener.onProgress(pack.totalBytes(), pack.totalBytes());
            return;
        }
        checkPolicy(allowMetered, cancel, tried);
        if (wifiOnly && !allowMetered && network.isMetered()) {
            throw new DownloadException(Reason.NEEDS_UNMETERED, "metered network", tried);
        }
        final File staging = store.stagingDir(pack);
        staging.mkdirs();
        final long missing = pack.totalBytes() - store.partialBytes(pack);
        if (store.usableSpace() < missing + SPACE_MARGIN) {
            throw new DownloadException(Reason.NO_SPACE, "need " + (missing + SPACE_MARGIN) + " bytes", tried);
        }
        final long total = pack.totalBytes();
        long base = 0;
        for (ModelManifest.FileEntry f : pack.files) {
            checkPolicy(allowMetered, cancel, tried);
            downloadFile(f, new File(staging, f.path), base, total, listener, cancel, tried, allowMetered);
            base += f.size;
        }
        // all files verified: move the staging directory into place
        checkPolicy(allowMetered, cancel, tried);
        final File finalDir = store.packDir(pack);
        ModelStore.deleteRecursively(finalDir);
        finalDir.getParentFile().mkdirs();
        if (!staging.renameTo(finalDir)) {
            throw new DownloadException(Reason.ALL_SOURCES_FAILED, "could not move " + staging + " to " + finalDir, tried);
        }
        try {
            new File(finalDir, ModelStore.READY).createNewFile();
        } catch (IOException e) {
            throw new DownloadException(Reason.ALL_SOURCES_FAILED, "could not write ready marker: " + e, tried);
        }
        store.pruneOldVersions(pack);
        if (listener != null) listener.onProgress(total, total);
    }

    private void downloadFile(ModelManifest.FileEntry f, File target, long base, long total, Listener listener,
                              Cancel cancel, List<String> tried, boolean allowMetered) throws DownloadException {
        target.getParentFile().mkdirs();
        // a file finished in an earlier run
        if (target.isFile() && target.length() == f.size && sha256(target).equals(f.sha256)) return;
        target.delete();
        final File part = new File(target.getPath() + ".part");
        Reason lastReason = Reason.ALL_SOURCES_FAILED;
        if (part.length() == f.size && !sha256(part).equals(f.sha256)) part.delete();
        for (ModelManifest.Source s : ModelManifest.orderedSources(f, preferredSource)) {
            for (int attempt = 0; attempt < 2; attempt++) {
                checkPolicy(allowMetered, cancel, tried);
                if (!tried.contains(s.id)) tried.add(s.id);
                boolean resumed = part.length() > 0;
                try {
                    fetch(s.url, part, f.size, base, total, listener, cancel, allowMetered, tried);
                } catch (DownloadException e) { throw e;
                } catch (OversizedIOException e) {
                    part.delete(); lastReason = Reason.VERIFY_FAILED; break;
                } catch (IOException e) {
                    checkPolicy(allowMetered, cancel, tried);
                    lastReason = Reason.ALL_SOURCES_FAILED; break;
                }
                checkPolicy(allowMetered, cancel, tried);
                if (part.length() == f.size && sha256(part).equals(f.sha256)) {
                    if (!part.renameTo(target)) throw new DownloadException(Reason.ALL_SOURCES_FAILED, "rename failed for " + f.path, tried);
                    return;
                }
                part.delete();
                lastReason = Reason.VERIFY_FAILED;
                if (!resumed) break;
            }
        }
        throw new DownloadException(lastReason, "all sources failed for " + f.path, tried);
    }

    private static final class CancelledIOException extends IOException {
    }

    private static final class OversizedIOException extends IOException {}

    private void checkPolicy(boolean allowMetered, Cancel cancel, List<String> tried) throws DownloadException {
        if (cancel != null && cancel.isCancelled()) throw new DownloadException(Reason.CANCELLED, "cancelled", tried);
        if (wifiOnly && !allowMetered && network.isMetered())
            throw new DownloadException(Reason.NEEDS_UNMETERED, "metered network", tried);
    }

    /** Appends the remote file to `part` (resuming from its current length) until it has `size` bytes. */
    private void fetch(String url, File part, long size, long base, long total, Listener listener, Cancel cancel,
                       boolean allowMetered, List<String> tried) throws IOException {
        long have = part.exists() ? part.length() : 0;
        if (have > size) {
            part.delete();
            have = 0;
        }
        if (have == size) return;
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "TransGram");
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            final int code = c.getResponseCode();
            final boolean append;
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                String range = c.getHeaderField("Content-Range");
                java.util.regex.Matcher match = java.util.regex.Pattern.compile("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matcher(range == null ? "" : range);
                try {
                    if (!match.matches() || Long.parseLong(match.group(1)) != have
                            || Long.parseLong(match.group(2)) != size - 1
                            || Long.parseLong(match.group(3)) != size) throw new IOException("invalid resume range");
                } catch (NumberFormatException e) { throw new IOException("invalid resume range", e); }
                append = true;
            } else if (code == HttpURLConnection.HTTP_OK) {
                append = false; // the server ignored the Range header: start over
                have = 0;
            } else {
                throw new IOException("HTTP " + code);
            }
            checkPolicy(allowMetered, cancel, tried);
            long done = have;
            long lastReported = done;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                final byte[] buf = new byte[BUFFER];
                int n;
                while ((n = in.read(buf)) > 0) {
                    checkPolicy(allowMetered, cancel, tried);
                    if (n > size - done) throw new OversizedIOException();
                    out.write(buf, 0, n);
                    done += n;
                    if (listener != null && done - lastReported >= PROGRESS_EVERY_BYTES) {
                        lastReported = done;
                        listener.onProgress(base + Math.min(done, size), total);
                    }
                }
            }
            if (done < size) throw new IOException("connection ended early at " + done + " of " + size);
            checkPolicy(allowMetered, cancel, tried);
        } finally {
            c.disconnect();
        }
    }

    static String sha256(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] buf = new byte[BUFFER];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            final StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
