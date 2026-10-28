package org.transgram.ocr;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ProgressStepsTest {
    @Test public void announcesEachQuarterOnce() {
        ProgressSteps p = new ProgressSteps();
        assertEquals(-1, p.update(10, 100));
        assertEquals(25, p.update(25, 100));
        assertEquals(-1, p.update(30, 100));
        assertEquals(50, p.update(51, 100));
        assertEquals(75, p.update(76, 100));
        assertEquals(100, p.update(100, 100));
        assertEquals(-1, p.update(100, 100));
    }

    @Test public void jumpingOverMilestonesAnnouncesTheLatest() {
        ProgressSteps p = new ProgressSteps();
        assertEquals(75, p.update(80, 100));
        assertEquals(-1, p.update(81, 100));
    }

    @Test public void unknownTotalAndResetWork() {
        ProgressSteps p = new ProgressSteps();
        assertEquals(-1, p.update(5, 0));
        assertEquals(25, p.update(26, 100));
        p.reset();
        assertEquals(25, p.update(26, 100));
    }
}
