package org.transgram.ocr;

/** Why a recognition failed; the UI turns each one into a spoken sentence. */
public enum RecognitionError {
    /** The model pack is not installed (RecognitionException.packId says which). */
    MODEL_MISSING,
    /** The image file could not be decoded. */
    DECODE_FAILED,
    /** No text was recognised. */
    EMPTY,
    /** Out of memory while recognising. */
    OUT_OF_MEMORY,
    /** The device is below API 24, which onnxruntime-android needs. */
    UNSUPPORTED,
    /** Anything else the engine threw. */
    ENGINE_FAILED
}
