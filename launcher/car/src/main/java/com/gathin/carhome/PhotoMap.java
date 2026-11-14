package com.gathin.carhome;

/**
 * Where the car sits in a four-view photo sheet (front, rear, side and top, two by two), so each photo can be
 * laid onto the 3D model's faces that look that way. Each view pins model positions to picture positions
 * at a few landmarks (bumpers, wheel centres, ground, roof) and goes in straight lines between them, so a
 * photo whose proportions differ a little from the real car still lines its wheels and door gaps up with
 * the model's. Positions in the picture are fractions of its width and height (0 to 1), so any size works.
 *
 * Plain Java: unit tested.
 */
final class PhotoMap {
    /** One axis: model values (ascending) and where they are in the picture. */
    static final class Axis {
        final float[] model, pic;

        Axis(float[] model, float[] pic) {
            this.model = model;
            this.pic = pic;
        }

        float at(float v) {
            int n = model.length;
            int i = 0;
            while (i < n - 2 && v > model[i + 1]) i++;
            float t = (v - model[i]) / (model[i + 1] - model[i]);
            return pic[i] + (pic[i + 1] - pic[i]) * t;
        }
    }

    /** Side view (car's right side, front to the right): x across, y up. */
    final Axis sideX, sideY;
    /** Front view: z across (the car's right is on the picture's left), y up. */
    final Axis frontZ, frontY;
    /** Rear view: z across, y up. */
    final Axis rearZ, rearY;
    /** Top view (front to the left, the car's right side at the top): x across, z down the picture. */
    final Axis topX, topZ;

    PhotoMap(Axis sideX, Axis sideY, Axis frontZ, Axis frontY, Axis rearZ, Axis rearY, Axis topX, Axis topZ) {
        this.sideX = sideX;
        this.sideY = sideY;
        this.frontZ = frontZ;
        this.frontY = frontY;
        this.rearZ = rearZ;
        this.rearY = rearY;
        this.topX = topX;
        this.topZ = topZ;
    }

    private static Axis ax(float[] model, float[] px, float size) {
        float[] f = new float[px.length];
        for (int i = 0; i < px.length; i++) f[i] = px[i] / size;
        return new Axis(model, f);
    }

    /**
     * The owner's 2000 x 1342 sheet: front top left, rear top right, side bottom left, top bottom right.
     * Landmarks measured on the picture: body edges, tyre bottoms, wheel centres, roof.
     */
    static PhotoMap ownerSheet() {
        float w = 2000f, h = 1342f;
        return new PhotoMap(
                ax(new float[]{-2.35f, -1.29f, 1.40f, 2.35f}, new float[]{34, 240, 800, 955}, w),
                ax(new float[]{0f, 0.335f, 1.455f}, new float[]{1178, 1113, 876}, h),
                ax(new float[]{-0.905f, 0.905f}, new float[]{799, 195}, w),
                ax(new float[]{0f, 1.455f}, new float[]{600, 108}, h),
                ax(new float[]{-0.905f, 0.905f}, new float[]{1205, 1801}, w),
                ax(new float[]{0f, 1.455f}, new float[]{600, 112}, h),
                ax(new float[]{-2.35f, 2.35f}, new float[]{1945, 1061}, w),
                ax(new float[]{-0.905f, 0.905f}, new float[]{1193, 839}, h));
    }

    static final int NONE = -1, SIDE = 0, FRONT = 1, REAR = 2, TOP = 3;

    /** The view a face with this outward normal is best seen in; NONE for the underside. */
    static int viewFor(float nx, float ny, float nz) {
        float ax = Math.abs(nx), az = Math.abs(nz);
        if (ny < -0.6f) return NONE;
        if (ny >= ax && ny >= az) return TOP;
        if (az >= ax) return SIDE;
        return nx > 0 ? FRONT : REAR;
    }

    /** Picture position (u, v as fractions) of model point x, y, z in that view. */
    void uv(int view, float x, float y, float z, float[] out, int at) {
        switch (view) {
            case SIDE:
                out[at] = sideX.at(x);
                out[at + 1] = sideY.at(y);
                break;
            case FRONT:
                out[at] = frontZ.at(z);
                out[at + 1] = frontY.at(y);
                break;
            case REAR:
                out[at] = rearZ.at(z);
                out[at + 1] = rearY.at(y);
                break;
            default:
                out[at] = topX.at(x);
                out[at + 1] = topZ.at(z);
                break;
        }
    }
}
