package org.transgram.ocr;

import java.io.File;

/**
 * Where downloaded model packs live: root/&lt;id&gt;/&lt;version&gt;/... once complete, root/.part/&lt;id&gt;/&lt;version&gt;/... while
 * downloading. Only java.io, so it is unit-testable. The root is ApplicationLoader.getFilesDirFixed("models") in the app,
 * which "clear cache" does not touch.
 */
public class ModelStore {
    /** Written into a pack directory after every file has been verified. */
    public static final String READY = ".ready";
    private static final String STAGING = ".part";

    private final File root;

    public ModelStore(File root) {
        this.root = root;
    }

    public File root() {
        return root;
    }

    public File packDir(ModelManifest.Pack p) {
        return new File(new File(root, p.id), p.version);
    }

    public File stagingDir(ModelManifest.Pack p) {
        return new File(new File(new File(root, STAGING), p.id), p.version);
    }

    /** A file of an installed pack (relative path with '/'). */
    public File file(ModelManifest.Pack p, String relativePath) {
        return new File(packDir(p), relativePath);
    }

    public boolean isInstalled(ModelManifest.Pack p) {
        final File dir = packDir(p);
        if (!new File(dir, READY).isFile()) return false;
        for (ModelManifest.FileEntry f : p.files) {
            final File x = new File(dir, f.path);
            if (!x.isFile() || x.length() != f.size) return false;
        }
        return true;
    }

    /** Bytes of an installed pack, 0 when it is not installed. */
    public long installedBytes(ModelManifest.Pack p) {
        return isInstalled(p) ? p.totalBytes() : 0;
    }

    /** Bytes already downloaded into the staging directory (complete and partial files). */
    public long partialBytes(ModelManifest.Pack p) {
        long bytes = 0;
        for (ModelManifest.FileEntry f : p.files) {
            File target = new File(stagingDir(p), f.path);
            bytes += Math.min(f.size, Math.max(target.length(), new File(target + ".part").length()));
        }
        return bytes;
    }

    /** Free space on the volume that holds the models. */
    public long usableSpace() {
        File f = root;
        while (f != null && !f.exists()) f = f.getParentFile();
        return f == null ? 0 : f.getUsableSpace();
    }

    /** Removes the pack (every version) and any half-finished download of it. */
    public void delete(ModelManifest.Pack p) {
        deleteRecursively(new File(root, p.id));
        deleteRecursively(new File(new File(root, STAGING), p.id));
    }

    /** Removes older versions of the same pack after a newer one has been installed. */
    public void pruneOldVersions(ModelManifest.Pack p) {
        final File[] versions = new File(root, p.id).listFiles();
        if (versions == null) return;
        for (File v : versions) if (!v.getName().equals(p.version)) deleteRecursively(v);
    }

    /** Disk used by everything below the root (installed packs and partial downloads). */
    public long totalBytesOnDisk() {
        return sizeOf(root);
    }

    static long sizeOf(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long n = 0;
        final File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) n += sizeOf(k);
        return n;
    }

    static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        final File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteRecursively(k);
        f.delete();
    }
}
