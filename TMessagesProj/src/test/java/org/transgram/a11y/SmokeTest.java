package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class SmokeTest {
    @Test
    public void jvmTestsRun() {
        assertEquals(2, 1 + 1);
    }

    @Test
    public void accessibilityIsOffByDefault() {
        assertFalse(TransGramA11y.DEFAULT_ENABLED);
    }
}
