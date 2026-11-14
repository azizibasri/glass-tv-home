package com.gathin.carhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CanFinderTest {
    @Test
    public void spotsCarApps() {
        for (String p : new String[]{"com.syu.canbus", "com.fyt.car", "com.example.canbox", "com.hiworld.canbus",
                "com.microntek.canbusdisplay", "com.android.can", "com.ts.can.service", "com.unit.mcu"}) {
            assertTrue(p, CanFinder.looksLikeCan(p));
        }
    }

    @Test
    public void ignoresLookAlikes() {
        for (String p : new String[]{"com.google.android.apps.scanner", "com.king.candycrush", "com.spotify.music",
                "com.android.chrome", "com.vulcan.app"}) {
            assertFalse(p, CanFinder.looksLikeCan(p));
        }
    }

    @Test
    public void spotsCarAppsByName() {
        assertTrue(CanFinder.labelLooksLikeCan("Car Set"));
        assertTrue(CanFinder.labelLooksLikeCan("CanBus"));
        assertTrue(CanFinder.labelLooksLikeCan("Car Info"));
        assertFalse(CanFinder.labelLooksLikeCan("Calendar"));
        assertFalse(CanFinder.labelLooksLikeCan("Spotify"));
        assertFalse(CanFinder.labelLooksLikeCan(null));
    }
}
