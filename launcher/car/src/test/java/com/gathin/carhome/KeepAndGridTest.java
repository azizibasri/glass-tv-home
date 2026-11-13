package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class KeepAndGridTest {
    @Test
    public void carAppsAreNeverClosed() {
        for (String pkg : new String[]{"com.zjinnova.zlink", "com.syu.canbus", "com.fyt.camera", "com.example.avm360",
                "com.nforetek.bt", "com.android.systemui", "android", "com.google.android.gms", "com.syu.radio"}) {
            assertTrue(pkg, Keep.isProtected(pkg, Collections.emptyList()));
        }
    }

    @Test
    public void ordinaryAppsCanBeClosed() {
        for (String pkg : new String[]{"com.netflix.mediaclient", "com.spotify.music", "com.waze",
                "com.google.android.youtube", "com.android.chrome"}) {
            assertFalse(pkg, Keep.isProtected(pkg, Collections.emptyList()));
        }
    }

    @Test
    public void extraKeepPartsWork() {
        assertTrue(Keep.isProtected("com.waze", Arrays.asList("WAZE")));
        assertTrue(Keep.isProtected("", null));
    }

    @Test
    public void gridFitsCommonHeadUnits() {
        // 1024x600 at mdpi, rail and margins taken off: about 880 x 550 dp
        Grid small = Grid.fit(880, 550, 1f, 0);
        assertEquals(5, small.columns);
        assertEquals(3, small.rows);
        // 1280x720 at 1.5x: about 750 x 430 dp
        Grid hd = Grid.fit(1125, 645, 1.5f, 0);
        assertEquals(5, hd.columns);
        assertEquals(2, hd.rows);
        // Forced columns win, rows still fit
        Grid forced = Grid.fit(1125, 645, 1.5f, 4);
        assertEquals(4, forced.columns);
        assertEquals(1, forced.pages(8));
        assertEquals(2, forced.pages(9));
        assertEquals(1, forced.pages(0));
    }
}
