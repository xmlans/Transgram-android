package org.transgram.ocr;

import android.graphics.Bitmap;
import android.os.Build;

import ai.onnxruntime.OrtException;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Connects RecognitionService to the real OCR engine, translating every failure into a RecognitionError. */
final class AndroidEngines implements RecognitionService.Engines {
    private static final int OCR_MAX_SIDE = 2560;

    private OcrEngine ocr;

    private static ModelManifest.Pack installedOcrPack() throws RecognitionException {
        final ModelManifest.Pack pack;
        try {
            pack = RecognitionEnv.ocrPack();
        } catch (ModelManifest.ManifestException e) {
            throw new RecognitionException(RecognitionError.ENGINE_FAILED, e);
        }
        if (!RecognitionEnv.store().isInstalled(pack)) {
            throw new RecognitionException(RecognitionError.MODEL_MISSING, pack.id, null);
        }
        return pack;
    }

    @Override public synchronized String ocr(String path, AtomicBoolean cancelled) throws RecognitionException {
        // onnxruntime-android needs API 24; never touch OcrEngine (and its native library) below that
        if (Build.VERSION.SDK_INT < RecognitionHub.MIN_SDK) throw new RecognitionException(RecognitionError.UNSUPPORTED, null);
        final ModelManifest.Pack pack = installedOcrPack();
        final Bitmap bitmap;
        try {
            bitmap = ImageLoader.load(path, OCR_MAX_SIDE);
        } catch (IOException e) {
            throw new RecognitionException(RecognitionError.DECODE_FAILED, e);
        }
        try {
            if (ocr == null) ocr = new OcrEngine(RecognitionEnv.store().packDir(pack));
            return ocr.recognize(bitmap, cancelled).text;
        } catch (IOException | OrtException e) {
            throw new RecognitionException(RecognitionError.ENGINE_FAILED, e);
        } finally {
            bitmap.recycle();
        }
    }

    @Override public synchronized void releaseIdle() {
        if (ocr != null) {
            ocr.close();
            ocr = null;
        }
    }
}
