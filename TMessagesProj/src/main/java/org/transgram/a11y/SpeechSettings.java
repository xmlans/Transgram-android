package org.transgram.a11y;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable: which message fields are spoken and in which order. */
public final class SpeechSettings {
    private final SpeechPreset preset;
    private final List<SpeechField> order;
    private final Map<SpeechField, Boolean> on;

    private SpeechSettings(SpeechPreset preset, List<SpeechField> order, Map<SpeechField, Boolean> on) {
        this.preset = preset;
        this.order = Collections.unmodifiableList(order);
        this.on = on;
    }

    public static SpeechSettings forPreset(SpeechPreset preset) {
        List<SpeechField> order = new ArrayList<>();
        Map<SpeechField, Boolean> on = new EnumMap<>(SpeechField.class);
        for (SpeechField f : SpeechField.values()) {
            order.add(f);
            on.put(f, defaultOn(preset, f));
        }
        return new SpeechSettings(preset, order, on);
    }

    private static boolean defaultOn(SpeechPreset preset, SpeechField f) {
        switch (preset) {
            case BRIEF:
                return f == SpeechField.SENDER || f == SpeechField.TYPE || f == SpeechField.BODY || f == SpeechField.STATUS;
            case VERBOSE:
                return true;
            case STANDARD:
            default:
                return f != SpeechField.STATS;
        }
    }

    public SpeechPreset preset() { return preset; }
    public boolean isOn(SpeechField f) { return Boolean.TRUE.equals(on.get(f)); }
    public boolean verboseExtras() { return preset == SpeechPreset.VERBOSE; }
    public List<SpeechField> order() { return order; }

    public List<SpeechField> enabledInOrder() {
        List<SpeechField> out = new ArrayList<>();
        for (SpeechField f : order) if (isOn(f)) out.add(f);
        return out;
    }

    /** Moves the given fields to the front, keeping the rest in their current order. */
    public SpeechSettings withOrder(List<SpeechField> first) {
        List<SpeechField> next = new ArrayList<>(first);
        for (SpeechField f : order) if (!next.contains(f)) next.add(f);
        return new SpeechSettings(preset, next, new EnumMap<>(on));
    }

    /** Moves one field by delta positions (-1 = up), clamped to the list. */
    public SpeechSettings move(SpeechField f, int delta) {
        List<SpeechField> next = new ArrayList<>(order);
        int i = next.indexOf(f);
        int j = Math.max(0, Math.min(next.size() - 1, i + delta));
        if (i < 0 || i == j) return this;
        next.remove(i);
        next.add(j, f);
        return new SpeechSettings(preset, next, new EnumMap<>(on));
    }

    public SpeechSettings withField(SpeechField f, boolean enabled) {
        Map<SpeechField, Boolean> next = new EnumMap<>(on);
        next.put(f, enabled);
        return new SpeechSettings(preset, new ArrayList<>(order), next);
    }

    /** Format: v1;PRESET;FIELD+,FIELD-,... */
    public String serialize() {
        StringBuilder sb = new StringBuilder("v1;").append(preset.name()).append(';');
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sb.append(',');
            SpeechField f = order.get(i);
            sb.append(f.name()).append(isOn(f) ? '+' : '-');
        }
        return sb.toString();
    }

    public static SpeechSettings parse(String s) {
        SpeechSettings fallback = forPreset(SpeechPreset.STANDARD);
        if (s == null) return fallback;
        String[] parts = s.split(";", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) return fallback;
        SpeechPreset preset;
        try {
            preset = SpeechPreset.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
        SpeechSettings base = forPreset(preset);
        List<SpeechField> order = new ArrayList<>();
        Map<SpeechField, Boolean> on = new EnumMap<>(base.on);
        for (String item : parts[2].split(",")) {
            if (item.length() < 2) continue;
            char flag = item.charAt(item.length() - 1);
            try {
                SpeechField f = SpeechField.valueOf(item.substring(0, item.length() - 1));
                if (!order.contains(f)) order.add(f);
                on.put(f, flag == '+');
            } catch (IllegalArgumentException ignore) {
                // field from another app version
            }
        }
        for (SpeechField f : SpeechField.values()) if (!order.contains(f)) order.add(f);
        return new SpeechSettings(preset, order, on);
    }
}
