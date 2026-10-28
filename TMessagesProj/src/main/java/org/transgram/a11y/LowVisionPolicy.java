package org.transgram.a11y;

/** Android-free bounds and contrast rules. UI scaling never changes display density. */
public final class LowVisionPolicy {
    private LowVisionPolicy() {}

    public static float uiScale(int percent) {
        return Math.max(100, Math.min(130, percent)) / 100f;
    }

    public static int rowHeight(int baseDp, int percent, boolean largeControls) {
        return Math.max((int) Math.ceil(baseDp * (double) Math.max(100, Math.min(130, percent)) / 100),
                largeControls ? 60 : baseDp);
    }

    public static int messageSize(int size) {
        return Math.max(12, Math.min(30, size));
    }

    public static float compactText(float baseDp, int percent) {
        return baseDp * Math.min(1.15f, uiScale(percent));
    }

    /** Preserve readable theme accents; only replace low-contrast foregrounds. */
    public static int readableColor(int foreground, int background) {
        if (contrast(foreground, background) >= 7) return foreground;
        return contrast(0xff000000, background) >= contrast(0xffffffff, background)
                ? 0xff000000 : 0xffffffff;
    }

    public static double contrast(int foreground, int background) {
        double alpha = (foreground >>> 24) / 255d;
        double r = ((foreground >> 16) & 255) * alpha + ((background >> 16) & 255) * (1 - alpha);
        double g = ((foreground >> 8) & 255) * alpha + ((background >> 8) & 255) * (1 - alpha);
        double b = (foreground & 255) * alpha + (background & 255) * (1 - alpha);
        double front = luminance(r, g, b);
        double back = luminance((background >> 16) & 255, (background >> 8) & 255, background & 255);
        return (Math.max(front, back) + .05) / (Math.min(front, back) + .05);
    }

    private static double luminance(double r, double g, double b) {
        return .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b);
    }

    private static double linear(double channel) {
        double value = channel / 255;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
