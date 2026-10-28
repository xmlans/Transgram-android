package org.transgram.ocr;

import android.graphics.Bitmap;
import android.graphics.Matrix;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.transgram.ocr.OcrPostProcess.Box;

/**
 * PP-OCRv5 mobile with ONNX Runtime: text detection (DB), text-line direction (0 / 180 degrees) and recognition (CTC).
 * Pack files: det.onnx, cls.onnx, rec.onnx, dict.txt. Not thread-safe; RecognitionService runs one job at a time.
 * Needs API 24+ (onnxruntime-android); callers check RecognitionHub.isSupported() first.
 */
public final class OcrEngine {
    public static final class Result {
        public final String text;
        public final List<Box> boxes;

        Result(String text, List<Box> boxes) {
            this.text = text;
            this.boxes = boxes;
        }
    }

    /** PaddleOCR models were trained on BGR input. Only flipped by hand when comparing against RGB. */
    static volatile boolean feedBgr = true;

    private static final int MAX_BOXES = 150;
    private static final int REC_MAX_WIDTH = 1600;
    private static final float REC_DROP_SCORE = 0.5f;
    private static final float CLS_ROTATE_ABOVE = 0.9f;

    private final File dir;
    private final OrtEnvironment env = OrtEnvironment.getEnvironment();
    private final String[] charset;
    private OrtSession det, cls, rec;

    public OcrEngine(File packDir) throws IOException {
        this.dir = packDir;
        // java.nio.file is API 26+ and not desugared here, so read with java.io
        final String dict = new String(readAll(new File(packDir, "dict.txt")), StandardCharsets.UTF_8);
        final List<String> lines = new ArrayList<>(Arrays.asList(dict.split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1); // trailing newline
        this.charset = OcrPostProcess.buildCharset(lines);
    }

    private static byte[] readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(16, f.length()));
            final byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    private OrtSession open(String file) throws OrtException {
        final OrtSession.SessionOptions o = new OrtSession.SessionOptions();
        o.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors() >= 8 ? 4 : 2);
        o.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        return env.createSession(new File(dir, file).getAbsolutePath(), o);
    }

    /** @throws CancellationException when `cancelled` became true */
    public Result recognize(Bitmap src, AtomicBoolean cancelled) throws OrtException {
        if (det == null) det = open("det.onnx");
        if (cls == null) cls = open("cls.onnx");
        if (rec == null) rec = open("rec.onnx");
        final int sw = src.getWidth(), sh = src.getHeight();

        // 1. detection on a copy whose sides are multiples of 32
        final int[] ds = OcrPostProcess.detSize(sw, sh, OcrPostProcess.DET_MAX_SIDE);
        final Bitmap small = Bitmap.createScaledBitmap(src, ds[0], ds[1], true);
        final float[] prob = runDet(tensor(small, ImageTensor.DET_MEAN, ImageTensor.DET_STD), ds[0], ds[1]);
        if (small != src) small.recycle();
        final List<Box> mapBoxes = OcrPostProcess.detBoxes(prob, ds[0], ds[1], OcrPostProcess.DET_THRESH,
                OcrPostProcess.DET_BOX_THRESH, OcrPostProcess.DET_UNCLIP_RATIO, OcrPostProcess.DET_MIN_SIDE);
        final List<Box> boxes = new ArrayList<>();
        for (Box b : mapBoxes) boxes.add(OcrPostProcess.toSource(b, (float) sw / ds[0], (float) sh / ds[1], sw, sh));
        List<Box> ordered = OcrPostProcess.sortReadingOrder(boxes);
        if (ordered.size() > MAX_BOXES) ordered = new ArrayList<>(ordered.subList(0, MAX_BOXES));

        // 2. direction + recognition for every box, in reading order
        for (Box b : ordered) {
            if (cancelled.get()) throw new CancellationException();
            final int x = Math.max(0, (int) b.x0), y = Math.max(0, (int) b.y0);
            final int w = Math.min(sw - x, Math.max(1, (int) (b.x1 - b.x0)));
            final int h = Math.min(sh - y, Math.max(1, (int) (b.y1 - b.y0)));
            if (w < 2 || h < 2) continue;
            Bitmap crop = Bitmap.createBitmap(src, x, y, w, h);
            try {
                if (h >= 1.5f * w) crop = rotate(crop, -90f, src);     // vertical text, like PaddleOCR's rot90
                if (needsFlip(crop)) crop = rotate(crop, 180f, src);
                final OcrPostProcess.Decoded d = recognizeLine(crop);
                b.text = d.score >= REC_DROP_SCORE ? d.text : null;
            } finally {
                if (crop != src) crop.recycle();
            }
        }
        return new Result(OcrPostProcess.mergeLines(ordered), ordered);
    }

    /** Rotates b; recycles b unless it is `keep` (the caller's source bitmap). */
    private static Bitmap rotate(Bitmap b, float degrees, Bitmap keep) {
        final Matrix m = new Matrix();
        m.postRotate(degrees);
        final Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b && b != keep) b.recycle();
        return r;
    }

    private static float[] tensor(Bitmap bmp, float[] mean, float[] std) {
        final int w = bmp.getWidth(), h = bmp.getHeight();
        final int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        return ImageTensor.fromArgb(px, w, h, feedBgr, mean, std);
    }

    private float[] runDet(float[] chw, int w, int h) throws OrtException {
        final String in = det.getInputNames().iterator().next();
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, h, w});
             OrtSession.Result r = det.run(Collections.singletonMap(in, t))) {
            final float[][][][] a = (float[][][][]) r.get(0).getValue(); // [1][1][h][w]
            final float[] out = new float[w * h];
            for (int y = 0; y < h; y++) System.arraycopy(a[0][0][y], 0, out, y * w, w);
            return out;
        }
    }

    /** Input height / width of a model, read from the model itself (fallbacks when the dimension is dynamic). */
    private static int[] inputHW(OrtSession s, int defH, int defW) throws OrtException {
        final String name = s.getInputNames().iterator().next();
        final long[] shape = ((TensorInfo) s.getInputInfo().get(name).getInfo()).getShape(); // [N, 3, H, W]
        return new int[]{
                shape.length > 2 && shape[2] > 0 ? (int) shape[2] : defH,
                shape.length > 3 && shape[3] > 0 ? (int) shape[3] : defW};
    }

    private boolean needsFlip(Bitmap crop) throws OrtException {
        final int[] hw = inputHW(cls, 80, 160);
        final int rw = Math.min(hw[1], (int) Math.ceil((double) hw[0] * crop.getWidth() / Math.max(1, crop.getHeight())));
        final Bitmap scaled = Bitmap.createScaledBitmap(crop, Math.max(1, rw), hw[0], true);
        final float[] chw = ImageTensor.padWidth(tensor(scaled, ImageTensor.HALF, ImageTensor.HALF), scaled.getWidth(), hw[0], hw[1]);
        if (scaled != crop) scaled.recycle();
        final String in = cls.getInputNames().iterator().next();
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, hw[0], hw[1]});
             OrtSession.Result r = cls.run(Collections.singletonMap(in, t))) {
            final float[][] p = (float[][]) r.get(0).getValue();      // [1][2]: 0 = upright, 1 = upside down
            return p[0].length > 1 && p[0][1] > CLS_ROTATE_ABOVE;
        }
    }

    private OcrPostProcess.Decoded recognizeLine(Bitmap crop) throws OrtException {
        final int[] hw = inputHW(rec, 48, -1);
        final int rw = ImageTensor.recWidth(crop.getWidth(), crop.getHeight(), hw[0], REC_MAX_WIDTH);
        final Bitmap scaled = Bitmap.createScaledBitmap(crop, rw, hw[0], true);
        final float[] chw = tensor(scaled, ImageTensor.HALF, ImageTensor.HALF);
        if (scaled != crop) scaled.recycle();
        final String in = rec.getInputNames().iterator().next();
        try (OnnxTensor t = OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), new long[]{1, 3, hw[0], rw});
             OrtSession.Result r = rec.run(Collections.singletonMap(in, t))) {
            final float[][][] a = (float[][][]) r.get(0).getValue();  // [1][T][C]
            final int steps = a[0].length, classes = a[0][0].length;
            if (classes != charset.length) {
                // dictionary lines + blank + space must equal the model's class count
                throw new IllegalStateException("recognition model has " + classes + " classes, dictionary gives " + charset.length);
            }
            final float[] flat = new float[steps * classes];
            for (int s = 0; s < steps; s++) System.arraycopy(a[0][s], 0, flat, s * classes, classes);
            return OcrPostProcess.ctcGreedy(flat, steps, classes, charset);
        }
    }

    public void close() {
        for (OrtSession s : new OrtSession[]{det, cls, rec}) {
            if (s != null) {
                try {
                    s.close();
                } catch (OrtException ignored) {
                    // nothing useful to do while shutting down
                }
            }
        }
        det = cls = rec = null;
    }
}
