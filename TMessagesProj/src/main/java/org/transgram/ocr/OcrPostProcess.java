package org.transgram.ocr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** PP-OCR post-processing without any Android classes: DB boxes, reading order, CTC decode, line merge. */
public final class OcrPostProcess {
    private OcrPostProcess() {}

    public static final float DET_THRESH = 0.3f;
    public static final float DET_BOX_THRESH = 0.6f;
    public static final float DET_UNCLIP_RATIO = 1.5f;
    public static final int DET_MIN_SIDE = 3;
    public static final int DET_MAX_SIDE = 960;

    public static final class Box {
        public float x0, y0, x1, y1;
        public float score;
        /** Filled by OcrEngine after recognition. */
        public String text;

        public Box(float x0, float y0, float x1, float y1, float score) {
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.score = score;
        }

        public float width() { return x1 - x0; }
        public float height() { return y1 - y0; }
        public float centerY() { return (y0 + y1) / 2f; }
    }

    public static final class Decoded {
        public final String text;
        public final float score;

        public Decoded(String text, float score) {
            this.text = text;
            this.score = score;
        }
    }

    /**
     * Detector input size: scale down so the longer side is at most maxSide (never scale up), then round
     * each side to a multiple of 32 (minimum 32).
     */
    public static int[] detSize(int w, int h, int maxSide) {
        float ratio = 1f;
        final int longSide = Math.max(w, h);
        if (longSide > maxSide) ratio = (float) maxSide / longSide;
        return new int[]{round32(w * ratio), round32(h * ratio)};
    }

    private static int round32(float v) {
        return Math.max(32, Math.round(v / 32f) * 32);
    }

    /**
     * DB post-processing on a probability map (row-major, mapW x mapH). Boxes are axis-aligned, in map
     * coordinates, already unclipped. Components whose shorter side is below minSide or whose mean
     * probability is below boxThresh are dropped.
     */
    public static List<Box> detBoxes(float[] prob, int mapW, int mapH, float thresh, float boxThresh,
                                     float unclipRatio, int minSide) {
        final List<Box> out = new ArrayList<>();
        final boolean[] seen = new boolean[mapW * mapH];
        final int[] stack = new int[mapW * mapH];
        for (int start = 0; start < prob.length; start++) {
            if (seen[start] || prob[start] <= thresh) continue;
            int sp = 0;
            stack[sp++] = start;
            seen[start] = true;
            int minX = mapW, minY = mapH, maxX = -1, maxY = -1;
            double sum = 0;
            int count = 0;
            while (sp > 0) {
                final int p = stack[--sp];
                final int x = p % mapW, y = p / mapW;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                sum += prob[p];
                count++;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        final int nx = x + dx, ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= mapW || ny >= mapH) continue;
                        final int q = ny * mapW + nx;
                        if (!seen[q] && prob[q] > thresh) {
                            seen[q] = true;
                            stack[sp++] = q;
                        }
                    }
                }
            }
            final float w = maxX - minX + 1, h = maxY - minY + 1;
            if (Math.min(w, h) < minSide) continue;
            final float score = (float) (sum / count);
            if (score < boxThresh) continue;
            // unclip: offset = area * ratio / perimeter, same formula as PaddleOCR's DB post-process
            final float d = w * h * unclipRatio / (2f * (w + h));
            final float x0 = Math.max(0f, minX - d), y0 = Math.max(0f, minY - d);
            final float x1 = Math.min(mapW, maxX + 1 + d), y1 = Math.min(mapH, maxY + 1 + d);
            if (Math.min(x1 - x0, y1 - y0) < minSide + 2) continue;
            out.add(new Box(x0, y0, x1, y1, score));
        }
        return out;
    }

    /** Maps a box from detector-map coordinates back to the source image and clamps it. */
    public static Box toSource(Box b, float scaleX, float scaleY, int imgW, int imgH) {
        final Box r = new Box(clamp(b.x0 * scaleX, 0, imgW), clamp(b.y0 * scaleY, 0, imgH),
                clamp(b.x1 * scaleX, 0, imgW), clamp(b.y1 * scaleY, 0, imgH), b.score);
        r.text = b.text;
        return r;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Groups boxes into text lines (top to bottom), each line sorted left to right. */
    public static List<List<Box>> groupLines(List<Box> boxes) {
        final List<Box> byY = new ArrayList<>(boxes);
        Collections.sort(byY, new Comparator<Box>() {
            @Override public int compare(Box a, Box b) { return Float.compare(a.centerY(), b.centerY()); }
        });
        final List<List<Box>> lines = new ArrayList<>();
        final List<float[]> stats = new ArrayList<>(); // {sum of centerY, sum of height, count}
        for (Box b : byY) {
            boolean placed = false;
            if (!lines.isEmpty()) {
                final int last = lines.size() - 1;
                final float[] s = stats.get(last);
                final float lineCy = s[0] / s[2], lineH = s[1] / s[2];
                if (Math.abs(b.centerY() - lineCy) <= 0.5f * Math.min(b.height(), lineH)) {
                    lines.get(last).add(b);
                    s[0] += b.centerY();
                    s[1] += b.height();
                    s[2] += 1;
                    placed = true;
                }
            }
            if (!placed) {
                final List<Box> l = new ArrayList<>();
                l.add(b);
                lines.add(l);
                stats.add(new float[]{b.centerY(), b.height(), 1});
            }
        }
        for (List<Box> l : lines) {
            Collections.sort(l, new Comparator<Box>() {
                @Override public int compare(Box a, Box b) { return Float.compare(a.x0, b.x0); }
            });
        }
        return lines;
    }

    /** Reading order as one flat list. */
    public static List<Box> sortReadingOrder(List<Box> boxes) {
        final List<Box> out = new ArrayList<>();
        for (List<Box> l : groupLines(boxes)) out.addAll(l);
        return out;
    }

    /** Index 0 = CTC blank, then the dictionary lines, then a space (PP-OCR use_space_char). */
    public static String[] buildCharset(List<String> dictLines) {
        final String[] cs = new String[dictLines.size() + 2];
        cs[0] = "";
        for (int i = 0; i < dictLines.size(); i++) {
            String s = dictLines.get(i);
            if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
            cs[i + 1] = s;
        }
        cs[cs.length - 1] = " ";
        return cs;
    }

    /** CTC greedy decode of a [steps x classes] row-major score matrix (blank = class 0). */
    public static Decoded ctcGreedy(float[] scores, int steps, int classes, String[] charset) {
        final StringBuilder sb = new StringBuilder();
        int prev = 0;
        double conf = 0;
        int kept = 0;
        for (int t = 0; t < steps; t++) {
            int best = 0;
            float bestV = scores[t * classes];
            for (int c = 1; c < classes; c++) {
                final float v = scores[t * classes + c];
                if (v > bestV) {
                    bestV = v;
                    best = c;
                }
            }
            if (best != 0 && best != prev && best < charset.length) {
                sb.append(charset[best]);
                conf += bestV;
                kept++;
            }
            prev = best;
        }
        return new Decoded(sb.toString(), kept == 0 ? 0f : (float) (conf / kept));
    }

    /**
     * Joins recognised boxes (Box.text filled) into text: one line per visual line; neighbours in a line
     * are joined directly, or with one space when the horizontal gap is wider than 0.6 line heights.
     */
    public static String mergeLines(List<Box> boxes) {
        final StringBuilder out = new StringBuilder();
        for (List<Box> line : groupLines(boxes)) {
            final StringBuilder sb = new StringBuilder();
            Box prev = null;
            for (Box b : line) {
                if (b.text == null || b.text.isEmpty()) continue;
                if (prev != null && b.x0 - prev.x1 > 0.6f * Math.max(prev.height(), b.height())) sb.append(' ');
                sb.append(b.text);
                prev = b;
            }
            if (sb.length() == 0) continue;
            if (out.length() > 0) out.append('\n');
            out.append(sb);
        }
        return out.toString();
    }
}
