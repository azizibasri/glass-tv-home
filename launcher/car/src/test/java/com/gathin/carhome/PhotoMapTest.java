package com.gathin.carhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhotoMapTest {
    @Test
    public void landmarksLandOnThePicture() {
        PhotoMap m = PhotoMap.ownerSheet();
        float[] uv = new float[2];
        m.uv(PhotoMap.SIDE, 1.40f, 0.335f, 0.9f, uv, 0);       // front wheel centre
        assertEquals(800f / 2000f, uv[0], 1e-4f);
        assertEquals(1113f / 1342f, uv[1], 1e-4f);
        m.uv(PhotoMap.FRONT, 0f, 0f, 0f, uv, 0);                // middle of the nose at the ground
        assertEquals(497f / 2000f, uv[0], 1e-4f);
        m.uv(PhotoMap.TOP, 2.35f, 1.4f, 0.905f, uv, 0);          // front right corner from above
        assertEquals(1061f / 2000f, uv[0], 1e-4f);
        assertEquals(839f / 1342f, uv[1], 1e-4f);
    }

    @Test
    public void viewsByDirection() {
        assertEquals(PhotoMap.TOP, PhotoMap.viewFor(0.1f, 0.9f, 0.1f));
        assertEquals(PhotoMap.SIDE, PhotoMap.viewFor(0.2f, 0.1f, -0.95f));
        assertEquals(PhotoMap.FRONT, PhotoMap.viewFor(0.9f, 0.2f, 0.1f));
        assertEquals(PhotoMap.REAR, PhotoMap.viewFor(-0.9f, 0.2f, 0.1f));
        assertEquals(PhotoMap.NONE, PhotoMap.viewFor(0f, -1f, 0f));
    }

    @Test
    public void everyVisibleFaceButTheUndersideGetsPhotoCoordinates() {
        CarMesh car = CarMesh.jetta();
        car.applyPhotos(PhotoMap.ownerSheet());
        int with = 0;
        for (CarMesh.Face f : car.faces) {
            if (f.uv == null) continue;
            with++;
            for (float v : f.uv) assertTrue("uv " + v, v >= -0.01f && v <= 1.01f);
        }
        assertTrue(with > car.faces.size() * 0.7f);
        boolean any = false;
        for (CarMesh.Flat f : car.project(-50f, 14f, null, 600, 300)) any |= f.uv != null;
        assertTrue(any);
        assertNotNull(car.faces.get(0));
    }
}
