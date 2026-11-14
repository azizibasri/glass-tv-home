package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class CarMeshTest {
    private static float maxY(List<CarMesh.Flat> flats, int part) {
        float best = Float.NEGATIVE_INFINITY;
        for (CarMesh.Flat f : flats) if (f.part == part) for (int i = 1; i < f.xy.length; i += 2) best = Math.max(best, -f.xy[i]);
        return best;
    }

    @Test
    public void projectsFromEveryAngleInsideTheArea() {
        CarMesh m = CarMesh.jetta();
        for (int yaw = 0; yaw < 360; yaw += 15) {
            List<CarMesh.Flat> flats = m.project(yaw, 18f, new float[CarMesh.PARTS], 600, 300);
            assertTrue(flats.size() > 40);
            for (CarMesh.Flat f : flats) {
                for (int i = 0; i < f.xy.length; i += 2) {
                    assertTrue("x " + f.xy[i] + " at yaw " + yaw, f.xy[i] > -5 && f.xy[i] < 605);
                    assertTrue("y " + f.xy[i + 1] + " at yaw " + yaw, f.xy[i + 1] > -5 && f.xy[i + 1] < 305);
                }
            }
            for (int i = 1; i < flats.size(); i++) assertTrue(flats.get(i - 1).depth <= flats.get(i).depth);
        }
    }

    @Test
    public void onlyAboutHalfTheBodyIsDrawn() {
        CarMesh m = CarMesh.jetta();
        int shown = m.project(-60f, 18f, null, 600, 300).size();
        assertTrue("shown " + shown + " of " + m.faces.size(), shown < m.faces.size() * 0.75f);
    }

    @Test
    public void bootLidLiftsWhenOpen() {
        CarMesh m = CarMesh.jetta();
        float[] closed = new float[CarMesh.PARTS], open = new float[CarMesh.PARTS];
        open[CarMesh.TRUNK] = 1f;
        // Seen from the side (yaw 0, left side toward the viewer is -z... use the right side), no tilt.
        float shut = maxY(m.project(0f, 0f, closed, 600, 300), CarMesh.TRUNK);
        float up = maxY(m.project(0f, 0f, open, 600, 300), CarMesh.TRUNK);
        assertTrue("boot " + shut + " -> " + up, up > shut + 20);
    }

    @Test
    public void doorsSwingOutward() {
        CarMesh m = CarMesh.jetta();
        float[] v = {-0.25f, 0.5f, 0.894f};   // rear edge of the front right door
        CarMesh.rotateAboutHinge(v, m.hinge[CarMesh.DOOR_FR], m.maxAngle[CarMesh.DOOR_FR]);
        assertTrue("z " + v[2], v[2] > 1.4f);
        float[] w = {-0.25f, 0.5f, -0.894f};  // and the front left one
        CarMesh.rotateAboutHinge(w, m.hinge[CarMesh.DOOR_FL], m.maxAngle[CarMesh.DOOR_FL]);
        assertTrue("z " + w[2], w[2] < -1.4f);
    }

    @Test
    public void doorBits() {
        boolean[] o = CarMesh.openParts(0b000101, CarMesh.DEFAULT_BITS);
        assertTrue(o[CarMesh.DOOR_FL]);
        assertFalse(o[CarMesh.DOOR_FR]);
        assertTrue(o[CarMesh.DOOR_RL]);
        assertFalse(o[CarMesh.TRUNK]);
        assertEquals(CarMesh.PARTS, o.length);
    }
}
