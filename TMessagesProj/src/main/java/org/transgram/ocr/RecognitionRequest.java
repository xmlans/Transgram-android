package org.transgram.ocr;

/** One thing to recognise: which kind of result, for which message, from which local media file. */
public final class RecognitionRequest {
    /** TransGram only does OCR; the enum keeps the on-disk layout (one directory per kind). */
    public enum Kind {
        OCR("ocr", "txt");

        /** Sub-directory name below the result directory. */
        public final String dirName;
        public final String extension;

        Kind(String dirName, String extension) {
            this.dirName = dirName;
            this.extension = extension;
        }
    }

    public final Kind kind;
    public final int account;
    public final long dialogId;
    public final int messageId;
    /** Absolute path of the downloaded photo / image file. */
    public final String mediaPath;

    public RecognitionRequest(Kind kind, int account, long dialogId, int messageId, String mediaPath) {
        this.kind = kind;
        this.account = account;
        this.dialogId = dialogId;
        this.messageId = messageId;
        this.mediaPath = mediaPath;
    }

    /** Shortcut for an OCR request. */
    public static RecognitionRequest ocr(int account, long dialogId, int messageId, String mediaPath) {
        return new RecognitionRequest(Kind.OCR, account, dialogId, messageId, mediaPath);
    }

    /** Identifies the result: kind + account + dialog + message (the media path is not part of it). */
    public String key() {
        return kind.dirName + ":" + account + "_" + dialogId + "_" + messageId;
    }

    /** File name used by RecognitionResultStore. */
    public String fileName() {
        return account + "_" + dialogId + "_" + messageId + "." + kind.extension;
    }
}
