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

    @Test
    public void turboBoost() {
        // 190 kPa in the manifold at 100 kPa outside: 0.9 bar of boost.
        assertEquals(0.9f, ObdParser.boostBar(ObdParser.kpa(ObdParser.data("41 0B BE", "010B")),
                ObdParser.kpa(ObdParser.data("41 33 64", "0133"))), 0.001f);
        // Idling: 35 kPa, vacuum. No barometer yet: standard air.
        assertEquals(-0.663f, ObdParser.boostBar(35f, Float.NaN), 0.001f);
        assertTrue(Float.isNaN(ObdParser.boostBar(Float.NaN, 100f)));
        assertEquals("+0.90 bar", SpeedMath.boostText(0.9f, false));
        assertEquals("+13.1 psi", SpeedMath.boostText(0.9f, true));
        assertEquals(0.4f, SpeedMath.fractionOf(0f, 1.5f), 0.001f);
        assertEquals(1f, SpeedMath.boostFraction(3f, 1.5f), 0f);
    }

    @Test
    public void torqueFromEngineLoad() {
        // 50 % load on a 250 Nm engine: about 125 Nm.
        assertEquals(50.2f, ObdParser.engineLoad(ObdParser.data("41 04 80", "0104")), 0.1f);
        assertEquals(125f, ObdParser.torqueFromLoad(50f, 250f), 0.001f);
        assertEquals(250f, ObdParser.torqueFromLoad(130f, 250f), 0.001f);
        assertTrue(Float.isNaN(ObdParser.torqueFromLoad(Float.NaN, 250f)));
    }

    @org.junit.Test
    public void engineHeaderOnlyOnElevenBitCan() {
        org.junit.Assert.assertEquals("7E0", ObdParser.engineHeader("A6\r\r"));
        org.junit.Assert.assertEquals("7E0", ObdParser.engineHeader("6"));
        org.junit.Assert.assertEquals("7E0", ObdParser.engineHeader("A8"));
        org.junit.Assert.assertNull(ObdParser.engineHeader("A7"));
        org.junit.Assert.assertNull(ObdParser.engineHeader("A3"));
        org.junit.Assert.assertNull(ObdParser.engineHeader(null));
        org.junit.Assert.assertTrue(ObdParser.notUnderstood("?\r\r"));
        org.junit.Assert.assertFalse(ObdParser.notUnderstood("410B64"));
        org.junit.Assert.assertTrue(ObdParser.ok("OK\r"));
    }

    @org.junit.Test
    public void boostFromOneReplyRequest() {
        int[] b = ObdParser.data("410BC8\r\r", "010B");
        org.junit.Assert.assertEquals(0.987f, ObdParser.boostBar(ObdParser.kpa(b), 101.3f), 0.001f);
    }
}
