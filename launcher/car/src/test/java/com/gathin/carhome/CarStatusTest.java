package com.gathin.carhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CarStatusTest {
    private static final String[] NAMES = {"", "Front left door", "Front right door", "Rear left door",
            "Rear right door", "Boot", "Bonnet"};

    private static String status(boolean[] open, boolean known) {
        return Doors.status("%1$s open", "All doors closed", "Door status: no data", NAMES, open, known);
    }

    @Test
    public void namesEachOpening() {
        assertEquals("Front left door open", status(Doors.open(0b1, Doors.DEFAULT_BITS), true));
        assertEquals("Front left door, Boot open", status(Doors.open(0b10001, Doors.DEFAULT_BITS), true));
        assertEquals("All doors closed", status(Doors.open(0, Doors.DEFAULT_BITS), true));
        assertEquals("Door status: no data", status(null, false));
    }
}
