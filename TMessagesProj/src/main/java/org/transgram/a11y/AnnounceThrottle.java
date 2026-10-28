package org.transgram.a11y;

import java.util.LinkedHashSet;
import java.util.Set;

/** Collects incoming messages and produces one spoken line per window. Pure Java. */
public final class AnnounceThrottle {
    static final int MAX_CHARS = 80;
    private final long windowMs;
    private long firstAt;
    private int count;
    private String firstSender;
    private String firstText;
    private final Set<String> senders = new LinkedHashSet<>();

    public AnnounceThrottle(long windowMs) {
        this.windowMs = windowMs;
    }

    public void add(String sender, String text, long nowMs) {
        if (count == 0) {
            firstAt = nowMs;
            firstSender = sender == null ? "" : sender;
            firstText = text == null ? "" : text;
        }
        count++;
        senders.add(sender == null ? "" : sender);
    }

    public void reset() {
        count = 0;
        senders.clear();
    }

    /** Returns the line to speak once the window has passed, else null. */
    public String flushIfDue(long nowMs, SpeechFormat f) {
        if (count == 0 || nowMs - firstAt < windowMs) return null;
        boolean zh = "：".equals(f.colon());
        String line;
        if (count == 1) {
            String t = firstText.length() > MAX_CHARS ? firstText.substring(0, MAX_CHARS) + "…" : firstText;
            line = firstSender.isEmpty() ? t : firstSender + f.colon() + t;
        } else if (zh) {
            line = senders.size() == 1
                    ? firstSender + "发来 " + f.count(count) + "新消息"
                    : firstSender + "等 " + senders.size() + " 人发来 " + f.count(count) + "新消息";
        } else {
            int others = senders.size() - 1;
            line = others == 0
                    ? firstSender + " sent " + count + " new messages"
                    : firstSender + " and " + others + " other" + (others == 1 ? "" : "s") + " sent " + count + " new messages";
        }
        count = 0;
        senders.clear();
        return line;
    }
}
