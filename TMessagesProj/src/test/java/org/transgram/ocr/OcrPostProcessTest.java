package org.transgram.ocr;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.transgram.ocr.OcrPostProcess.Box;
import org.transgram.ocr.OcrPostProcess.Decoded;

public class OcrPostProcessTest {
    private static Box box(float x0, float y0, float x1, float y1, String text) {
        Box b = new Box(x0, y0, x1, y1, 0.9f);
        b.text = text;
        return b;
    }

    // ---- detector size ----
    @Test public void detSizeRoundsToMultipleOf32() {
        assertArrayEquals(new int[]{640, 480}, OcrPostProcess.detSize(640, 480, 960));
        assertArrayEquals(new int[]{672, 352}, OcrPostProcess.detSize(670, 350, 960));
    }

    @Test public void detSizeCapsLongSideAndKeepsAspect() {
        int[] s = OcrPostProcess.detSize(1920, 1080, 960);
        assertEquals(960, s[0]);
        assertEquals(544, s[1]); // 540 -> 544
        int[] tall = OcrPostProcess.detSize(1080, 4000, 960);
        assertEquals(960, tall[1]);
        assertEquals(0, tall[0] % 32);
    }

    @Test public void detSizeNeverBelow32() {
        assertArrayEquals(new int[]{32, 32}, OcrPostProcess.detSize(5, 7, 960));
    }

    // ---- DB boxes ----
    private static float[] map(int w, int h) { return new float[w * h]; }

    private static void fill(float[] m, int w, int x0, int y0, int x1, int y1, float v) {
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) m[y * w + x] = v;
    }

    @Test public void detFindsBlobAndUnclips() {
        float[] m = map(64, 64);
        fill(m, 64, 10, 20, 40, 30, 0.9f);   // 31 x 11 text line
        List<Box> boxes = OcrPostProcess.detBoxes(m, 64, 64, 0.3f, 0.6f, 1.5f, 3);
        assertEquals(1, boxes.size());
        Box b = boxes.get(0);
        float d = 31f * 11f * 1.5f / (2f * 42f); // about 6.09
        assertEquals(10 - d, b.x0, 1e-3f);
        assertEquals(20 - d, b.y0, 1e-3f);
        assertEquals(41 + d, b.x1, 1e-3f);
        assertEquals(31 + d, b.y1, 1e-3f);
        assertEquals(0.9f, b.score, 1e-4f);
    }

    @Test public void detDropsTinyAndLowScoreBlobs() {
        float[] m = map(64, 64);
        fill(m, 64, 2, 2, 3, 3, 0.95f);       // 2x2: below min side
        fill(m, 64, 10, 40, 40, 50, 0.45f);   // above thresh, but mean below box thresh
        assertEquals(0, OcrPostProcess.detBoxes(m, 64, 64, 0.3f, 0.6f, 1.5f, 3).size());
    }

    @Test public void detSeparatesTwoBlobsAndClampsToMap() {
        float[] m = map(64, 64);
        fill(m, 64, 0, 0, 20, 8, 0.9f);
        fill(m, 64, 30, 30, 60, 40, 0.9f);
        List<Box> boxes = OcrPostProcess.detBoxes(m, 64, 64, 0.3f, 0.6f, 1.5f, 3);
        assertEquals(2, boxes.size());
        for (Box b : boxes) {
            assertTrue(b.x0 >= 0 && b.y0 >= 0 && b.x1 <= 64 && b.y1 <= 64);
        }
    }

    @Test public void toSourceScalesAndClamps() {
        Box b = OcrPostProcess.toSource(new Box(10, 10, 100, 60, 0.8f), 2f, 2f, 150, 100);
        assertEquals(20f, b.x0, 1e-4f);
        assertEquals(150f, b.x1, 1e-4f);
        assertEquals(100f, b.y1, 1e-4f);
    }

    // ---- reading order ----
    @Test public void readingOrderIsTopToBottomThenLeftToRight() {
        List<Box> in = new ArrayList<>(Arrays.asList(
                box(200, 100, 300, 130, "c"),
                box(10, 12, 90, 40, "a"),
                box(120, 8, 220, 38, "b"),
                box(10, 105, 150, 133, "d")));
        List<Box> out = OcrPostProcess.sortReadingOrder(in);
        assertEquals("a", out.get(0).text);
        assertEquals("b", out.get(1).text);
        assertEquals("d", out.get(2).text);
        assertEquals("c", out.get(3).text);
    }

    @Test public void slightlyTiltedLineStaysOneLine() {
        List<Box> in = Arrays.asList(box(10, 10, 60, 40, "l"), box(70, 16, 120, 46, "r"));
        assertEquals(1, OcrPostProcess.groupLines(in).size());
    }

    // ---- CTC ----
    private static final String[] CHARSET = OcrPostProcess.buildCharset(Arrays.asList("你", "好", "a"));

    private static float[] onehot(int classes, int... picks) {
        float[] m = new float[picks.length * classes];
        for (int t = 0; t < picks.length; t++) m[t * classes + picks[t]] = 0.9f;
        return m;
    }

    @Test public void charsetHasBlankAndSpace() {
        assertEquals(5, CHARSET.length);
        assertEquals("", CHARSET[0]);
        assertEquals("你", CHARSET[1]);
        assertEquals(" ", CHARSET[4]);
    }

    @Test public void ctcCollapsesRepeatsAndBlanks() {
        Decoded d = OcrPostProcess.ctcGreedy(onehot(5, 1, 1, 0, 2, 2, 0, 3, 3), 8, 5, CHARSET);
        assertEquals("你好a", d.text);
        assertEquals(0.9f, d.score, 1e-4f);
    }

    @Test public void ctcKeepsRepeatSeparatedByBlank() {
        assertEquals("你你", OcrPostProcess.ctcGreedy(onehot(5, 1, 0, 1), 3, 5, CHARSET).text);
    }

    @Test public void ctcAllBlankIsEmptyWithZeroScore() {
        Decoded d = OcrPostProcess.ctcGreedy(onehot(5, 0, 0, 0), 3, 5, CHARSET);
        assertEquals("", d.text);
        assertEquals(0f, d.score, 0f);
    }

    @Test public void ctcSpaceClassIsKept() {
        assertEquals("a 你", OcrPostProcess.ctcGreedy(onehot(5, 3, 4, 1), 3, 5, CHARSET).text);
    }

    // ---- line merge ----
    @Test public void mergeJoinsNeighboursAndSeparatesLines() {
        List<Box> in = Arrays.asList(
                box(10, 10, 60, 40, "你好"),
                box(62, 10, 110, 40, "世界"),      // tiny gap: joined directly
                box(160, 10, 200, 40, "end"),      // wide gap: one space
                box(10, 60, 90, 90, "第二行"));
        assertEquals("你好世界 end\n第二行", OcrPostProcess.mergeLines(in));
    }

    @Test public void mergeSkipsEmptyTexts() {
        List<Box> in = Arrays.asList(box(10, 10, 60, 40, ""), box(10, 60, 90, 90, "x"));
        assertEquals("x", OcrPostProcess.mergeLines(in));
    }
}
