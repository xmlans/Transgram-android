package org.transgram.a11y;

import java.util.TimeZone;

import org.transgram.a11y.SpeechFormat.Label;

public final class DialogSpeech {
    private DialogSpeech() {}

    public static String say(DialogSpeechInput in, SpeechFormat f, long nowMillis, TimeZone tz) {
        boolean zh = "：".equals(f.colon());
        String sep = f.separator();
        StringBuilder sb = new StringBuilder(in.title == null ? "" : in.title);
        switch (in.type) {
            case GROUP: sb.append(' ').append(f.label(Label.GROUP)); break;
            case CHANNEL: sb.append(' ').append(f.label(Label.CHANNEL)); break;
            case BOT: sb.append(' ').append(f.label(Label.BOT)); break;
            default: break;
        }
        if (in.secret) sb.append(' ').append(f.label(Label.SECRET_CHAT));
        if (in.verified) sb.append(sep).append(f.label(Label.VERIFIED));
        if (in.online) sb.append(sep).append(f.label(Label.ONLINE));
        if (in.unread > 0) {
            sb.append(sep);
            if (zh) sb.append(f.label(Label.UNREAD_COUNT)).append(' ').append(f.count(in.unread));
            else sb.append(in.unread).append(' ').append(f.label(Label.UNREAD_COUNT));
        }
        if (in.lastText != null && in.lastText.length() > 0) {
            sb.append(sep);
            String who = in.lastOwn ? f.label(Label.YOU) : in.lastSender;
            if (who != null) sb.append(who).append(f.colon());
            sb.append(in.lastText);
        }
        if (in.dateMillis > 0) sb.append(sep).append(f.time(in.dateMillis, nowMillis, tz));
        if (in.mentioned) sb.append(sep).append(f.label(Label.MENTIONED));
        if (in.pinned) sb.append(sep).append(f.label(Label.PINNED_CHAT));
        if (in.muted) sb.append(sep).append(f.label(Label.MUTED));
        return sb.toString();
    }
}
