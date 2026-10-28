package org.transgram.ocr;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;

import androidx.core.net.ConnectivityManagerCompat;

import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Android glue: where the models live, the bundled manifest, the metered-network check. */
public final class RecognitionEnv {
    /** Id of the only model pack in assets/models/manifest.json. */
    public static final String OCR_PACK = "ocr-ppocrv5-mobile";

    private static volatile ModelManifest manifest;
    private static volatile ModelStore store;

    private RecognitionEnv() {}

    public static ModelStore store() {
        ModelStore s = store;
        if (s == null) {
            s = new ModelStore(ApplicationLoader.getFilesDirFixed("models"));
            store = s;
        }
        return s;
    }

    public static ModelManifest manifest() throws ModelManifest.ManifestException {
        ModelManifest m = manifest;
        if (m == null) {
            m = ModelManifest.parse(readAsset("models/manifest.json"));
            manifest = m;
        }
        return m;
    }

    /** The OCR pack from the bundled manifest. */
    public static ModelManifest.Pack ocrPack() throws ModelManifest.ManifestException {
        final ModelManifest.Pack p = manifest().pack(OCR_PACK);
        if (p == null) throw new ModelManifest.ManifestException("manifest has no pack " + OCR_PACK);
        return p;
    }

    public static String readAsset(String path) throws ModelManifest.ManifestException {
        final AssetManager am = ApplicationLoader.applicationContext.getAssets();
        try (InputStream in = am.open(path)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ModelManifest.ManifestException("cannot read asset " + path, e);
        }
    }

    public static ModelDownloader.Network network() {
        return new ModelDownloader.Network() {
            @Override public boolean isMetered() {
                final ConnectivityManager cm = (ConnectivityManager) ApplicationLoader.applicationContext
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                return cm == null || ConnectivityManagerCompat.isActiveNetworkMetered(cm);
            }
        };
    }

    public static ModelDownloader downloader() {
        return new ModelDownloader(store(), RecognitionSettings.source(), RecognitionSettings.wifiOnly(), network());
    }
}
