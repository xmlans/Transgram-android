package org.transgram.a11y;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.transgram.a11y.SpeechFormat.Label;

/** Builds the spoken parts of a message. Pure Java: no Android classes. */
public final class MessageSpeech {
    static final int REPLY_PREVIEW_CHARS = 30;
    static final int OCR_MAX_CHARS = 200;

    private MessageSpeech() {}

    public static List<CharSequence> build(SpeechInput in, SpeechSettings s, SpeechFormat f, long nowMillis, TimeZone tz) {
        List<CharSequence> parts = new ArrayList<>();
        for (SpeechField field : s.enabledInOrder()) {
            switch (field) {
                case SENDER: sender(in, f, parts); break;
                case TYPE: type(in, s, f, parts); break;
                case FORWARD_REPLY: forwardReply(in, f, parts); break;
                case BODY:
                    if (in.body != null && in.body.length() > 0) parts.add(in.body);
                    ocr(in, f, parts);
                    break;
                case EXTRAS: extras(in, f, parts); break;
                case TIME: if (in.dateMillis > 0) parts.add(f.time(in.dateMillis, nowMillis, tz)); break;
                case STATUS: status(in, f, parts); break;
                case REACTIONS: reactions(in, f, parts); break;
                case STATS: stats(in, f, parts); break;
            }
        }
        return parts;
    }

    public static String join(List<CharSequence> parts, SpeechFormat f) {
        StringBuilder sb = new StringBuilder();
        for (CharSequence p : parts) {
            if (sb.length() > 0) sb.append(f.separator());
            sb.append(p);
        }
        return sb.toString();
    }

    private static void sender(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (in.own) {
            out.add(f.label(Label.YOU));
        } else if (in.senderName != null) {
            out.add(in.adminTitle != null ? in.senderName + " " + in.adminTitle : in.senderName);
        }
    }

    private static void type(SpeechInput in, SpeechSettings s, SpeechFormat f, List<CharSequence> out) {
        StringBuilder sb = new StringBuilder();
        switch (in.kind) {
            case TEXT:
                return;
            case PHOTO: sb.append(f.label(Label.PHOTO)); break;
            case VIDEO: sb.append(f.label(Label.VIDEO)).append(' ').append(f.duration(in.durationSec)); break;
            case VOICE: sb.append(f.label(Label.VOICE)).append(' ').append(f.duration(in.durationSec)); break;
            case ROUND_VIDEO: sb.append(f.label(Label.ROUND_VIDEO)).append(' ').append(f.duration(in.durationSec)); break;
            case MUSIC:
                sb.append(f.label(Label.MUSIC));
                if (in.musicTitle != null) sb.append(' ').append(in.musicTitle);
                sb.append(' ').append(f.duration(in.durationSec));
                break;
            case GIF: sb.append(f.label(Label.GIF)); break;
            case STICKER:
                sb.append(f.label(Label.STICKER));
                if (in.stickerEmoji != null) sb.append(' ').append(in.stickerEmoji);
                break;
            case FILE:
                sb.append(f.label(Label.FILE));
                if (in.fileName != null) sb.append(' ').append(in.fileName);
                if (in.fileSize > 0) sb.append(' ').append(f.size(in.fileSize));
                break;
            case POLL:
                sb.append(f.label(Label.POLL));
                if (in.pollQuestion != null) sb.append(' ').append(in.pollQuestion);
                break;
            case LOCATION: sb.append(f.label(Label.LOCATION)); break;
            case CONTACT: sb.append(f.label(Label.CONTACT)); break;
            case OTHER_MEDIA: sb.append(f.label(Label.MEDIA)); break;
        }
        if (s.verboseExtras()) {
            if (in.kind != SpeechInput.Kind.FILE && in.fileSize > 0) sb.append(' ').append(f.size(in.fileSize));
            if (in.downloadPercent >= 0 && in.downloadPercent < 100) {
                sb.append(' ').append(f.label(Label.DOWNLOADED_PERCENT)).append(' ').append(in.downloadPercent).append('%');
            }
        }
        out.add(sb.toString());
    }

    private static void forwardReply(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (in.forwardFrom != null) out.add(f.label(Label.FORWARDED_FROM) + " " + in.forwardFrom);
        if (in.replyToName != null) {
            String preview = in.replyToText == null ? "" : in.replyToText;
            if (preview.length() > REPLY_PREVIEW_CHARS) preview = preview.substring(0, REPLY_PREVIEW_CHARS) + "…";
            out.add(f.label(Label.REPLY_TO) + " " + in.replyToName + (preview.isEmpty() ? "" : f.colon() + preview));
        }
    }

    private static void ocr(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (in.ocrText == null) return;
        String t = in.ocrText.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return;
        if (t.codePointCount(0, t.length()) > OCR_MAX_CHARS) {
            t = t.substring(0, t.offsetByCodePoints(0, OCR_MAX_CHARS)) + "…";
        }
        out.add(f.label(Label.OCR_TEXT) + f.colon() + t);
    }

    private static void extras(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (in.edited) out.add(f.label(Label.EDITED));
        if (in.pinned) out.add(f.label(Label.PINNED));
        if (in.silent) out.add(f.label(Label.SILENT));
        if (in.selfDestructSec > 0) out.add(f.label(Label.SELF_DESTRUCT) + " " + f.duration(in.selfDestructSec));
        if (in.priceStars > 0) out.add(in.priceStars + " " + f.label(Label.PRICE_STARS));
    }

    private static void status(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (!in.own) return;
        switch (in.status) {
            case READ: out.add(f.label(Label.READ)); break;
            case UNREAD: out.add(f.label(Label.UNREAD)); break;
            case SENDING: out.add(f.label(Label.SENDING)); break;
            case FAILED: out.add(f.label(Label.FAILED)); break;
            default: break;
        }
    }

    private static void reactions(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (in.reactions == null || in.reactions.isEmpty()) return;
        StringBuilder sb = new StringBuilder(f.label(Label.REACTIONS)).append(f.colon());
        String mine = null;
        for (int i = 0; i < in.reactions.size(); i++) {
            SpeechInput.Reaction r = in.reactions.get(i);
            if (i > 0) sb.append(f.separator());
            sb.append(r.emoji).append(' ').append(r.count);
            if (r.mine && mine == null) mine = r.emoji;
        }
        if (mine != null) sb.append(f.separator()).append(f.label(Label.YOU_REACTED)).append(' ').append(mine);
        out.add(sb.toString());
    }

    private static void stats(SpeechInput in, SpeechFormat f, List<CharSequence> out) {
        if (in.views > 0) out.add(f.label(Label.VIEWS) + " " + in.views);
        if (in.comments > 0) out.add(f.label(Label.COMMENTS) + " " + in.comments);
    }
}
