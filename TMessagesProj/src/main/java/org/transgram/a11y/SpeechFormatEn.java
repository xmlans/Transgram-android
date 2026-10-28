package org.transgram.a11y;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public class SpeechFormatEn implements SpeechFormat {
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    @Override
    public String time(long dateMillis, long nowMillis, TimeZone tz) {
        Calendar d = Calendar.getInstance(tz);
        d.setTimeInMillis(dateMillis);
        Calendar n = Calendar.getInstance(tz);
        n.setTimeInMillis(nowMillis);
        int h = d.get(Calendar.HOUR_OF_DAY), m = d.get(Calendar.MINUTE);
        String clock = String.format(Locale.US, "%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
        int dayDiff = SpeechDates.dayDiff(d, n);
        if (dayDiff == 0) return clock;
        if (dayDiff == 1) return "yesterday " + clock;
        String date = MONTHS[d.get(Calendar.MONTH)] + " " + d.get(Calendar.DAY_OF_MONTH);
        if (d.get(Calendar.YEAR) != n.get(Calendar.YEAR)) date += ", " + d.get(Calendar.YEAR);
        return date + ", " + clock;
    }

    @Override
    public String duration(int seconds) {
        int h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(plural(h, "hour")).append(' ');
        if (h > 0 || m > 0) sb.append(plural(m, "minute")).append(' ');
        sb.append(plural(s, "second"));
        return sb.toString();
    }

    private static String plural(int n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    @Override public String size(long bytes) { return SpeechDates.size(bytes, " bytes", " "); }
    @Override public String count(int n) { return String.valueOf(n); }
    @Override public String separator() { return ", "; }
    @Override public String colon() { return ": "; }

    @Override
    public String label(Label label) {
        switch (label) {
            case YOU: return "You";
            case PHOTO: return "Photo";
            case OCR_TEXT: return "Text in image";
            case VIDEO: return "Video";
            case VOICE: return "Voice message";
            case ROUND_VIDEO: return "Video message";
            case MUSIC: return "Music";
            case GIF: return "GIF";
            case STICKER: return "Sticker";
            case FILE: return "File";
            case POLL: return "Poll";
            case LOCATION: return "Location";
            case CONTACT: return "Contact";
            case MEDIA: return "Media";
            case FORWARDED_FROM: return "Forwarded from";
            case REPLY_TO: return "Reply to";
            case EDITED: return "edited";
            case PINNED: return "pinned";
            case SILENT: return "sent silently";
            case SELF_DESTRUCT: return "self-destructing";
            case PRICE_STARS: return "stars";
            case READ: return "read";
            case UNREAD: return "unread";
            case SENDING: return "sending";
            case FAILED: return "failed to send";
            case REACTIONS: return "Reactions";
            case YOU_REACTED: return "you reacted";
            case VIEWS: return "views";
            case COMMENTS: return "comments";
            case DOWNLOADED_PERCENT: return "downloaded";
            case UNREAD_COUNT: return "unread";
            case MENTIONED: return "you were mentioned";
            case MUTED: return "muted";
            case PINNED_CHAT: return "pinned";
            case GROUP: return "group";
            case CHANNEL: return "channel";
            case BOT: return "bot";
            case ONLINE: return "online";
            case VERIFIED: return "verified";
            case SECRET_CHAT: return "secret chat";
            default: return label.name();
        }
    }
}
