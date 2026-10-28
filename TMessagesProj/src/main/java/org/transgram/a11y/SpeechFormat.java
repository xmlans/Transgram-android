package org.transgram.a11y;

import java.util.TimeZone;

/** Spoken formats for one UI language. Implementations must be pure Java (JVM-testable). */
public interface SpeechFormat {
    String time(long dateMillis, long nowMillis, TimeZone tz);
    String duration(int seconds);
    String size(long bytes);
    String count(int n);
    /** Separator placed between spoken fields. */
    String separator();
    /** Separator between a name and a quoted text ("张三：…" / "Ann: …"). */
    String colon();
    String label(Label label);

    enum Label {
        YOU, PHOTO, VIDEO, VOICE, ROUND_VIDEO, MUSIC, GIF, STICKER, FILE, POLL, LOCATION, CONTACT, MEDIA,
        FORWARDED_FROM, REPLY_TO, EDITED, PINNED, SILENT, SELF_DESTRUCT, PRICE_STARS,
        READ, UNREAD, SENDING, FAILED, REACTIONS, YOU_REACTED, VIEWS, COMMENTS,
        OCR_TEXT, DOWNLOADED_PERCENT, UNREAD_COUNT, MENTIONED, MUTED, PINNED_CHAT, GROUP, CHANNEL, BOT, ONLINE, VERIFIED, SECRET_CHAT
    }
}
