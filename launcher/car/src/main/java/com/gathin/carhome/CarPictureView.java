package com.gathin.carhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

import java.io.File;

/**
 * The car under the dials: the owner's own picture of the car (picked from the gallery, kept on the unit as
 * {@link #PICTURE_FILE}) and, beside it, the car seen from above, where each open door, the boot and the
 * bonnet swing out in orange. A label underneath says what is open. It only redraws while something moves.
 */
final class CarPictureView extends View {
    static final String PICTURE_FILE = "car_picture.jpg";
    private static final int ORANGE = 0xFFFF9F0A;

    private final float d;
    private final Paint picPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint door = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final float[] openNow = new float[Doors.COUNT];
    private final boolean[] openWanted = new boolean[Doors.COUNT];
    private final String[] names;
    private Bitmap picture;
    private boolean hasData;
    private String status;
    private long lastFrame;

    CarPictureView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        names = new String[]{"", ctx.getString(R.string.door_fl), ctx.getString(R.string.door_fr),
                ctx.getString(R.string.door_rl), ctx.getString(R.string.door_rr), ctx.getString(R.string.door_trunk),
                ctx.getString(R.string.door_hood)};
        body.setColor(0x2EFFFFFF);
        outline.setStyle(Paint.Style.STROKE);
        outline.setColor(0xE6FFFFFF);
        outline.setStrokeWidth(1.6f * d);
        outline.setStrokeJoin(Paint.Join.ROUND);
        glass.setColor(0x8C0B1220);
        door.setStyle(Paint.Style.STROKE);
        door.setStrokeCap(Paint.Cap.ROUND);
        door.setStrokeWidth(3.2f * d);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setStrokeWidth(9f * d);
        glow.setColor(0x66FF9F0A);
        label.setTextSize(12 * d);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        status = ctx.getString(R.string.doors_no_data);
        setContentDescription(status);
        loadPicture();
    }

    static File pictureFile(Context ctx) {
        File dir = CarConfig.dir(ctx);
        File f = dir == null ? null : new File(dir, PICTURE_FILE);
        return f != null && f.isFile() ? f : null;
    }

    /** Loads the car picture off the main thread, at most about 1200 pixels wide; none: top view only. */
    void loadPicture() {
        File f = pictureFile(getContext());
        if (f == null) {
            setPicture(null);
            return;
        }
        new Thread(() -> {
            Bitmap b = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                o.inSampleSize = 1;
                while (o.outWidth / o.inSampleSize > 1200) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                b = BitmapFactory.decodeFile(f.getPath(), o);
            } catch (OutOfMemoryError | RuntimeException ignored) {
                // Not a picture or too big: top view only.
            }
            Bitmap done = b;
            post(() -> setPicture(done));
        }, "car-picture").start();
    }

    private void setPicture(Bitmap b) {
        if (picture != null && picture != b) picture.recycle();
        picture = b;
        picPaint.setShader(b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidate();
    }

    /** Which openings are open (index = Doors constant), or {@code known} false when there is no door data. */
    void setDoors(boolean[] open, boolean known) {
        hasData = known;
        int count = 0, last = 0;
        for (int p = 1; p < Doors.COUNT; p++) {
            openWanted[p] = known && open != null && open[p];
            if (openWanted[p]) {
                count++;
                last = p;
            }
        }
        Context c = getContext();
        String s = !known ? c.getString(R.string.doors_no_data) : count == 0 ? c.getString(R.string.doors_closed)
                : count == 1 ? c.getString(R.string.door_open, names[last]) : c.getString(R.string.doors_open, count);
        if (!s.equals(status)) {
            status = s;
            setContentDescription(s);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(100f, now - lastFrame);
        lastFrame = now;
        boolean moving = false;
        for (int p = 1; p < Doors.COUNT; p++) {
            float goal = openWanted[p] ? 1f : 0f;
            openNow[p] += (goal - openNow[p]) * Math.min(1f, dt / 260f);
            if (Math.abs(goal - openNow[p]) < 0.004f) openNow[p] = goal;
            else moving = true;
        }

        int w = getWidth(), h = getHeight();
        float labelH = 22 * d, gap = 10 * d;
        float area = h - labelH - 6 * d;
        // The top view: about as tall as the area, a little under half as wide.
        float topW = area * 0.62f;
        float picW = picture == null ? 0 : Math.min(w - topW - gap, area * picture.getWidth() / (float) picture.getHeight());
        float total = picW + (picture == null ? 0 : gap) + topW;
        float x0 = (w - total) / 2;
        if (picture != null && picW > 20 * d) {
            rect.set(x0, 0, x0 + picW, area);
            float scale = Math.max(rect.width() / picture.getWidth(), rect.height() / picture.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(rect.centerX() - picture.getWidth() * scale / 2, rect.centerY() - picture.getHeight() * scale / 2);
            picPaint.getShader().setLocalMatrix(matrix);
            c.drawRoundRect(rect, 14 * d, 14 * d, picPaint);
        }
        drawTopView(c, x0 + total - topW, 0, topW, area);

        // Label.
        boolean anyOpen = false;
        for (int p = 1; p < Doors.COUNT; p++) anyOpen |= openWanted[p];
        float tw = label.measureText(status) + 24 * d;
        rect.set(w / 2f - tw / 2, h - labelH, w / 2f + tw / 2, h - 2 * d);
        pill.setColor(anyOpen ? 0xCCFF9F0A : hasData ? 0x4D30D158 : 0x33FFFFFF);
        c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, pill);
        label.setColor(anyOpen ? 0xFF1C1C1E : 0xFFFFFFFF);
        c.drawText(status, w / 2f, rect.centerY() - (label.ascent() + label.descent()) / 2, label);

        if (moving) postInvalidateOnAnimation();
        else lastFrame = 0;
    }

    /** The car from above, nose up, in a box: body, glass, mirrors, wheels, then the opening panels. */
    private void drawTopView(Canvas c, float bx, float by, float bw, float bh) {
        // Car: 4.7 m long, 1.8 m wide; leave room either side for the doors to swing out.
        float len = bh * 0.92f, wid = len * 0.383f;
        if (wid > bw * 0.56f) {
            wid = bw * 0.56f;
            len = wid / 0.383f;
        }
        float cx = bx + bw / 2, top = by + (bh - len) / 2;
        float m = len / 4.7f;                      // pixels per metre
        float l = cx - wid / 2, r = cx + wid / 2;

        // Wheels peeking out.
        door.setColor(0xFF2A2C31);
        door.setStrokeWidth(4f * d);
        for (float wy : new float[]{0.95f, 3.64f}) {
            c.drawLine(l - 1.5f * d, top + wy * m, l - 1.5f * d, top + (wy + 0.6f) * m, door);
            c.drawLine(r + 1.5f * d, top + wy * m, r + 1.5f * d, top + (wy + 0.6f) * m, door);
        }
        door.setStrokeWidth(3.2f * d);

        // Body, rounder at the nose.
        rect.set(l, top, r, top + len);
        c.drawRoundRect(rect, wid * 0.30f, wid * 0.22f, body);
        c.drawRoundRect(rect, wid * 0.30f, wid * 0.22f, outline);
        // Windscreen, roof, rear window.
        float gl = l + wid * 0.12f, gr = r - wid * 0.12f;
        trapezoid(c, gl + wid * 0.04f, gr - wid * 0.04f, top + 1.45f * m, gl, gr, top + 2.05f * m, glass);
        rect.set(gl, top + 2.05f * m, gr, top + 3.05f * m);
        c.drawRoundRect(rect, 3 * d, 3 * d, body);
        trapezoid(c, gl, gr, top + 3.05f * m, gl + wid * 0.05f, gr - wid * 0.05f, top + 3.75f * m, glass);
        // Mirrors.
        c.drawLine(l, top + 1.62f * m, l - 0.16f * m, top + 1.70f * m, outline);
        c.drawLine(r, top + 1.62f * m, r + 0.16f * m, top + 1.70f * m, outline);

        // Doors: hinged at their front edge, swinging outward.
        sideDoor(c, l, top + 1.50f * m, top + 2.60f * m, -1, openNow[Doors.FRONT_LEFT]);
        sideDoor(c, r, top + 1.50f * m, top + 2.60f * m, 1, openNow[Doors.FRONT_RIGHT]);
        sideDoor(c, l, top + 2.60f * m, top + 3.55f * m, -1, openNow[Doors.REAR_LEFT]);
        sideDoor(c, r, top + 2.60f * m, top + 3.55f * m, 1, openNow[Doors.REAR_RIGHT]);
        // Bonnet and boot lift: drawn as the lid sliding out past the nose or tail.
        lid(c, gl, gr, top + 1.40f * m, top + 0.12f * m, -1, openNow[Doors.BONNET], m);
        lid(c, gl, gr, top + 3.85f * m, top + len - 0.10f * m, 1, openNow[Doors.BOOT], m);
    }

    private void trapezoid(Canvas c, float tl, float tr, float ty, float bl, float br, float by, Paint p) {
        path.rewind();
        path.moveTo(tl, ty);
        path.lineTo(tr, ty);
        path.lineTo(br, by);
        path.lineTo(bl, by);
        path.close();
        c.drawPath(path, p);
    }

    private void sideDoor(Canvas c, float x, float y0, float y1, int side, float open) {
        float ang = (float) Math.toRadians(58 * open);
        float dx = (float) Math.sin(ang) * (y1 - y0) * side, dy = (float) Math.cos(ang) * (y1 - y0);
        door.setColor(open > 0.01f ? ORANGE : 0xE6FFFFFF);
        if (open > 0.01f) {
            glow.setAlpha(Math.round(0x66 * open));
            c.drawLine(x, y0, x + dx, y0 + dy, glow);
        }
        c.drawLine(x, y0, x + dx, y0 + dy, door);
    }

    /** {@code dir} -1 lifts toward the top (bonnet), +1 toward the bottom (boot). */
    private void lid(Canvas c, float l, float r, float hingeY, float edgeY, int dir, float open, float m) {
        if (open < 0.01f) return;
        float shift = dir * 0.55f * m * open;
        rect.set(l, Math.min(hingeY, edgeY) + shift, r, Math.max(hingeY, edgeY) + shift);
        pill.setColor(ORANGE);
        pill.setAlpha(Math.round(0x8C * open));
        c.drawRoundRect(rect, 4 * d, 4 * d, pill);
        door.setColor(ORANGE);
        c.drawRoundRect(rect, 4 * d, 4 * d, door);
    }
}
