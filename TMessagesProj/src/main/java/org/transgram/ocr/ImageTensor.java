package org.transgram.ocr;

/** ARGB pixels to a planar (CHW) float tensor with per-channel normalisation. Pure Java, unit-tested. */
public final class ImageTensor {
    private ImageTensor() {}

    /** PaddleOCR detector normalisation (applied to BGR pixels, as PaddleOCR does). */
    public static final float[] DET_MEAN = {0.485f, 0.456f, 0.406f};
    public static final float[] DET_STD = {0.229f, 0.224f, 0.225f};
    /** Recognition and direction classifier: (x/255 - 0.5) / 0.5. */
    public static final float[] HALF = {0.5f, 0.5f, 0.5f};

    /**
     * @param bgr  true: output channel 0 = blue, 1 = green, 2 = red; false: R, G, B
     * @return float[3 * h * w], channel-major; value = (pixel / 255 - mean[c]) / std[c]
     */
    public static float[] fromArgb(int[] argb, int w, int h, boolean bgr, float[] mean, float[] std) {
        final int plane = w * h;
        final float[] out = new float[3 * plane];
        for (int i = 0; i < plane; i++) {
            final int p = argb[i];
            final int r = (p >> 16) & 0xff, g = (p >> 8) & 0xff, b = p & 0xff;
            final int c0 = bgr ? b : r, c2 = bgr ? r : b;
            out[i] = (c0 / 255f - mean[0]) / std[0];
            out[plane + i] = (g / 255f - mean[1]) / std[1];
            out[2 * plane + i] = (c2 / 255f - mean[2]) / std[2];
        }
        return out;
    }

    /** Pads a CHW tensor on the right with zeros (zero = mid-grey after normalisation), like PaddleOCR's classifier input. */
    public static float[] padWidth(float[] chw, int w, int h, int newW) {
        if (newW <= w) return chw;
        final float[] out = new float[3 * h * newW];
        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < h; y++) {
                System.arraycopy(chw, (c * h + y) * w, out, (c * h + y) * newW, w);
            }
        }
        return out;
    }

    /** Width for the recogniser input: height-48 scaling of the crop's aspect ratio, at least 16 and at most maxW. */
    public static int recWidth(int cropW, int cropH, int targetH, int maxW) {
        final int w = (int) Math.ceil((double) targetH * cropW / Math.max(1, cropH));
        return Math.max(16, Math.min(maxW, w));
    }
}
