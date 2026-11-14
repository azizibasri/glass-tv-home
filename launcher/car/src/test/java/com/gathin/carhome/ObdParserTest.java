package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ObdParserTest {
    @Test
    public void readsCommonReplies() {
        assertEquals(62f, ObdParser.speed(ObdParser.data("41 0D 3E \r\r>", "010D")), 0f);
        assertEquals(2350f, ObdParser.rpm(ObdParser.data("410C24B8\r>", "010C")), 0f);
        assertEquals(90f, ObdParser.coolant(ObdParser.data("41 05 82", "0105")), 0f);
        assertEquals(50.2f, ObdParser.fuelPercent(ObdParser.data("41 2F 80", "012F")), 0.1f);
        assertEquals(25f, ObdParser.torquePercent(ObdParser.data("41 62 96", "0162")), 0f);
        assertEquals(250f, ObdParser.torqueReference(ObdParser.data("41 63 00 FA", "0163")), 0f);
    }

    @Test
    public void handlesEchoSearchingAndHeaders() {
        assertEquals(62f, ObdParser.speed(ObdParser.data("010D\rSEARCHING...\r41 0D 3E\r\r>", "010D")), 0f);
        assertEquals(62f, ObdParser.speed(ObdParser.data("7E8 03 41 0D 3E\r>", "010D")), 0f);
    }

    @Test
    public void noDataIsNull() {
        assertNull(ObdParser.data("NO DATA\r\r>", "010D"));
        assertNull(ObdParser.data("?\r>", "010D"));
        assertNull(ObdParser.data("41 0C 1A F8", "010D"));
        assertTrue(Float.isNaN(ObdParser.speed(null)));
    }

    @Test
    public void rangeFromTank() {
        // Half of a 55 L tank at 7.5 L/100 km is about 367 km.
        assertEquals(366.7f, ObdParser.range(50f, 55f, 7.5f), 0.1f);
        assertTrue(Float.isNaN(ObdParser.range(Float.NaN, 55f, 7.5f)));
    }
}
