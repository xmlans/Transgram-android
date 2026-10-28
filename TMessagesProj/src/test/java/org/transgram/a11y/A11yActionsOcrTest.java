package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class A11yActionsOcrTest {
    @Test public void photoOffersOcrFirstAndBeforeReply() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canReply = true; c.canOcr = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertEquals(A11yAction.OCR, a.get(0));
        assertTrue(a.indexOf(A11yAction.OCR) < a.indexOf(A11yAction.REPLY));
    }

    @Test public void ocrComesAfterPlayback() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.playable = true; c.canOcr = true; c.canReply = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertTrue(a.indexOf(A11yAction.SPEED) < a.indexOf(A11yAction.OCR));
    }

    @Test public void plainTextMessageOffersNoOcr() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canReply = true; c.hasText = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertFalse(a.contains(A11yAction.OCR));
        assertEquals(A11yAction.SELECT, a.get(a.size() - 1));
    }

    @Test public void selectStaysLast() {
        A11yActions.Caps c = new A11yActions.Caps();
        c.canOcr = true; c.canOpenSender = true; c.hasReplyTo = true;
        List<A11yAction> a = A11yActions.forMessage(c);
        assertEquals(A11yAction.SELECT, a.get(a.size() - 1));
        assertTrue(a.contains(A11yAction.OCR));
    }

    @Test public void bindingTableHasOneRowPerActionAndRoundTrips() {
        // Loading the class runs the row-count check; every id must map back to its action.
        for (A11yAction action : A11yAction.values()) {
            assertEquals(action, A11yActionBinding.fromId(A11yActionBinding.id(action)));
        }
    }
}
