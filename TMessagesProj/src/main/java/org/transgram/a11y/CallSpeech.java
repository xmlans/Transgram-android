package org.transgram.a11y;

import java.util.List;
import java.util.Locale;

/** Pure logic: call state -> spoken text. Texts come from an injected {@link Strings} so this stays JVM-testable. */
public final class CallSpeech {
    private CallSpeech() {}

    public enum Key {
        INCOMING_VOICE, INCOMING_VIDEO, UNKNOWN_CALLER,
        CONNECTING, RINGING, CONNECTED, RECONNECTING, ENDED, ENDED_DURATION, FAILED, BUSY,
        MUTE, SPEAKER, BLUETOOTH, VIDEO, FLIP_CAMERA, END_CALL, STATE_ON, STATE_OFF,
        DURATION_NOW, DURATION_NONE,
        NAME_SEP, CLAUSE_SEP, JOINED, LEFT, JOINED_MANY, LEFT_MANY,
        MY_MUTED, MY_UNMUTED, MY_FORBIDDEN, MY_CAN_SPEAK
    }

    public interface Strings {
        String get(Key key);
    }

    public enum Phase { NONE, INCOMING, CONNECTING, RINGING, CONNECTED, RECONNECTING, ENDED, FAILED, BUSY }

    public enum Button { MUTE, SPEAKER, BLUETOOTH, VIDEO, FLIP_CAMERA, END_CALL }

    /** What the signed-in user can do in a group call. OTHER = connecting, scheduled, raised hand and so on. */
    public enum GroupSelf { OTHER, MUTED, TALKING, FORBIDDEN }

    private static String fmt(Strings s, Key k, Object... args) {
        return String.format(Locale.ROOT, s.get(k), args);
    }

    /** Text for a call phase, or null if there is nothing to say. durationSec is the finished call length for ENDED. */
    public static String status(Strings s, SpeechFormat f, Phase p, String name, boolean video, int durationSec) {
        switch (p) {
            case INCOMING: {
                String who = name == null || name.trim().isEmpty() ? s.get(Key.UNKNOWN_CALLER) : name.trim();
                return fmt(s, video ? Key.INCOMING_VIDEO : Key.INCOMING_VOICE, who);
            }
            case CONNECTING: return s.get(Key.CONNECTING);
            case RINGING: return s.get(Key.RINGING);
            case CONNECTED: return s.get(Key.CONNECTED);
            case RECONNECTING: return s.get(Key.RECONNECTING);
            case FAILED: return s.get(Key.FAILED);
            case BUSY: return s.get(Key.BUSY);
            case ENDED:
                return durationSec > 0 ? fmt(s, Key.ENDED_DURATION, f.duration(durationSec)) : s.get(Key.ENDED);
            default:
                return null;
        }
    }

    /** Content description of a call button; state is on/off for toggles and ignored for the others. */
    public static String button(Strings s, Button b, boolean on) {
        Key base;
        switch (b) {
            case MUTE: base = Key.MUTE; break;
            case SPEAKER: base = Key.SPEAKER; break;
            case VIDEO: base = Key.VIDEO; break;
            case BLUETOOTH: return s.get(Key.BLUETOOTH);
            case FLIP_CAMERA: return s.get(Key.FLIP_CAMERA);
            default: return s.get(Key.END_CALL);
        }
        return fmt(s, on ? Key.STATE_ON : Key.STATE_OFF, s.get(base));
    }

    public static String duration(Strings s, SpeechFormat f, int seconds) {
        return seconds > 0 ? fmt(s, Key.DURATION_NOW, f.duration(seconds)) : s.get(Key.DURATION_NONE);
    }

    /** Announcement for a change of my own group-call state, or null (no change, or the initial connect). */
    public static String groupSelf(Strings s, GroupSelf prev, GroupSelf cur) {
        if (prev == cur || cur == GroupSelf.OTHER) return null;
        switch (cur) {
            case FORBIDDEN: return s.get(Key.MY_FORBIDDEN);
            case MUTED:
                if (prev == GroupSelf.FORBIDDEN) return s.get(Key.MY_CAN_SPEAK);
                return prev == GroupSelf.TALKING ? s.get(Key.MY_MUTED) : null;
            case TALKING:
                if (prev == GroupSelf.FORBIDDEN) return s.get(Key.MY_CAN_SPEAK);
                return prev == GroupSelf.MUTED ? s.get(Key.MY_UNMUTED) : null;
            default:
                return null;
        }
    }

    private static final int MAX_NAMES = 2;

    private static String names(Strings s, List<String> list, Key one, Key many) {
        if (list.isEmpty()) return null;
        if (list.size() > MAX_NAMES) return fmt(s, many, list.size());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(s.get(Key.NAME_SEP));
            sb.append(list.get(i));
        }
        return fmt(s, one, sb.toString());
    }

    /** "A joined, B left" for a batch of roster changes, or null if empty. */
    public static String joinLeave(Strings s, List<String> joined, List<String> left) {
        String j = names(s, joined, Key.JOINED, Key.JOINED_MANY);
        String l = names(s, left, Key.LEFT, Key.LEFT_MANY);
        if (j == null) return l;
        if (l == null) return j;
        return j + s.get(Key.CLAUSE_SEP) + l;
    }

    /** Remembers the last spoken phase so the same state is not announced twice. */
    public static final class Tracker {
        private Phase last = Phase.NONE;

        public String next(Strings s, SpeechFormat f, Phase p, String name, boolean video, int durationSec) {
            if (p == Phase.NONE || p == last) return null;
            last = p;
            return status(s, f, p, name, video, durationSec);
        }

        public void reset() {
            last = Phase.NONE;
        }
    }
}
