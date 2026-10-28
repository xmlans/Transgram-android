package org.transgram.ocr;

import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.io.File;

/** The one RecognitionService and result store of the app. */
public final class RecognitionHub {
    /** onnxruntime-android 1.28 needs Android 7.0 (API 24); the app itself still installs on API 23. */
    public static final int MIN_SDK = 24;
    private static final long IDLE_RELEASE_MS = 60_000;
    private static RecognitionService service;
    private static RecognitionResultStore results;

    private RecognitionHub() {}

    /** False below API 24: hide every OCR entry point (screen-reader action, settings group). */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= MIN_SDK;
    }

    public static synchronized RecognitionResultStore results() {
        if (results == null) {
            results = new RecognitionResultStore(new File(ApplicationLoader.getFilesDirFixed(), "transgram_ocr"),
                    RecognitionResultStore.DEFAULT_MAX_FILES);
        }
        return results;
    }

    public static synchronized RecognitionService service() {
        if (service == null) {
            service = new RecognitionService(new AndroidEngines(), results(), new RecognitionService.Poster() {
                @Override public void post(Runnable r) {
                    AndroidUtilities.runOnUIThread(r);
                }
            }, IDLE_RELEASE_MS);
        }
        return service;
    }
}
