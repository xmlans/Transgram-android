package org.transgram.a11y;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

public class SpeechSettingsTest {
    @Test public void standardPresetSkipsOnlyStats() {
        SpeechSettings s = SpeechSettings.forPreset(SpeechPreset.STANDARD);
        assertTrue(s.isOn(SpeechField.REACTIONS));
        assertFalse(s.isOn(SpeechField.STATS));
        assertFalse(s.verboseExtras());
    }

    @Test public void briefPreset() {
        assertEquals(Arrays.asList(SpeechField.SENDER, SpeechField.TYPE, SpeechField.BODY, SpeechField.STATUS),
                SpeechSettings.forPreset(SpeechPreset.BRIEF).enabledInOrder());
    }

    @Test public void verboseHasEverythingAndExtras() {
        SpeechSettings s = SpeechSettings.forPreset(SpeechPreset.VERBOSE);
        assertEquals(SpeechField.values().length, s.enabledInOrder().size());
        assertTrue(s.verboseExtras());
    }

    @Test public void roundTripKeepsOrderAndToggles() {
        SpeechSettings s = SpeechSettings.forPreset(SpeechPreset.STANDARD)
                .withOrder(Arrays.asList(SpeechField.BODY, SpeechField.SENDER))
                .withField(SpeechField.TIME, false);
        SpeechSettings back = SpeechSettings.parse(s.serialize());
        assertEquals(s.enabledInOrder(), back.enabledInOrder());
        assertEquals(SpeechField.BODY, back.enabledInOrder().get(0));
        assertFalse(back.isOn(SpeechField.TIME));
    }

    @Test public void parseGarbageFallsBackToStandard() {
        String std = SpeechSettings.forPreset(SpeechPreset.STANDARD).serialize();
        assertEquals(std, SpeechSettings.parse("%%bad").serialize());
        assertEquals(std, SpeechSettings.parse(null).serialize());
    }

    @Test public void parseIgnoresUnknownFieldsAndAppendsMissing() {
        SpeechSettings s = SpeechSettings.parse("v1;STANDARD;BODY+,NOPE+,SENDER-");
        assertEquals(SpeechField.BODY, s.enabledInOrder().get(0));
        assertFalse(s.isOn(SpeechField.SENDER));
        assertTrue(s.isOn(SpeechField.TIME));
    }

    @Test public void moveSwapsNeighbours() {
        SpeechSettings s = SpeechSettings.forPreset(SpeechPreset.STANDARD).move(SpeechField.TYPE, -1);
        assertEquals(SpeechField.TYPE, s.order().get(0));
        assertEquals(SpeechField.SENDER, s.order().get(1));
        assertEquals(s.order(), s.move(SpeechField.TYPE, -1).order()); // already first: unchanged
    }
}
