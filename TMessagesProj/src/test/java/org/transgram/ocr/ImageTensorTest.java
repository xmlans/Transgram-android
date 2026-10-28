package org.transgram.ocr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageTensorTest {
    private static final float[] ZERO = {0f, 0f, 0f};
    private static final float[] ONE = {1f, 1f, 1f};

    @Test public void rgbPlanesAreChannelMajor() {
        // two pixels: (R16,G32,B48) and (R255,G0,B128)
        int[] px = {0xFF102030, 0xFFFF0080};
        float[] t = ImageTensor.fromArgb(px, 2, 1, false, ZERO, ONE);
        assertEquals(6, t.length);
        assertEquals(16 / 255f, t[0], 1e-6f);
        assertEquals(255 / 255f, t[1], 1e-6f);
        assertEquals(32 / 255f, t[2], 1e-6f);
        assertEquals(0f, t[3], 1e-6f);
        assertEquals(48 / 255f, t[4], 1e-6f);
        assertEquals(128 / 255f, t[5], 1e-6f);
    }

    @Test public void bgrSwapsFirstAndLastPlane() {
        float[] t = ImageTensor.fromArgb(new int[]{0xFF102030}, 1, 1, true, ZERO, ONE);
        assertEquals(48 / 255f, t[0], 1e-6f);
        assertEquals(32 / 255f, t[1], 1e-6f);
        assertEquals(16 / 255f, t[2], 1e-6f);
    }

    @Test public void normalisationUsesMeanAndStdPerOutputChannel() {
        float[] t = ImageTensor.fromArgb(new int[]{0xFFFFFFFF}, 1, 1, false, ImageTensor.HALF, ImageTensor.HALF);
        for (float v : t) assertEquals(1f, v, 1e-6f);                       // (1 - 0.5) / 0.5
        float[] black = ImageTensor.fromArgb(new int[]{0xFF000000}, 1, 1, false, ImageTensor.HALF, ImageTensor.HALF);
        for (float v : black) assertEquals(-1f, v, 1e-6f);
        float[] det = ImageTensor.fromArgb(new int[]{0xFF000000}, 1, 1, true, ImageTensor.DET_MEAN, ImageTensor.DET_STD);
        assertEquals(-0.485f / 0.229f, det[0], 1e-5f);
        assertEquals(-0.406f / 0.225f, det[2], 1e-5f);
    }

    @Test public void padWidthKeepsRowsAndFillsZeros() {
        float[] chw = new float[3 * 2 * 2];   // 3 channels, h = 2, w = 2 -> w = 4
        for (int i = 0; i < chw.length; i++) chw[i] = i + 1;
        float[] p = ImageTensor.padWidth(chw, 2, 2, 4);
        assertEquals(3 * 2 * 4, p.length);
        assertEquals(1f, p[0], 0f);
        assertEquals(2f, p[1], 0f);
        assertEquals(0f, p[2], 0f);
        assertEquals(3f, p[4], 0f);           // channel 0, row 1
        assertEquals(5f, p[8], 0f);           // channel 1, row 0
        assertTrue(chw == ImageTensor.padWidth(chw, 2, 2, 2));
    }

    @Test public void recWidthKeepsAspectWithLimits() {
        assertEquals(480, ImageTensor.recWidth(300, 30, 48, 1600));   // 10:1 -> 480
        assertEquals(16, ImageTensor.recWidth(1, 100, 48, 1600));     // very narrow
        assertEquals(1600, ImageTensor.recWidth(5000, 20, 48, 1600)); // very wide
    }
}
