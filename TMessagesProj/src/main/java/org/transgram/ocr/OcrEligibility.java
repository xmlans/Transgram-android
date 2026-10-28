package org.transgram.ocr;

import java.util.Locale;

/**
 * Which messages offer the "read text in image" screen-reader action. Pure Java so the rules are unit-tested; OcrUi
 * reads the MessageObject and the settings and passes plain values in.
 */
public final class OcrEligibility {
    private OcrEligibility() {}

    /** What OcrUi learned about one message. */
    public static final class Media {
        /** A compressed photo (TL_messageMediaPhoto). */
        public boolean photo;
        /** MIME type of the document, or null when the message has no document. */
        public String documentMime;
        public boolean sticker;
        public boolean gif;
        /** Self-destructing / view-once media: never recognised (the user can only look at it once). */
        public boolean secret;
        /** Not sent yet: the message id is temporary, so a cached result would be filed under the wrong key. */
        public boolean sending;
    }

    /** True for a document whose content BitmapFactory can decode as a still picture. */
    public static boolean isStillImageMime(String mime) {
        if (mime == null) return false;
        final String m = mime.trim().toLowerCase(Locale.ROOT);
        if (!m.startsWith("image/")) return false;
        return !m.equals("image/gif") && !m.startsWith("image/svg") && !m.equals("image/vnd.djvu");
    }

    /** The message itself is a picture OCR can work on. */
    public static boolean isImage(Media m) {
        if (m == null || m.secret || m.sending || m.sticker || m.gif) return false;
        return m.photo || isStillImageMime(m.documentMime);
    }

    /**
     * The action is offered when the accessibility master switch and the OCR switch are both on, the device can run
     * the engine, and the message is a picture. Media hidden by the 18+ mask still offers the action: performing it
     * says that the picture is hidden instead of recognising it.
     */
    public static boolean offer(boolean a11yEnabled, boolean ocrEnabled, boolean supported, Media m) {
        return a11yEnabled && ocrEnabled && supported && isImage(m);
    }
}
