package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TripMeterTest {
    @Test
    public void addsUpSpeedAndSkipsGaps() {
        TripMeter t = new TripMeter();
        long now = 1_000;
        t.speed(60f, now);
        for (int i = 0; i < 60; i++) t.speed(60f, now += 1_000);   // one minute at 60 km/h
        assertEquals(1.0f, t.distance(), 0.001f);
        t.speed(60f, now += 60_000);                                 // a minute away from Home: not counted
        assertEquals(1.0f, t.distance(), 0.001f);
        t.speed(1f, now += 1_000);                                   // standing still: GPS noise ignored
        assertEquals(1.0f, t.distance(), 0.02f);
        assertFalse(t.fromOdometer());
    }

    @Test
    public void odometerWinsAndResets() {
        TripMeter t = new TripMeter();
        t.odometer(52_000f);
        t.odometer(52_012.5f);
        assertTrue(t.fromOdometer());
        assertEquals(12.5f, t.distance(), 0.001f);
        t.reset();
        assertEquals(0f, t.distance(), 0f);
        t.odometer(52_020f);
        assertEquals(7.5f, t.distance(), 0.001f);
    }

    @Test
    public void formats() {
        assertEquals("0.0", TripMeter.format(0f));
        assertEquals("123.4", TripMeter.format(123.44f));
        assertEquals("1235", TripMeter.format(1234.6f));
    }
}
