package org.transgram.a11y;

import java.util.Calendar;
import java.util.Locale;

final class SpeechDates {
    private SpeechDates() {}

    /** Whole calendar days from d to n (0 = same day, 1 = d is yesterday). */
    static int dayDiff(Calendar d, Calendar n) {
        Calendar a = (Calendar) d.clone();
        Calendar b = (Calendar) n.clone();
        for (Calendar c : new Calendar[]{a, b}) {
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
        }
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86_400_000.0);
    }

    static String size(long bytes, String byteUnit, String gap) {
        if (bytes < 1024) return bytes + byteUnit;
        String[] units = {"KB", "MB", "GB"};
        double v = bytes;
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.US, "%.1f", v) + gap + units[i];
    }
}
