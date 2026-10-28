package org.transgram.a11y;

import java.util.Collections;
import java.util.List;

/** Plain description of one message for speech. Filled by MessageSpeechAdapter. */
public class SpeechInput {
    public enum Kind { TEXT, PHOTO, VIDEO, VOICE, ROUND_VIDEO, MUSIC, GIF, STICKER, FILE, POLL, LOCATION, CONTACT, OTHER_MEDIA }
    public enum Status { NONE, SENDING, UNREAD, READ, FAILED }

    public static final class Reaction {
        public final String emoji;
        public final int count;
        public final boolean mine;

        public Reaction(String emoji, int count, boolean mine) {
            this.emoji = emoji;
            this.count = count;
            this.mine = mine;
        }
    }

    /** Null when the sender should not be spoken (1:1 incoming). Ignored when own. */
    public String senderName;
    public String adminTitle;
    public boolean own;
    public Kind kind = Kind.TEXT;
    public int durationSec;
    public String fileName;
    public long fileSize;
    public String stickerEmoji;
    public String musicTitle;
    public String pollQuestion;
    public String forwardFrom;
    public String replyToName;
    public String replyToText;
    /** Message text or caption; may be a Spannable with links. */
    public CharSequence body;
    /** Text recognised in a photo (OCR); null when unknown or switched off. Spoken right after the body. */
    public String ocrText;
    public boolean edited;
    public boolean pinned;
    public boolean silent;
    public int selfDestructSec;
    public long priceStars;
    public long dateMillis;
    public Status status = Status.NONE;
    public List<Reaction> reactions = Collections.emptyList();
    public int views;
    public int comments;
    /** -1 when unknown or not downloading. */
    public int downloadPercent = -1;
}
