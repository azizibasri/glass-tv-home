package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeedMathTest {
    @Test
    public void canWinsWhileFreshThenGpsThenNothing() {
        assertEquals(SpeedMath.CAN, SpeedMath.pick(10_000, 9_000, 9_500));
        assertEquals(SpeedMath.GPS, SpeedMath.pick(10_000, 6_000, 9_500));
        assertEquals(SpeedMath.NONE, SpeedMath.pick(20_000, 6_000, 9_500));
        assertEquals(SpeedMath.NONE, SpeedMath.pick(20_000, 0, 0));
    }

    @Test
    public void unitsAndDisplay() {
        assertEquals(36f, SpeedMath.fromMetresPerSecond(10f, false), 0.01f);
        assertEquals(22.37f, SpeedMath.fromMetresPerSecond(10f, true), 0.01f);
        assertEquals(0, SpeedMath.display(1.6f));
        assertEquals(62, SpeedMath.display(61.6f));
        assertEquals(1f, SpeedMath.fraction(300f, 240f), 0f);
        assertEquals(0.5f, SpeedMath.fraction(120f, 240f), 0f);
    }

    @Test
    public void parsesWhatCanAppsSend() {
        assertEquals(62f, SpeedMath.parse(62, 1f), 0f);
        assertEquals(62.5f, SpeedMath.parse("62.5 km/h", 1f), 0.001f);
        assertEquals(31f, SpeedMath.parse(310, 0.1f), 0.001f);
        assertTrue(Float.isNaN(SpeedMath.parse("n/a", 1f)));
        assertTrue(Float.isNaN(SpeedMath.parse(null, 1f)));
    }

    @Test
    public void smoothingSettles() {
        float v = 0;
        for (int i = 0; i < 200; i++) v = SpeedMath.smooth(v, 100f, 16);
        assertEquals(100f, v, 0f);
        assertTrue(SpeedMath.smooth(0f, 100f, 16) < 20f);
    }
}
