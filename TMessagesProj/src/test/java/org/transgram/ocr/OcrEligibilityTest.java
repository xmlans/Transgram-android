package org.transgram.ocr;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OcrEligibilityTest {
    private static OcrEligibility.Media photo() {
        OcrEligibility.Media m = new OcrEligibility.Media();
        m.photo = true;
        return m;
    }

    private static OcrEligibility.Media doc(String mime) {
        OcrEligibility.Media m = new OcrEligibility.Media();
        m.documentMime = mime;
        return m;
    }

    @Test public void photoIsOfferedOnlyWhenEverythingIsOn() {
        assertTrue(OcrEligibility.offer(true, true, true, photo()));
        assertFalse("a11y master switch off", OcrEligibility.offer(false, true, true, photo()));
        assertFalse("OCR switch off", OcrEligibility.offer(true, false, true, photo()));
        assertFalse("below API 24", OcrEligibility.offer(true, true, false, photo()));
    }

    @Test public void imageDocumentsButNotGifSvgOrOtherFiles() {
        assertTrue(OcrEligibility.isImage(doc("image/png")));
        assertTrue(OcrEligibility.isImage(doc("image/jpeg")));
        assertTrue(OcrEligibility.isImage(doc("IMAGE/WEBP")));
        assertFalse(OcrEligibility.isImage(doc("image/gif")));
        assertFalse(OcrEligibility.isImage(doc("image/svg+xml")));
        assertFalse(OcrEligibility.isImage(doc("application/pdf")));
        assertFalse(OcrEligibility.isImage(doc("video/mp4")));
        assertFalse(OcrEligibility.isImage(doc(null)));
        assertFalse(OcrEligibility.isImage(new OcrEligibility.Media()));
        assertFalse(OcrEligibility.isImage(null));
    }

    @Test public void stickersGifsSecretAndUnsentMediaAreNotOffered() {
        OcrEligibility.Media sticker = doc("image/webp");
        sticker.sticker = true;
        assertFalse(OcrEligibility.isImage(sticker));
        OcrEligibility.Media gif = doc("image/png");
        gif.gif = true;
        assertFalse(OcrEligibility.isImage(gif));
        OcrEligibility.Media secret = photo();
        secret.secret = true;
        assertFalse(OcrEligibility.isImage(secret));
        OcrEligibility.Media sending = photo();
        sending.sending = true;
        assertFalse(OcrEligibility.isImage(sending));
    }
}
