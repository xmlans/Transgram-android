package org.transgram.ocr;

/** Which download milestones (every 25%) have been announced. Pure Java. */
public final class ProgressSteps {
    private int lastAnnounced = 0;

    /** @return the milestone to announce now (25, 50, 75 or 100), or -1 when there is nothing new to say. */
    public int update(long done, long total) {
        if (total <= 0) return -1;
        final int percent = (int) Math.min(100, done * 100 / total);
        final int milestone = percent >= 100 ? 100 : (percent / 25) * 25;
        if (milestone > lastAnnounced && milestone > 0) {
            lastAnnounced = milestone;
            return milestone;
        }
        return -1;
    }

    public void reset() {
        lastAnnounced = 0;
    }
}
