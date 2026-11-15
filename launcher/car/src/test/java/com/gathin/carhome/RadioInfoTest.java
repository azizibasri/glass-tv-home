package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RadioInfoTest {
    @Test
    public void fmFrequencyInManyWritings() {
        assertEquals("FM 98.8", RadioInfo.parse("98.80MHz").headline());
        assertEquals("FM 98.8", RadioInfo.parse("FM 98.8").headline());
        assertEquals("FM 101.1", RadioInfo.parse("FM1  101,10 MHz").headline());
        assertEquals("FM 88.25", RadioInfo.parse("88.25").headline());
    }

    @Test
    public void channelNumber() {
        RadioInfo r = RadioInfo.parse("FM 98.8", "CH 3");
        assertEquals("FM 98.8", r.headline());
        assertEquals(3, r.channel);
        assertEquals("CH 3", r.detail());
        assertEquals(5, RadioInfo.parse("P5 104.9").channel);
        assertEquals("CH 12", RadioInfo.parse("Preset 12").headline());
    }

    @Test
    public void stationName() {
        RadioInfo r = RadioInfo.parse("Hitz FM", "98.8 MHz", "CH 2");
        assertEquals("FM 98.8", r.headline());
        assertEquals("CH 2 · Hitz FM", r.detail());
    }

    @Test
    public void amOnlyWhenSaid() {
        assertEquals("AM 1035", RadioInfo.parse("AM 1035 kHz").headline());
        assertNull(RadioInfo.parse("Song 1035").headline());
    }

    @Test
    public void nothingFound() {
        assertTrue(RadioInfo.parse("Radio", null).isEmpty());
        assertNull(RadioInfo.parse("Radio").station);
    }

    @Test
    public void fromBroadcastNumber() {
        assertEquals("FM 98.8", RadioInfo.fromNumber(9880, 0.01f).headline());
        assertEquals("FM 104.75", RadioInfo.fromNumber(104750, 0.001f).headline());
        assertEquals("AM 1035", RadioInfo.fromNumber(1035, 1f).headline());
        assertTrue(RadioInfo.fromNumber(5, 1f).isEmpty());
    }

    @Test
    public void radioPackages() {
        assertTrue(RadioInfo.isRadioPackage("com.syu.radio"));
        assertTrue(RadioInfo.isRadioPackage("com.android.fmradio"));
        assertTrue(RadioInfo.isRadioPackage("com.microntek.radio"));
        assertFalse(RadioInfo.isRadioPackage("com.spotify.music"));
    }
}
