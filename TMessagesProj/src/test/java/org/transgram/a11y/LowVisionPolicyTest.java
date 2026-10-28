package org.transgram.a11y;

import org.junit.Test;
import static org.junit.Assert.*;

public class LowVisionPolicyTest {
    @Test public void scaleRejectsCorruptPreferencesAndBoundsLayoutGrowth() {
        assertEquals(1f, LowVisionPolicy.uiScale(-1), 0f);
        assertEquals(1f, LowVisionPolicy.uiScale(0), 0f);
        assertEquals(1.15f, LowVisionPolicy.uiScale(115), 0.001f);
        assertEquals(1.3f, LowVisionPolicy.uiScale(900), 0.001f);
    }

    @Test public void largerRowsAccommodateTypeAndTouchWithoutChangingDefaults() {
        assertEquals(40, LowVisionPolicy.rowHeight(40, 100, false));
        assertEquals(52, LowVisionPolicy.rowHeight(40, 130, false));
        assertEquals(60, LowVisionPolicy.rowHeight(40, 100, true));
        assertEquals(84, LowVisionPolicy.rowHeight(64, 130, true));
        assertEquals(30, LowVisionPolicy.messageSize(100));
        assertEquals(12, LowVisionPolicy.messageSize(-100));
    }

    @Test public void contrastPreservesAlreadyReadableThemeForegrounds() {
        assertEquals(0xff123456, LowVisionPolicy.readableColor(0xff123456, 0xffffffff));
        assertEquals(0xffeeeeee, LowVisionPolicy.readableColor(0xffeeeeee, 0xff121212));
        assertEquals(0xff000000, LowVisionPolicy.readableColor(0xffaaaaaa, 0xffffffff));
        assertEquals(0xffffffff, LowVisionPolicy.readableColor(0xff333333, 0xff121212));
    }

    @Test public void contrastChecksVisibleCompositeRatherThanTransparentRgb() {
        assertEquals(0xff000000, LowVisionPolicy.readableColor(0x20000000, 0xffffffff));
        assertEquals(0xffffffff, LowVisionPolicy.readableColor(0x20ffffff, 0xff000000));
        assertEquals(21d, LowVisionPolicy.contrast(0xff000000, 0xffffffff), 0.001);
        assertEquals(1d, LowVisionPolicy.contrast(0x00ffffff, 0xff000000), 0.001);
    }

    @Test public void fixedHeightLabelsHaveTheirOwnConservativeCap() {
        assertEquals(20f, LowVisionPolicy.compactText(20, 100), .001f);
        assertEquals(23f, LowVisionPolicy.compactText(20, 130), .001f);
        assertEquals(23f, LowVisionPolicy.compactText(20, Integer.MAX_VALUE), .001f);
    }

    @Test public void contrastOverlayIsIdempotentAcrossLightDarkAndCustomSurfaces() {
        for (int surface : new int[]{0xff000000, 0xffffffff, 0xff121212, 0xff777777, 0xff106060, 0xffeeddaa}) {
            for (int text : new int[]{0xffaaaaaa, 0x20888888, 0xff334455, 0xff000000, 0xffffffff}) {
                int adjusted = LowVisionPolicy.readableColor(text, surface);
                assertTrue(LowVisionPolicy.contrast(adjusted, surface) >= 4.5);
                assertEquals(adjusted, LowVisionPolicy.readableColor(adjusted, surface));
            }
        }
    }
}
