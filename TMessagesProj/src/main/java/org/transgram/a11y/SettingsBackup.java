package org.transgram.a11y;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Text format for exporting and importing TransGram preferences: "transgram-a11y-settings-v1" then one "key=type:value" per line. */
public final class SettingsBackup {
    public static final String HEADER = "transgram-a11y-settings-v1";

    private SettingsBackup() {}

    /** Supports String, Boolean, Integer, Long and Float values; anything else is skipped. */
    public static String export(Map<String, ?> all) {
        StringBuilder sb = new StringBuilder(HEADER).append('\n');
        for (Map.Entry<String, ?> e : new TreeMap<String, Object>(all).entrySet()) {
            String key = e.getKey();
            Object v = e.getValue();
            if (key == null || key.indexOf('=') >= 0 || key.indexOf('\n') >= 0 || v == null) continue;
            char type;
            if (v instanceof String) type = 's';
            else if (v instanceof Boolean) type = 'b';
            else if (v instanceof Integer) type = 'i';
            else if (v instanceof Long) type = 'l';
            else if (v instanceof Float) type = 'f';
            else continue;
            sb.append(key).append('=').append(type).append(':').append(escape(String.valueOf(v))).append('\n');
        }
        return sb.toString();
    }

    /** Returns null when the text is not a TransGram settings export. Malformed lines are skipped. */
    public static Map<String, Object> parse(String text) {
        if (text == null) return null;
        String[] lines = text.replace("\r\n", "\n").split("\n");
        if (lines.length == 0 || !HEADER.equals(lines[0].trim())) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int eq = line.indexOf('=');
            if (eq <= 0 || line.length() < eq + 3 || line.charAt(eq + 2) != ':') continue;
            String key = line.substring(0, eq);
            char type = line.charAt(eq + 1);
            String raw = unescape(line.substring(eq + 3));
            try {
                switch (type) {
                    case 's': out.put(key, raw); break;
                    case 'b': out.put(key, Boolean.valueOf(raw)); break;
                    case 'i': out.put(key, Integer.valueOf(raw)); break;
                    case 'l': out.put(key, Long.valueOf(raw)); break;
                    case 'f': out.put(key, Float.valueOf(raw)); break;
                    default: break;
                }
            } catch (NumberFormatException ignore) {
                // skip bad value
            }
        }
        return out;
    }

    /**
     * Keeps only known keys that have the right type and a valid value, so a hand-edited clipboard cannot
     * make later getBoolean/getFloat calls throw. Allowed enum names are passed in to keep this class pure.
     */
    public static Map<String, Object> sanitize(Map<String, Object> in, Collection<String> feedbackEvents,
                                               Collection<String> feedbackModes, Collection<String> announceModes) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k.equals("enabled") || k.equals("ann_downloads")
                    || k.equals("name_clean") || k.equals("call_group_joinleave")
                    || k.equals("ocr_enabled") || k.equals("ocr_in_speech") || k.equals("ocr_wifi_only")) {
                if (v instanceof Boolean) out.put(k, v);
            } else if (k.equals("speech")) {
                if (v instanceof String && ((String) v).startsWith("v1;")) out.put(k, v);
            } else if (k.equals("fb_volume")) {
                if (v instanceof Float) {
                    float f = (Float) v;
                    if (!Float.isNaN(f)) out.put(k, Math.max(0f, Math.min(1f, f)));
                }
            } else if (k.startsWith("fb_")) {
                if (feedbackEvents.contains(k.substring(3)) && v instanceof String && feedbackModes.contains(v)) out.put(k, v);
            } else if (k.equals("ann_default") || k.matches("ann_-?[0-9]+")) {
                if (v instanceof String && announceModes.contains(v)) out.put(k, v);
            } else if (k.equals("ui_lang")) {
                if ("system".equals(v) || "zh".equals(v) || "en".equals(v)) out.put(k, v);
            }
        }
        return out;
    }

    private static final char BS = (char) 92;

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == BS) sb.append(BS).append(BS);
            else if (c == '\n') sb.append(BS).append('n');
            else if (c == '\r') sb.append(BS).append('r');
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String unescape(String s) {
        if (s.indexOf(BS) < 0) return s;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == BS && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
