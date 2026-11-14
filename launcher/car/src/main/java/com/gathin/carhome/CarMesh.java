package com.gathin.carhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A simple 3D sedan in the proportions of a 2017 Jetta (4.6 m long), built from code: a lofted body, a
 * glasshouse, four wheels, and doors, boot lid and bonnet that swing open on their hinges. Styled after the
 * owner's car: white paint, black mirrors and front lip, smoked headlights, a red line across the grille.
 * No maker's badges.
 *
 * Plain Java (no Android), so the geometry is unit tested. {@link #project} turns it into flat-shaded 2D
 * polygons sorted back to front, which a view or a canvas simply fills in order.
 *
 * Axes: x forward (front at +2.3), y up (ground at 0), z to the car's right. Left = -z.
 */
final class CarMesh {
    static final int BODY = 0, DOOR_FL = 1, DOOR_FR = 2, DOOR_RL = 3, DOOR_RR = 4, TRUNK = 5, HOOD = 6;
    static final int PARTS = 7;

    static final int WHITE = 0xFFF4F5F7, GLASS = 0xFF1E2530, BLACK = 0xFF16181C, BAY = 0xFF3A3D44,
            TYRE = 0xFF111214, RIM = 0xFFB9BEC6, RED = 0xFFE0202A, LAMP = 0xFF2A3038, TAIL = 0xFFB0101A,
            INSIDE = 0xFF8E939B;

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

    // ---- Shape ----

    /** Body stations: x, bottom, top (beltline / deck), half width. */
    private static final float[][] STATIONS = {
            {-2.32f, 0.32f, 0.80f, 0.80f}, {-2.22f, 0.22f, 0.97f, 0.86f}, {-1.55f, 0.20f, 0.99f, 0.89f},
            {-1.15f, 0.18f, 0.985f, 0.89f}, {-0.25f, 0.18f, 0.97f, 0.89f}, {0.75f, 0.18f, 0.95f, 0.89f},
            {0.85f, 0.18f, 0.94f, 0.89f}, {1.70f, 0.20f, 0.88f, 0.88f}, {2.15f, 0.22f, 0.80f, 0.86f},
            {2.32f, 0.28f, 0.66f, 0.80f}};
    /** Glasshouse stations: x, base y, roof y, base half width, roof half width. */
    private static final float[][] CABIN = {
            {-1.55f, 0.99f, 1.00f, 0.80f, 0.66f}, {-0.95f, 0.99f, 1.42f, 0.82f, 0.66f},
            {0.15f, 0.97f, 1.45f, 0.83f, 0.67f}, {0.80f, 0.95f, 0.97f, 0.83f, 0.70f}};

    static CarMesh jetta() {
        CarMesh m = new CarMesh();
        m.buildBody();
        m.buildCabin();
        m.buildDoors();
        m.buildLids();
        m.buildDetails();
        for (float[] w : new float[][]{{1.35f, -1}, {1.35f, 1}, {-1.30f, -1}, {-1.30f, 1}}) m.wheel(w[0], w[1]);
        return m;
    }

    private static float[] ring(float[] st) {
        float b = st[1], t = st[2], w = st[3];
        return new float[]{
                -w, b + 0.10f, -w, t - 0.10f, -w + 0.12f, t, w - 0.12f, t,
                w, t - 0.10f, w, b + 0.10f, w - 0.12f, b, -w + 0.12f, b};
    }

    private void buildBody() {
        for (int i = 0; i + 1 < STATIONS.length; i++) {
            float x0 = STATIONS[i][0], x1 = STATIONS[i + 1][0];
            float[] r0 = ring(STATIONS[i]), r1 = ring(STATIONS[i + 1]);
            float mid = (x0 + x1) / 2;
            boolean doorBand = mid > -1.15f && mid < 0.75f;
            boolean hoodBand = mid > 0.85f && mid < 2.15f, trunkBand = mid > -2.22f && mid < -1.55f;
            for (int k = 0; k < 8; k++) {
                int a = k, b = (k + 1) % 8;
                int color = WHITE;
                if (k == 6) color = BLACK;                                       // underside
                else if (k == 2 && (hoodBand || trunkBand)) color = BAY;          // under the lids
                else if ((k == 0 || k == 4) && doorBand) color = INSIDE;          // behind the doors
                float[] quad = {
                        x0, r0[2 * a + 1], r0[2 * a], x1, r1[2 * a + 1], r1[2 * a],
                        x1, r1[2 * b + 1], r1[2 * b], x0, r0[2 * b + 1], r0[2 * b]};
                faces.add(new Face(quad, color, BODY, false, 0));
                // The opening panels sit on the same surface, a hair outside it, so they always fit.
                int part = -1;
                if (k == 2 && hoodBand) part = HOOD;
                else if (k == 2 && trunkBand) part = TRUNK;
                else if (k == 0 && doorBand) part = mid > -0.25f ? DOOR_FL : DOOR_RL;
                else if (k == 4 && doorBand) part = mid > -0.25f ? DOOR_FR : DOOR_RR;
                if (part >= 0) {
                    float[] panel = outward(quad.clone());
                    float ox = 0, oy = k == 2 ? 0.012f : 0, oz = k == 0 ? -0.006f : k == 4 ? 0.006f : 0;
                    for (int q = 0; q < 4; q++) {
                        panel[3 * q] += ox;
                        panel[3 * q + 1] += oy;
                        panel[3 * q + 2] += oz;
                    }
                    faces.add(new Face(panel, WHITE, part, true, 0.02f));
                }
            }
        }
        cap(STATIONS[0], false);
        cap(STATIONS[STATIONS.length - 1], true);
    }

    /** Closes the nose or the tail with the station's own outline. */
    private void cap(float[] st, boolean front) {
        float[] r = ring(st);
        float[] p = new float[24];
        for (int k = 0; k < 8; k++) {
            int src = front ? k : 7 - k;
            p[3 * k] = st[0];
            p[3 * k + 1] = r[2 * src + 1];
            p[3 * k + 2] = r[2 * src];
        }
        faces.add(new Face(p, WHITE, BODY, false, 0));
    }

    private void buildCabin() {
        for (int i = 0; i + 1 < CABIN.length; i++) {
            float[] a = CABIN[i], b = CABIN[i + 1];
            boolean roof = a[0] >= -0.96f && b[0] <= 0.16f;
            // left window, roof (or rear window / windscreen), right window
            faces.add(new Face(new float[]{a[0], a[1], -a[3], b[0], b[1], -b[3], b[0], b[2], -b[4], a[0], a[2], -a[4]},
                    GLASS, BODY, false, 0));
            faces.add(new Face(new float[]{a[0], a[2], -a[4], b[0], b[2], -b[4], b[0], b[2], b[4], a[0], a[2], a[4]},
                    roof ? WHITE : GLASS, BODY, false, 0));
            faces.add(new Face(new float[]{a[0], a[2], a[4], b[0], b[2], b[4], b[0], b[1], b[3], a[0], a[1], a[3]},
                    GLASS, BODY, false, 0));
        }
    }

    /** Roof line height at x, from the glasshouse stations. */
    static float roofAt(float x) {
        for (int i = 0; i + 1 < CABIN.length; i++) {
            float[] a = CABIN[i], b = CABIN[i + 1];
            if (x >= a[0] && x <= b[0]) return a[2] + (b[2] - a[2]) * (x - a[0]) / (b[0] - a[0]);
        }
        return CABIN[0][2];
    }

    /** Half width of the glasshouse side at height y (it leans in toward the roof). */
    private static float cabinSideAt(float y) {
        float t = Math.max(0f, Math.min(1f, (y - 0.97f) / (1.45f - 0.97f)));
        return 0.83f + (0.67f - 0.83f) * t;
    }

    private void buildDoors() {
        // front door: x 0.75 to -0.25; rear door: -0.25 to -1.15. Hinged at the front edge.
        float[][] doors = {{0.75f, -0.25f}, {-0.25f, -1.15f}};
        for (int d = 0; d < 2; d++) {
            float front = doors[d][0], rear = doors[d][1];
            for (int side = -1; side <= 1; side += 2) {
                int part = d == 0 ? (side < 0 ? DOOR_FL : DOOR_FR) : (side < 0 ? DOOR_RL : DOOR_RR);
                // the door's window, on the slope of the glasshouse
                float base = 0.98f;
                float xa = d == 0 ? front - 0.05f : front - 0.02f, xb = rear + (d == 0 ? 0.02f : 0.05f);
                float mid = d == 0 ? 0.15f : -0.95f;
                float[] xs = {xa, xb, xb, mid, xa};
                float[] ys = {base, base, roofAt(xb) - 0.05f, roofAt(mid) - 0.05f, Math.max(base + 0.03f, roofAt(xa) - 0.05f)};
                float[] p = new float[15];
                for (int k = 0; k < 5; k++) {
                    int kk = side > 0 ? k : 4 - k;
                    p[3 * k] = xs[kk];
                    p[3 * k + 1] = ys[kk];
                    p[3 * k + 2] = side * (cabinSideAt(ys[kk]) + 0.006f);
                }
                faces.add(new Face(outward(p), GLASS, part, true, 0.02f));
                hinge[part] = new float[]{front, 0, side * 0.894f, 0, 1, 0};
                maxAngle[part] = side * 58f;   // the rear edge swings outward
            }
        }
    }

    private void buildLids() {
        // Bonnet (its panels come from the body), hinged at the windscreen, lifts at the front.
        hinge[HOOD] = new float[]{0.85f, 0.952f, 0, 0, 0, 1};
        maxAngle[HOOD] = 48f;
        // Boot lid, hinged at the rear window, lifts at the back.
        hinge[TRUNK] = new float[]{-1.55f, 1.002f, 0, 0, 0, 1};
        maxAngle[TRUNK] = -62f;
    }

    private void decal(float[] p, int color) {
        faces.add(new Face(p, color, BODY, false, 0.04f));
    }

    private void buildDetails() {
        float fx = 2.325f;
        // Grille, the red line over it, smoked headlights, black lip.
        decal(new float[]{fx, 0.36f, 0.50f, fx, 0.36f, -0.50f, fx, 0.56f, -0.52f, fx, 0.56f, 0.52f}, BLACK);
        decal(new float[]{fx, 0.565f, 0.52f, fx, 0.565f, -0.52f, fx, 0.59f, -0.54f, fx, 0.59f, 0.54f}, RED);
        decal(new float[]{fx, 0.56f, 0.76f, fx, 0.56f, 0.54f, fx, 0.63f, 0.56f, fx, 0.60f, 0.77f}, LAMP);
        decal(new float[]{fx, 0.56f, -0.54f, fx, 0.56f, -0.76f, fx, 0.60f, -0.77f, fx, 0.63f, -0.56f}, LAMP);
        faces.add(new Face(new float[]{2.10f, 0.19f, -0.84f, 2.10f, 0.19f, 0.84f, 2.44f, 0.19f, 0.80f, 2.44f, 0.19f, -0.80f},
                BLACK, BODY, true, 0.02f));
        faces.add(new Face(new float[]{2.44f, 0.19f, -0.80f, 2.44f, 0.19f, 0.80f, 2.33f, 0.30f, 0.80f, 2.33f, 0.30f, -0.80f},
                BLACK, BODY, true, 0.02f));
        // Tail lights.
        float rx = -2.325f;
        decal(new float[]{rx, 0.68f, 0.45f, rx, 0.68f, 0.76f, rx, 0.78f, 0.76f, rx, 0.78f, 0.45f}, TAIL);
        decal(new float[]{rx, 0.68f, -0.76f, rx, 0.68f, -0.45f, rx, 0.78f, -0.45f, rx, 0.78f, -0.76f}, TAIL);
        // Black mirrors on the doors' front corners.
        for (int side = -1; side <= 1; side += 2) {
            float z0 = side * 0.89f, z1 = side * 1.04f;
            float[][] q = {
                    {0.72f, 1.00f, z0, 0.72f, 1.00f, z1, 0.72f, 1.10f, z1, 0.72f, 1.10f, z0},
                    {0.58f, 1.00f, z0, 0.58f, 1.10f, z0, 0.58f, 1.10f, z1, 0.58f, 1.00f, z1},
                    {0.58f, 1.10f, z0, 0.72f, 1.10f, z0, 0.72f, 1.10f, z1, 0.58f, 1.10f, z1},
                    {0.58f, 1.00f, z1, 0.58f, 1.10f, z1, 0.72f, 1.10f, z1, 0.72f, 1.00f, z1}};
            for (float[] f : q) faces.add(new Face(f, BLACK, side < 0 ? DOOR_FL : DOOR_FR, true, 0.03f));
        }
    }

    private void wheel(float x, float side) {
        int n = 14;
        float r = 0.33f, cy = 0.33f, zo = side * 0.905f, zi = side * 0.70f;
        float[] rim = new float[3 * n], tyre = new float[3 * n];
        // Black wheel arch on the body side, around the top of the tyre.
        int an = 10;
        float[] arch = new float[3 * (an + 1)];
        for (int i = 0; i <= an; i++) {
            double t = Math.toRadians(-20 + 220.0 * i / an);
            int k = side > 0 ? i : an - i;
            arch[3 * k] = x + 0.40f * (float) Math.cos(t);
            arch[3 * k + 1] = Math.max(0.19f, cy + 0.40f * (float) Math.sin(t));
            arch[3 * k + 2] = side * 0.896f;
        }
        faces.add(new Face(arch, BLACK, BODY, false, 0.38f));
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            float xa = x + r * (float) Math.cos(a0), ya = cy + r * (float) Math.sin(a0);
            float xb = x + r * (float) Math.cos(a1), yb = cy + r * (float) Math.sin(a1);
            faces.add(new Face(new float[]{xa, ya, zi, xb, yb, zi, xb, yb, zo, xa, ya, zo}, TYRE, BODY, true, -0.40f));
            int k = side > 0 ? i : n - 1 - i;
            tyre[3 * k] = xa;
            tyre[3 * k + 1] = ya;
            tyre[3 * k + 2] = zo;
            rim[3 * k] = x + r * 0.68f * (float) Math.cos(a0);
            rim[3 * k + 1] = cy + r * 0.68f * (float) Math.sin(a0);
            rim[3 * k + 2] = zo + side * 0.006f;
        }
        // The tyre's outer wall, then the rim on it; the tread only shows below the body.
        faces.add(new Face(tyre, TYRE, BODY, false, 0.50f));
        faces.add(new Face(rim, RIM, BODY, false, 0.56f));
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
        float dist = 9f, scale = Math.min(w / 6.0f, h / 3.9f);
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
            float lit = 0.66f + 0.40f * Math.max(0f, nx * lx + ny * ly + nz * lz);
            int color = back && f.part != BODY ? shade(INSIDE, lit) : shade(f.color, lit);
            float[] xy = new float[2 * n];
            for (int i = 0; i < n; i++) {
                float s = dist / (dist - cam[3 * i + 2]);
                xy[2 * i] = w / 2 + cam[3 * i] * s * scale;
                xy[2 * i + 1] = h * 0.52f - cam[3 * i + 1] * s * scale;
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
