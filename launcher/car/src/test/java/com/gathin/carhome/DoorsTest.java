package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DoorsTest {
    @Test
    public void defaultBits() {
        boolean[] o = Doors.open(0b000101, Doors.DEFAULT_BITS);
        assertTrue(o[Doors.FRONT_LEFT]);
        assertFalse(o[Doors.FRONT_RIGHT]);
        assertTrue(o[Doors.REAR_LEFT]);
        assertFalse(o[Doors.BOOT]);
        assertEquals(Doors.COUNT, o.length);
    }

    @Test
    public void ownBits() {
        int[] bits = {0, 0x80, 0x40, 0x20, 0x10, 0x08, 0};
        boolean[] o = Doors.open(0x88, bits);
        assertTrue(o[Doors.FRONT_LEFT]);
        assertTrue(o[Doors.BOOT]);
        assertFalse(o[Doors.BONNET]);   // bit 0 means "this box doesn't send it"
    }
}
