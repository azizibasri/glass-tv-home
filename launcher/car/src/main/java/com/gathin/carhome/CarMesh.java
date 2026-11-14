package com.gathin.carhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A 3D model of a white seventh-generation Jetta (2019 on), built from code after the owner's photos: a
 * lofted body with the crease along its sides, a fastback glasshouse with B and C pillars, a wide black grille
 * with three bars and slim LED headlights, black side skirts, mirrors, front lip, diffuser and boot spoiler,
 * wrap-round tail lights, twin exhausts and dark ten-spoke wheels. Doors (with their windows), boot lid and
 * bonnet swing open on their hinges. No maker's badges or names.
 *
 * Plain Java (no Android), so the geometry is unit tested. {@link #project} turns it into flat-shaded 2D
 * polygons sorted back to front, which a view or a canvas simply fills in order.
 *
 * Axes: x forward (front at +2.3), y up (ground at 0), z to the car's right. Left = -z.
 */
final class CarMesh {
    static final int BODY = 0, DOOR_FL = 1, DOOR_FR = 2, DOOR_RL = 3, DOOR_RR = 4, TRUNK = 5, HOOD = 6;
    static final int PARTS = 7;

    static final int WHITE = 0xFFEFF1F4, GLASS = 0xFF1B222B, BLACK = 0xFF16181C, BAY = 0xFF3A3D44,
            TYRE = 0xFF111214, RIM_DARK = 0xFF2B2D31, SPOKE = 0xFF5D6168, LAMP = 0xFF59616C, LED = 0xFFF2F8FF,
            TAIL = 0xFF8E1018, TAIL_LINE = 0xFFE0303A, BAR = 0xFF3A3F46, CHROME = 0xFFC9CED6, INSIDE = 0xFF8E939B;

    /** One flat polygon: xyz points, colour, the part it moves with, and how it is drawn. */
    static final class Face {
        final float[] p;
        final int color, part;
        final boolean twoSided;
        /** Pulled toward the viewer when sorting, so decals (lamps, grille) draw over the panel under them. */
        final float bias;

        Face(float[] p, int color, int part, boolean twoSided, float bias) {
            this.p = twoSided ? p : outward(p);
            this.color = color;
            this.part = part;
            this.twoSided = twoSided;
            this.bias = bias;
        }
    }

    /** A projected polygon: screen xy points and its shaded colour; {@code part} for highlighting. */
    static final class Flat {
        final float[] xy;
        final int color, part;
        final float depth;

        Flat(float[] xy, int color, int part, float depth) {
            this.xy = xy;
            this.color = color;
            this.part = part;
            this.depth = depth;
        }
    }

    /**
     * Puts a one-sided face's points in the order that makes it face out of the car (away from a point
     * inside the cabin), so faces turned away from the viewer can be skipped.
     */
    static float[] outward(float[] p) {
        int n = p.length / 3;
        float nx = 0, ny = 0, nz = 0, cx = 0, cy = 0, cz = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            nx += (p[3 * i + 1] - p[3 * j + 1]) * (p[3 * i + 2] + p[3 * j + 2]);
            ny += (p[3 * i + 2] - p[3 * j + 2]) * (p[3 * i] + p[3 * j]);
            nz += (p[3 * i] - p[3 * j]) * (p[3 * i + 1] + p[3 * j + 1]);
            cx += p[3 * i];
            cy += p[3 * i + 1];
            cz += p[3 * i + 2];
        }
        cx /= n;
        cy /= n;
        cz /= n;
        if (nx * cx + ny * (cy - 0.6f) + nz * cz >= 0) return p;
        float[] r = new float[p.length];
        for (int i = 0; i < n; i++) System.arraycopy(p, 3 * (n - 1 - i), r, 3 * i, 3);
        return r;
    }

    final List<Face> faces = new ArrayList<>();
    /** Hinge point and axis for each part, and its fully open angle in degrees. */
    final float[][] hinge = new float[PARTS][];
    final float[] maxAngle = new float[PARTS];

    // ---- Shape: a seventh-generation Jetta (2019 on), 4.70 m long, 1.80 m wide, 1.46 m tall ----

    /** Body stations: x, bottom, top (bonnet / shoulder / boot), half width. Doors and lids start and end on one. */
    private static final float[][] STATIONS = {
            {-2.35f, 0.32f, 0.98f, 0.80f}, {-2.33f, 0.26f, 1.02f, 0.86f}, {-2.20f, 0.22f, 1.05f, 0.885f},
            {-1.75f, 0.20f, 1.05f, 0.895f}, {-1.29f, 0.19f, 1.04f, 0.90f}, {-1.20f, 0.18f, 1.035f, 0.90f},
            {-0.25f, 0.18f, 1.00f, 0.90f}, {0.85f, 0.18f, 0.975f, 0.90f}, {0.95f, 0.19f, 0.96f, 0.90f},
            {1.40f, 0.20f, 0.89f, 0.90f}, {1.90f, 0.20f, 0.83f, 0.89f}, {2.20f, 0.21f, 0.80f, 0.885f},
            {2.33f, 0.24f, 0.78f, 0.86f}, {2.35f, 0.30f, 0.74f, 0.80f}};

    /** Front and rear door bands, bonnet and boot bands, along x. */
    private static final float FD_FRONT = 0.85f, FD_REAR = -0.25f, RD_REAR = -1.20f,
            HOOD_REAR = 0.95f, HOOD_FRONT = 2.20f, BOOT_FRONT = -1.75f, BOOT_REAR = -2.33f;

    /** Glasshouse key points: x and roof height (a long fastback slope to a short boot). */
    private static final float[][] ROOF = {{0.95f, 0.99f}, {0.60f, 1.20f}, {0.20f, 1.40f}, {0.0f, 1.455f},
            {-0.40f, 1.46f}, {-0.80f, 1.43f}, {-1.20f, 1.33f}, {-1.40f, 1.25f}, {-1.75f, 1.06f}};
    /** Glasshouse stations; a B pillar between -0.20 and -0.30, a body-colour C pillar behind -1.40. */
    private static final float[] CABIN_X = {0.95f, 0.85f, 0.60f, 0.20f, 0.0f, -0.20f, -0.30f, -0.80f, -1.20f,
            -1.40f, -1.75f};

    static CarMesh jetta() {
        CarMesh m = new CarMesh();
        m.buildBody();
        m.buildCabin();
        m.buildFront();
        m.buildRear();
        m.buildMirrors();
        for (float[] w : new float[][]{{1.40f, -1}, {1.40f, 1}, {-1.29f, -1}, {-1.29f, 1}}) m.wheel(w[0], w[1]);
        m.hinge[DOOR_FL] = new float[]{FD_FRONT, 0, -0.91f, 0, 1, 0};
        m.hinge[DOOR_FR] = new float[]{FD_FRONT, 0, 0.91f, 0, 1, 0};
        m.hinge[DOOR_RL] = new float[]{FD_REAR, 0, -0.91f, 0, 1, 0};
        m.hinge[DOOR_RR] = new float[]{FD_REAR, 0, 0.91f, 0, 1, 0};
        m.maxAngle[DOOR_FL] = m.maxAngle[DOOR_RL] = -58f;
        m.maxAngle[DOOR_FR] = m.maxAngle[DOOR_RR] = 58f;
        m.hinge[HOOD] = new float[]{HOOD_REAR, 0.972f, 0, 0, 0, 1};
        m.maxAngle[HOOD] = 50f;
        m.hinge[TRUNK] = new float[]{BOOT_FRONT, 1.062f, 0, 0, 0, 1};
        m.maxAngle[TRUNK] = -65f;
        return m;
    }

    /**
     * One body cross-section as a closed loop of (z, y): from the bottom centre up the right side (sill,
     * lower side, the crease along the side, shoulder, bonnet or boot edge) to the top centre and down the
     * left side. 16 points.
     */
    static float[] ring(float[] st) {
        float b = st[1], t = st[2], w = st[3];
        float crease = t - 0.13f, shoulder = t - 0.03f;
        float[][] half = {{0, b}, {w - 0.12f, b}, {w - 0.03f, b + 0.06f}, {w, b + 0.22f}, {w + 0.008f, crease},
                {w - 0.015f, shoulder - 0.05f}, {w - 0.06f, shoulder}, {w - 0.20f, t - 0.005f}, {0, t + 0.015f}};
        float[] r = new float[32];
        int k = 0;
        for (int i = 0; i < 9; i++) {
            r[k++] = half[i][0];
            r[k++] = half[i][1];
        }
        for (int i = 7; i >= 1; i--) {
            r[k++] = -half[i][0];
            r[k++] = half[i][1];
        }
        return r;
    }

    private static boolean between(float x, float a, float b) {
        return x > Math.min(a, b) && x < Math.max(a, b);
    }

    private void buildBody() {
        for (int i = 0; i + 1 < STATIONS.length; i++) {
            float x0 = STATIONS[i][0], x1 = STATIONS[i + 1][0], mid = (x0 + x1) / 2;
            float[] r0 = ring(STATIONS[i]), r1 = ring(STATIONS[i + 1]);
            boolean front = between(mid, FD_FRONT, FD_REAR), rear = between(mid, FD_REAR, RD_REAR);
            boolean hood = between(mid, HOOD_REAR, HOOD_FRONT), boot = between(mid, BOOT_FRONT, BOOT_REAR);
            boolean skirt = between(mid, -1.85f, 1.95f);
            for (int k = 0; k < 16; k++) {
                int a = k, b = (k + 1) % 16;
                boolean right = k <= 7;
                boolean top = k == 6 || k == 7 || k == 8 || k == 9;
                boolean side = k == 2 || k == 3 || k == 4 || k == 11 || k == 12 || k == 13;
                int color = WHITE;
                if (k == 0 || k == 15) color = BLACK;                              // underside
                else if ((k == 1 || k == 14) && skirt) color = BLACK;              // black side skirts
                else if (top && (hood || boot)) color = BAY;                        // under the lids
                else if (side && (front || rear)) color = INSIDE;                   // behind the doors
                float[] quad = {x0, r0[2 * a + 1], r0[2 * a], x1, r1[2 * a + 1], r1[2 * a],
                        x1, r1[2 * b + 1], r1[2 * b], x0, r0[2 * b + 1], r0[2 * b]};
                faces.add(new Face(quad, color, BODY, false, 0));
                int part = -1;
                if (top && hood) part = HOOD;
                else if (top && boot) part = TRUNK;
                else if (side && front) part = right ? DOOR_FR : DOOR_FL;
                else if (side && rear) part = right ? DOOR_RR : DOOR_RL;
                if (part >= 0) {
                    float[] panel = outward(quad.clone());
                    for (int q = 0; q < 4; q++) {
                        if (top) panel[3 * q + 1] += 0.01f;
                        else panel[3 * q + 2] += right ? 0.006f : -0.006f;
                    }
                    faces.add(new Face(panel, WHITE, part, true, 0.02f));
                }
            }
        }
        cap(STATIONS[0]);
        cap(STATIONS[STATIONS.length - 1]);
    }

    /** Closes the nose or the tail with the station's own outline. */
    private void cap(float[] st) {
        float[] r = ring(st);
        float[] p = new float[48];
        for (int k = 0; k < 16; k++) {
            p[3 * k] = st[0];
            p[3 * k + 1] = r[2 * k + 1];
            p[3 * k + 2] = r[2 * k];
        }
        faces.add(new Face(p, WHITE, BODY, false, 0));
    }

    static float roofAt(float x) {
        for (int i = 0; i + 1 < ROOF.length; i++) {
            float[] a = ROOF[i], b = ROOF[i + 1];
            if (x <= a[0] && x >= b[0]) return a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0]);
        }
        return x > 0 ? ROOF[0][1] : ROOF[ROOF.length - 1][1];
    }

    /** Glasshouse section at x: base, upper side, roof edge, roof centre (z, y pairs, right side only). */
    private static float[][] cabinHalf(float x) {
        float base = 0.97f + (1.05f - 0.97f) * (0.95f - x) / 2.70f;
        float roof = roofAt(x);
        float h = Math.max(0.02f, roof - base);
        float t = Math.max(0f, Math.min(1f, (roof - 1.0f) / 0.46f));      // 0 at the screens' foot, 1 at the roof
        float bw = 0.82f, rw = 0.72f - 0.08f * t;
        return new float[][]{{bw, base}, {rw + 0.025f, base + h * 0.93f}, {rw, roof - 0.01f}, {0, roof + 0.02f}};
    }

    private void buildCabin() {
        for (int i = 0; i + 1 < CABIN_X.length; i++) {
            float xa = CABIN_X[i], xb = CABIN_X[i + 1], mid = (xa + xb) / 2;
            float[][] a = cabinHalf(xa), b = cabinHalf(xb);
            boolean roofBand = mid < 0.25f && mid > -0.85f;
            boolean bPillar = between(mid, -0.20f, -0.30f), cPillar = mid < -1.40f;
            for (int side = -1; side <= 1; side += 2) {
                // window (base to upper side), then roof rail (upper side to roof edge), then the roof half
                for (int s = 0; s < 3; s++) {
                    float[] q = {xa, a[s][1], side * a[s][0], xb, b[s][1], side * b[s][0],
                            xb, b[s + 1][1], side * b[s + 1][0], xa, a[s + 1][1], side * a[s + 1][0]};
                    int color = GLASS, part = BODY;
                    boolean two = false;
                    if (s == 0) {
                        if (bPillar) color = BLACK;
                        else if (cPillar) color = WHITE;
                        else if (between(mid, FD_FRONT, -0.20f)) {
                            part = side > 0 ? DOOR_FR : DOOR_FL;
                            two = true;
                        } else if (between(mid, -0.30f, RD_REAR)) {
                            part = side > 0 ? DOOR_RR : DOOR_RL;
                            two = true;
                        }
                    } else if (s == 1) {
                        color = roofBand || cPillar ? WHITE : GLASS;
                    } else {
                        color = roofBand ? WHITE : GLASS;
                    }
                    if (two) {
                        q = outward(q);
                        for (int k = 0; k < 4; k++) q[3 * k + 2] += side * 0.004f;
                    }
                    faces.add(new Face(q, color, part, two, two ? 0.02f : 0));
                }
            }
        }
    }

    private void decal(float[] p, int color) {
        faces.add(new Face(p, color, BODY, false, 0.04f));
    }

    /** A quad on the nose (x = fx), given as z,y pairs; mirrored for the left side when {@code both}. */
    private void nose(float fx, float[] zy, int color, boolean both) {
        for (int side = 1; side >= (both ? -1 : 1); side -= 2) {
            float[] p = new float[zy.length / 2 * 3];
            for (int i = 0; i < zy.length / 2; i++) {
                p[3 * i] = fx;
                p[3 * i + 1] = zy[2 * i + 1];
                p[3 * i + 2] = side * zy[2 * i];
            }
            decal(p, color);
        }
    }

    private void buildFront() {
        float fx = 2.353f;
        // Wide black grille with three bars, slim LED headlights that run into it, lower intake, corner vents.
        nose(fx, new float[]{0.50f, 0.47f, -0.50f, 0.47f, -0.53f, 0.645f, 0.53f, 0.645f}, BLACK, false);
        for (float y : new float[]{0.51f, 0.555f, 0.60f}) {
            faces.add(new Face(new float[]{fx + 0.002f, y, 0.50f, fx + 0.002f, y, -0.50f, fx + 0.002f, y + 0.012f, -0.50f,
                    fx + 0.002f, y + 0.012f, 0.50f}, BAR, BODY, false, 0.05f));
        }
        nose(fx, new float[]{0.50f, 0.625f, 0.80f, 0.645f, 0.80f, 0.738f, 0.50f, 0.705f}, LAMP, true);
        nose(fx + 0.002f, new float[]{0.52f, 0.637f, 0.79f, 0.656f, 0.79f, 0.674f, 0.52f, 0.655f}, LED, true);
        nose(fx + 0.002f, new float[]{0.58f, 0.684f, 0.66f, 0.690f, 0.66f, 0.722f, 0.58f, 0.712f}, BLACK, true);
        nose(fx + 0.002f, new float[]{0.68f, 0.692f, 0.76f, 0.700f, 0.76f, 0.728f, 0.68f, 0.722f}, BLACK, true);
        nose(fx, new float[]{0.62f, 0.32f, -0.62f, 0.32f, -0.58f, 0.43f, 0.58f, 0.43f}, BLACK, false);
        nose(fx, new float[]{0.64f, 0.34f, 0.78f, 0.34f, 0.78f, 0.47f, 0.70f, 0.47f}, BLACK, true);
        // Headlights wrap a little onto the wings.
        for (int side = -1; side <= 1; side += 2) {
            decal(new float[]{2.33f, 0.645f, side * 0.866f, 2.05f, 0.70f, side * 0.894f, 2.05f, 0.755f, side * 0.894f,
                    2.33f, 0.74f, side * 0.866f}, LAMP);
            decal(new float[]{2.33f, 0.655f, side * 0.868f, 2.08f, 0.705f, side * 0.896f, 2.08f, 0.716f, side * 0.896f,
                    2.33f, 0.668f, side * 0.868f}, LED);
        }
        // Black front lip.
        faces.add(new Face(new float[]{2.10f, 0.19f, -0.86f, 2.10f, 0.19f, 0.86f, 2.43f, 0.19f, 0.80f, 2.43f, 0.19f, -0.80f},
                BLACK, BODY, true, 0.02f));
        faces.add(new Face(new float[]{2.43f, 0.19f, -0.80f, 2.43f, 0.19f, 0.80f, 2.36f, 0.30f, 0.80f, 2.36f, 0.30f, -0.80f},
                BLACK, BODY, true, 0.02f));
    }

    private void buildRear() {
        float rx = -2.353f;
        // Wide tail lights, red reflectors, black diffuser with twin exhausts.
        nose(rx, new float[]{0.22f, 0.79f, 0.82f, 0.77f, 0.82f, 0.935f, 0.22f, 0.935f}, TAIL, true);
        nose(rx - 0.002f, new float[]{0.25f, 0.84f, 0.80f, 0.83f, 0.80f, 0.848f, 0.25f, 0.858f}, TAIL_LINE, true);
        nose(rx, new float[]{0.64f, 0.47f, 0.76f, 0.47f, 0.76f, 0.50f, 0.64f, 0.50f}, TAIL, true);
        nose(rx, new float[]{0.80f, 0.32f, -0.80f, 0.32f, -0.80f, 0.42f, 0.80f, 0.42f}, BLACK, false);
        for (int side = -1; side <= 1; side += 2) {
            // the tail lights run round onto the rear wings
            decal(new float[]{-2.05f, 0.84f, side * 0.894f, -2.33f, 0.78f, side * 0.866f, -2.33f, 0.935f, side * 0.866f,
                    -2.05f, 0.95f, side * 0.894f}, TAIL);
            exhaust(rx - 0.004f, side * 0.55f);
        }
        // Black lip spoiler on the boot edge.
        faces.add(new Face(new float[]{-2.28f, 1.035f, -0.74f, -2.28f, 1.035f, 0.74f, -2.37f, 1.075f, 0.72f, -2.37f, 1.075f, -0.72f},
                BLACK, TRUNK, true, 0.05f));
    }

    private void exhaust(float x, float z) {
        int n = 10;
        float[] outer = new float[3 * n], inner = new float[3 * n];
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            outer[3 * i] = x;
            outer[3 * i + 1] = 0.37f + 0.045f * (float) Math.sin(a);
            outer[3 * i + 2] = z + 0.045f * (float) Math.cos(a);
            inner[3 * i] = x - 0.001f;
            inner[3 * i + 1] = 0.37f + 0.03f * (float) Math.sin(a);
            inner[3 * i + 2] = z + 0.03f * (float) Math.cos(a);
        }
        faces.add(new Face(outer, CHROME, BODY, false, 0.06f));
        faces.add(new Face(inner, TYRE, BODY, false, 0.07f));
    }

    private void buildMirrors() {
        for (int side = -1; side <= 1; side += 2) {
            float z0 = side * 0.86f, z1 = side * 1.05f;
            float[][] q = {
                    {0.80f, 1.00f, z0, 0.80f, 1.00f, z1, 0.78f, 1.11f, z1, 0.78f, 1.11f, z0},
                    {0.62f, 1.01f, z0, 0.64f, 1.10f, z0, 0.64f, 1.10f, z1, 0.62f, 1.01f, z1},
                    {0.64f, 1.10f, z0, 0.78f, 1.11f, z0, 0.78f, 1.11f, z1, 0.64f, 1.10f, z1},
                    {0.62f, 1.01f, z1, 0.64f, 1.10f, z1, 0.78f, 1.11f, z1, 0.80f, 1.00f, z1},
                    {0.62f, 1.01f, z0, 0.62f, 1.01f, z1, 0.80f, 1.00f, z1, 0.80f, 1.00f, z0}};
            for (float[] f : q) faces.add(new Face(f, BLACK, side < 0 ? DOOR_FL : DOOR_FR, true, 0.03f));
        }
    }

    /** A wheel: tyre, black arch around it, dark grey ten-spoke rim. */
    private void wheel(float x, float side) {
        int n = 16;
        float r = 0.335f, cy = 0.335f, zo = side * 0.905f, zi = side * 0.70f;
        int an = 12;
        float[] arch = new float[3 * (an + 1)];
        for (int i = 0; i <= an; i++) {
            double t = Math.toRadians(-18 + 216.0 * i / an);
            int k = side > 0 ? i : an - i;
            arch[3 * k] = x + 0.40f * (float) Math.cos(t);
            arch[3 * k + 1] = Math.max(0.19f, cy + 0.40f * (float) Math.sin(t));
            arch[3 * k + 2] = side * 0.898f;
        }
        faces.add(new Face(arch, BLACK, BODY, false, 0.38f));
        float[] tyre = new float[3 * n], rim = new float[3 * n];
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float xa = x + r * (float) Math.cos(a0), ya = cy + r * (float) Math.sin(a0);
            float xb = x + r * (float) Math.cos(a1), yb = cy + r * (float) Math.sin(a1);
            faces.add(new Face(new float[]{xa, ya, zi, xb, yb, zi, xb, yb, zo, xa, ya, zo}, TYRE, BODY, true, -0.40f));
            int k = side > 0 ? i : n - 1 - i;
            tyre[3 * k] = xa;
            tyre[3 * k + 1] = ya;
            tyre[3 * k + 2] = zo;
            rim[3 * k] = x + r * 0.72f * (float) Math.cos(a0);
            rim[3 * k + 1] = cy + r * 0.72f * (float) Math.sin(a0);
            rim[3 * k + 2] = zo + side * 0.006f;
        }
        faces.add(new Face(tyre, TYRE, BODY, false, 0.50f));
        faces.add(new Face(rim, RIM_DARK, BODY, false, 0.56f));
        // Ten spokes, from the hub to the rim.
        float zs = zo + side * 0.010f, hub = r * 0.14f, out = r * 0.70f;
        for (int i = 0; i < 10; i++) {
            double a = 2 * Math.PI * i / 10 + 0.15;
            double da = 0.13;
            float[] s = {
                    x + hub * (float) Math.cos(a - da * 1.6), cy + hub * (float) Math.sin(a - da * 1.6), zs,
                    x + out * (float) Math.cos(a - da * 0.55), cy + out * (float) Math.sin(a - da * 0.55), zs,
                    x + out * (float) Math.cos(a + da * 0.55), cy + out * (float) Math.sin(a + da * 0.55), zs,
                    x + hub * (float) Math.cos(a + da * 1.6), cy + hub * (float) Math.sin(a + da * 1.6), zs};
            faces.add(new Face(s, SPOKE, BODY, false, 0.60f));
        }
        float[] cap = new float[3 * 8];
        for (int i = 0; i < 8; i++) {
            double a = 2 * Math.PI * i / 8;
            cap[3 * i] = x + hub * 1.1f * (float) Math.cos(a);
            cap[3 * i + 1] = cy + hub * 1.1f * (float) Math.sin(a);
            cap[3 * i + 2] = zo + side * 0.012f;
        }
        faces.add(new Face(cap, SPOKE, BODY, false, 0.62f));
    }

    // ---- Projection ----

    /**
     * Rotates each part open by {@code open[part]} (0 closed to 1 fully open), turns the car by {@code yawDeg}
     * and tilts the view by {@code pitchDeg}, projects with a little perspective into a {@code w} x {@code h}
     * area, shades each face by a light from the upper front, and returns the visible faces back to front.
     */
    List<Flat> project(float yawDeg, float pitchDeg, float[] open, float w, float h) {
        double yaw = Math.toRadians(yawDeg), pitch = Math.toRadians(pitchDeg);
        float cyw = (float) Math.cos(yaw), syw = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float dist = 9f, scale = Math.min(w / 5.8f, h / 3.45f);
        float lx = -0.35f, ly = 0.80f, lz = 0.48f;   // light, in view space
        List<Flat> out = new ArrayList<>();
        float[] v = new float[3];
        for (Face f : faces) {
            int n = f.p.length / 3;
            float[] cam = new float[3 * n];
            float a = f.part == BODY || open == null ? 0f : open[f.part] * maxAngle[f.part];
            for (int i = 0; i < n; i++) {
                v[0] = f.p[3 * i];
                v[1] = f.p[3 * i + 1];
                v[2] = f.p[3 * i + 2];
                if (a != 0f) rotateAboutHinge(v, hinge[f.part], a);
                // around the car's vertical axis, lifted so it turns about its middle
                float x = v[0] * cyw + v[2] * syw, z = -v[0] * syw + v[2] * cyw, y = v[1] - 0.7f;
                // tilt toward the viewer
                float y2 = y * cp - z * sp, z2 = y * sp + z * cp;
                cam[3 * i] = x;
                cam[3 * i + 1] = y2;
                cam[3 * i + 2] = z2;
            }
            // Normal (Newell's method), facing the viewer at +z.
            float nx = 0, ny = 0, nz = 0, cx = 0, cy = 0, cz = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float x0 = cam[3 * i], y0 = cam[3 * i + 1], z0 = cam[3 * i + 2];
                float x1 = cam[3 * j], y1 = cam[3 * j + 1], z1 = cam[3 * j + 2];
                nx += (y0 - y1) * (z0 + z1);
                ny += (z0 - z1) * (x0 + x1);
                nz += (x0 - x1) * (y0 + y1);
                cx += x0;
                cy += y0;
                cz += z0;
            }
            cx /= n;
            cy /= n;
            cz /= n;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) continue;
            nx /= len;
            ny /= len;
            nz /= len;
            float toEye = -cx * nx - cy * ny + (dist - cz) * nz;
            boolean back = toEye <= 0;
            if (back && !f.twoSided) continue;
            if (back) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float lit = 0.62f + 0.42f * Math.max(0f, nx * lx + ny * ly + nz * lz);
            int base = back && f.part != BODY ? INSIDE : f.color;
            // A soft shine where the light glances off paint and glass toward the viewer.
            float vx = -cx, vy = -cy, vz = dist - cz, vl = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float hx = lx + vx / vl, hy = ly + vy / vl, hz = lz + vz / vl, hl = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
            float spec = (float) Math.pow(Math.max(0f, (nx * hx + ny * hy + nz * hz) / hl), 28);
            float gloss = base == GLASS ? 0.55f : base == WHITE ? 0.30f : base == TYRE || base == INSIDE ? 0.05f : 0.20f;
            int color = toward(shade(base, lit), 0xFFFFFFFF, spec * gloss);
            float[] xy = new float[2 * n];
            for (int i = 0; i < n; i++) {
                float s = dist / (dist - cam[3 * i + 2]);
                xy[2 * i] = w / 2 + cam[3 * i] * s * scale;
                xy[2 * i + 1] = h * 0.50f - cam[3 * i + 1] * s * scale;
            }
            out.add(new Flat(xy, color, f.part, cz + f.bias));
        }
        Collections.sort(out, (p, q) -> Float.compare(p.depth, q.depth));
        return out;
    }

    /** Rotates v about the hinge (point px,py,pz, axis ax,ay,az: only y or z axes are used) by deg. */
    static void rotateAboutHinge(float[] v, float[] h, float deg) {
        double r = Math.toRadians(deg);
        float c = (float) Math.cos(r), s = (float) Math.sin(r);
        float x = v[0] - h[0], y = v[1] - h[1], z = v[2] - h[2];
        if (h[4] != 0) {          // vertical axis: doors
            v[0] = h[0] + x * c + z * s;
            v[2] = h[2] - x * s + z * c;
        } else {                  // across the car: bonnet and boot lid
            v[0] = h[0] + x * c - y * s;
            v[1] = h[1] + x * s + y * c;
        }
    }

    /** Mixes {@code a} toward {@code b} by t (0 to 1). */
    static int toward(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static int shade(int argb, float k) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((argb & 0xFF) * k));
        return (argb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    /** Door bits as most CAN boxes send them, unless config.json says otherwise. */
    static final int[] DEFAULT_BITS = {0, 1, 2, 4, 8, 16, 32};

    /** Which parts are open, from a CAN door byte and the bit for each part (index = part). */
    static boolean[] openParts(int mask, int[] bits) {
        boolean[] open = new boolean[PARTS];
        for (int p = 1; p < PARTS; p++) open[p] = bits[p] != 0 && (mask & bits[p]) != 0;
        return open;
    }
}
