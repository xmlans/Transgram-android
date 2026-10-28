package org.transgram.a11y;

import java.util.ArrayList;
import java.util.List;

/** Pure logic: strips decorative symbols, zero-width chars, stacked marks and emoji runs from display names for speech. */
public final class NameCleaner {
    private NameCleaner() {}

    private static final int TEXT = 0, EMOJI = 1, SPACE = 2;

    private static final class Tok {
        final int kind;
        final StringBuilder text = new StringBuilder();
        int marks;
        int markLimit = 1;

        Tok(int kind) {
            this.kind = kind;
        }
    }

    public static String clean(String name) {
        if (name == null || name.isEmpty()) return name;
        String s = process(name);
        if (s.isEmpty() || (s.length() < 2 && s.length() * 5 < name.length())) return name;
        return s;
    }

    private static String process(String name) {
        int n = name.length();
        List<Tok> toks = new ArrayList<>();
        Tok cur = null; // base token that may still take combining marks
        boolean prevDropped = false;
        int i = 0;
        while (i < n) {
            int cp = name.codePointAt(i);
            int len = Character.charCount(cp);
            if (isInvisible(cp)) {
                i += len;
                continue;
            }
            if (isEmojiStart(name, i)) {
                int end = emojiEnd(name, i);
                Tok t = new Tok(EMOJI);
                t.text.append(name, i, end);
                toks.add(t);
                cur = null;
                prevDropped = false;
                i = end;
                continue;
            }
            if (isDecoration(cp)) {
                cur = null;
                prevDropped = true;
                i += len;
                continue;
            }
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK) {
                if (cur != null && cur.marks < cur.markLimit) {
                    cur.text.appendCodePoint(cp);
                    cur.marks++;
                }
                i += len;
                continue;
            }
            if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                toks.add(new Tok(SPACE));
                cur = null;
                i += len;
                continue;
            }
            if (cp == 0x5F61 && prevDropped && i + len < n) { // 彡 right after a decoration symbol
                i += len;
                continue;
            }
            if (prevDropped && !toks.isEmpty() && toks.get(toks.size() - 1).kind == TEXT) {
                toks.add(new Tok(SPACE)); // "Anna★Lee" must not become one word
            }
            Tok t = new Tok(TEXT);
            t.text.appendCodePoint(cp);
            t.markLimit = markLimit(cp);
            toks.add(t);
            cur = t;
            prevDropped = false;
            i += len;
        }
        StringBuilder out = new StringBuilder();
        int lastKind = -1;
        for (Tok t : toks) {
            if (t.kind == EMOJI && lastKind == EMOJI) continue;
            if (t.kind == SPACE) {
                if (lastKind == SPACE || lastKind == -1) continue;
                out.append(' ');
            } else {
                out.append(t.text);
            }
            lastKind = t.kind;
        }
        String s = out.toString().trim();
        if (s.length() >= 2) {
            int idx = "【『〖".indexOf(s.charAt(0));
            if (idx >= 0 && s.charAt(s.length() - 1) == "】』〗".charAt(idx)) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }

    private static int markLimit(int base) {
        Character.UnicodeScript sc;
        try {
            sc = Character.UnicodeScript.of(base);
        } catch (IllegalArgumentException e) {
            return 1;
        }
        switch (sc) {
            case HAN: case HIRAGANA: case KATAKANA: case HANGUL: case COMMON: case INHERITED:
                return 1;
            default:
                return 2; // Latin (decomposed Vietnamese), Thai, Indic, Arabic, Hebrew use two marks legitimately
        }
    }

    private static boolean isInvisible(int cp) {
        return (cp >= 0x200B && cp <= 0x200F) || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2060 && cp <= 0x206F) || cp == 0xFEFF || (cp >= 0xFE00 && cp <= 0xFE0F);
    }

    private static boolean isDecoration(int cp) {
        if (cp == 0x2640 || cp == 0x2642 || cp == 0x26A7) return false; // ♀ ♂ ⚧ carry meaning
        return (cp >= 0x2500 && cp <= 0x27BF) || cp == 0xA9C1 || cp == 0xA9C2 || (cp >= 0x0F3A && cp <= 0x0F3D);
    }

    private static boolean isRegional(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    private static boolean isEmojiStart(String s, int i) {
        int cp = s.codePointAt(i);
        if (cp >= 0x1F000 && cp <= 0x1FAFF) return true;
        switch (cp) {
            case 0x2728: case 0x2764: case 0x2705: case 0x274C: case 0x2753: case 0x2757: case 0x26A1:
            case 0x2614: case 0x2615: case 0x26BD: case 0x26C4: case 0x270A: case 0x270B:
            case 0x2B50: case 0x2B55: case 0x2B1B: case 0x2B1C: case 0x231A: case 0x231B:
                return true;
            default:
                break;
        }
        if (cp >= 0x23E9 && cp <= 0x23F3) return true;
        int next = i + Character.charCount(cp);
        return next < s.length() && s.charAt(next) == 0xFE0F && !Character.isLetterOrDigit(cp);
    }

    private static boolean isZwjTarget(String s, int i) {
        if (i >= s.length()) return false;
        int cp = s.codePointAt(i);
        return isEmojiStart(s, i) || cp == 0x2640 || cp == 0x2642 || cp == 0x2695 || cp == 0x2696
                || cp == 0x2708 || cp == 0x2764;
    }

    /** Index after the whole emoji cluster starting at i (flag pair, modifiers, ZWJ chains). */
    private static int emojiEnd(String s, int i) {
        int n = s.length();
        int cp = s.codePointAt(i);
        int j = i + Character.charCount(cp);
        if (isRegional(cp) && j < n && isRegional(s.codePointAt(j))) {
            return j + Character.charCount(s.codePointAt(j));
        }
        while (j < n) {
            int c = s.codePointAt(j);
            int cc = Character.charCount(c);
            if (c == 0xFE0F || c == 0x20E3 || (c >= 0x1F3FB && c <= 0x1F3FF) || (c >= 0xE0020 && c <= 0xE007F)) {
                j += cc;
            } else if (c == 0x200D && isZwjTarget(s, j + 1)) {
                j += 1 + Character.charCount(s.codePointAt(j + 1));
            } else {
                break;
            }
        }
        return j;
    }
}
