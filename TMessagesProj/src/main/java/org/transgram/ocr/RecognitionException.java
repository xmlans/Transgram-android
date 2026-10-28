package org.transgram.ocr;

/** Thrown by an engine with a reason the UI can speak. */
public final class RecognitionException extends Exception {
    public final RecognitionError error;
    /** For MODEL_MISSING: the pack id to download; otherwise null. */
    public final String packId;

    public RecognitionException(RecognitionError error, String packId, Throwable cause) {
        super(error + (packId != null ? " (" + packId + ")" : ""), cause);
        this.error = error;
        this.packId = packId;
    }

    public RecognitionException(RecognitionError error, Throwable cause) {
        this(error, null, cause);
    }
}
