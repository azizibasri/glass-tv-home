package com.gathin.carhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DriveTimerTest {
    @Test
    public void countsMovingAndShortStopsOnly() {
        DriveTimer t = new DriveTimer();
        long now = 1_000;
        for (int i = 0; i < 6; i++) t.speed(0f, now += 1_000);      // parked before driving: nothing
        assertEquals(0, t.drivenMs);
        t.speed(50f, now += 1_000);
        for (int i = 0; i < 60; i++) t.speed(50f, now += 1_000);    // one minute moving
        assertEquals(61_000, t.drivenMs);    // and the second it took to pull away
        for (int i = 0; i < 60; i++) t.speed(0f, now += 1_000);     // a one-minute red light counts
        assertEquals(121_000, t.drivenMs);
        for (int i = 0; i < 600; i++) t.speed(0f, now += 1_000);    // parked: only 3 minutes of the stop
        assertEquals(61_000 + DriveTimer.STOP_MAX_MS, t.drivenMs);
        t.speed(50f, now += 60_000);                                 // a gap: nothing for it
        t.speed(50f, now += 1_000);
        assertEquals(62_000 + DriveTimer.STOP_MAX_MS, t.drivenMs);
        t.speed(Float.NaN, now += 1_000);                            // no data: nothing
        t.speed(50f, now += 1_000);
        assertEquals(62_000 + DriveTimer.STOP_MAX_MS, t.drivenMs);
    }

    @Test
    public void resetsAndFormats() {
        DriveTimer t = new DriveTimer();
        t.drivenMs = 5_000;
        t.reset();
        assertEquals(0, t.drivenMs);
        assertEquals("0:05:09", DriveTimer.format(309_000));
        assertEquals("12:00:00", DriveTimer.format(12 * 3_600_000L));
    }
}
