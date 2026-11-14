package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;

/**
 * The 3D car under the dials. Drag to turn it (sideways) or tilt it (up and down); left alone it turns
 * slowly. Open doors, boot and bonnet swing open on the model and glow orange, the car turns to show
 * them, and a label says which are open. Draws only while Home is on screen.
 */
final class CarModelView extends View {
    private static final int ORANGE = 0xFFFF9F0A;
    private final CarMesh mesh = CarMesh.jetta();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float d;
    private final float[] openNow = new float[CarMesh.PARTS];
    private final boolean[] openWanted = new boolean[CarMesh.PARTS];
    private final String[] names;
    private float yaw = -55f, pitch = 16f, spin, targetYaw = Float.NaN;
    private float lastX, lastY;
    private long lastTouch, lastFrame;
    private boolean running, hasData;
    private String status = "";

    CarModelView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        names = new String[]{"", ctx.getString(R.string.door_fl), ctx.getString(R.string.door_fr),
                ctx.getString(R.string.door_rl), ctx.getString(R.string.door_rr), ctx.getString(R.string.door_trunk),
                ctx.getString(R.string.door_hood)};
        fill.setStyle(Paint.Style.FILL);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(0.6f, 0.5f * d));
        edge.setStrokeJoin(Paint.Join.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setColor(ORANGE);
        glow.setStrokeWidth(2.2f * d);
        glow.setStrokeJoin(Paint.Join.ROUND);
        label.setColor(0xFFFFFFFF);
        label.setTextSize(12 * d);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        setContentDescription(ctx.getString(R.string.car_model));
        status = ctx.getString(R.string.doors_no_data);
    }

    /** Runs the animation while Home is resumed. */
    void setRunning(boolean r) {
        running = r;
        lastFrame = 0;
        invalidate();
    }

    /** Which parts are open (index = CarMesh part), or {@code known} false when there is no door data. */
    void setDoors(boolean[] open, boolean known) {
        hasData = known;
        int newest = -1, count = 0;
        for (int p = 1; p < CarMesh.PARTS; p++) {
            boolean o = known && open != null && open[p];
            if (o && !openWanted[p]) newest = p;
            openWanted[p] = o;
            if (o) count++;
        }
        if (newest > 0) targetYaw = showYaw(newest);
        Context c = getContext();
        if (!known) status = c.getString(R.string.doors_no_data);
        else if (count == 0) status = c.getString(R.string.doors_closed);
        else if (count == 1) {
            for (int p = 1; p < CarMesh.PARTS; p++) if (openWanted[p]) status = c.getString(R.string.door_open, names[p]);
        } else status = c.getString(R.string.doors_open, count);
        invalidate();
    }

    /** A turn that shows that part best: its side, a little from the front (or back for the boot). */
    static float showYaw(int part) {
        switch (part) {
            case CarMesh.DOOR_FL:
            case CarMesh.DOOR_RL:
                return -145f;
            case CarMesh.DOOR_FR:
            case CarMesh.DOOR_RR:
                return -35f;
            case CarMesh.TRUNK:
                return 140f;
            default:
                return -60f;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // The car turns; the pages don't swipe while a finger is on it.
                getParent().requestDisallowInterceptTouchEvent(true);
                lastX = e.getX();
                lastY = e.getY();
                spin = 0;
                targetYaw = Float.NaN;
                lastTouch = SystemClock.uptimeMillis();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastX, dy = e.getY() - lastY;
                yaw -= dx / d * 0.6f;
                pitch = Math.max(4f, Math.min(42f, pitch + dy / d * 0.25f));
                spin = -dx / d * 0.6f;
                lastX = e.getX();
                lastY = e.getY();
                lastTouch = SystemClock.uptimeMillis();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastTouch = SystemClock.uptimeMillis();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        shadow.setShader(new RadialGradient(w / 2f, h * 0.78f, w * 0.34f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(100f, now - lastFrame);
        lastFrame = now;
        step(now, dt);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        float labelH = 22 * d;
        float mh = h - labelH;

        int save = c.save();
        c.scale(1f, 0.32f, w / 2f, mh * 0.80f);
        c.drawCircle(w / 2f, mh * 0.80f, w * 0.34f, shadow);
        c.restoreToCount(save);

        List<CarMesh.Flat> flats = mesh.project(yaw, pitch, openNow, w, mh);
        for (CarMesh.Flat f : flats) {
            path.rewind();
            path.moveTo(f.xy[0], f.xy[1]);
            for (int i = 2; i < f.xy.length; i += 2) path.lineTo(f.xy[i], f.xy[i + 1]);
            path.close();
            fill.setColor(f.color);
            c.drawPath(path, fill);
            // A hairline in the same colour hides seams; opening panels get a faint panel gap.
            edge.setColor(f.part != CarMesh.BODY ? 0x55000000 : f.color);
            c.drawPath(path, edge);
            if (f.part != CarMesh.BODY && openNow[f.part] > 0.05f) {
                glow.setAlpha(Math.round(255 * Math.min(1f, openNow[f.part] * 1.5f)));
                c.drawPath(path, glow);
            }
        }

        // Status pill.
        float tw = label.measureText(status) + 24 * d;
        rect.set(w / 2f - tw / 2, h - labelH, w / 2f + tw / 2, h - 2 * d);
        boolean anyOpen = false;
        for (int p = 1; p < CarMesh.PARTS; p++) anyOpen |= openWanted[p];
        pill.setColor(anyOpen ? 0xCCFF9F0A : hasData ? 0x4D30D158 : 0x33FFFFFF);
        c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, pill);
        label.setColor(anyOpen ? 0xFF1C1C1E : 0xFFFFFFFF);
        c.drawText(status, w / 2f, rect.centerY() - (label.ascent() + label.descent()) / 2, label);

        if (running) postInvalidateDelayed(33);
    }

    private void step(long now, float dt) {
        boolean touching = now - lastTouch < 60;
        for (int p = 1; p < CarMesh.PARTS; p++) {
            float goal = openWanted[p] ? 1f : 0f;
            float k = Math.min(1f, dt / 380f);
            openNow[p] += (goal - openNow[p]) * k;
            if (Math.abs(goal - openNow[p]) < 0.003f) openNow[p] = goal;
        }
        if (touching) return;
        if (!Float.isNaN(targetYaw)) {
            float diff = ((targetYaw - yaw) % 360f + 540f) % 360f - 180f;
            yaw += diff * Math.min(1f, dt / 300f);
            if (Math.abs(diff) < 0.3f) targetYaw = Float.NaN;
        } else if (Math.abs(spin) > 0.05f) {
            yaw += spin;                       // a flick keeps turning, then slows
            spin *= (float) Math.pow(0.92, dt / 16f);
        } else if (now - lastTouch > 5_000) {
            yaw += dt * 0.008f;               // left alone: a slow turn, about 45 s a lap
        }
    }
}
