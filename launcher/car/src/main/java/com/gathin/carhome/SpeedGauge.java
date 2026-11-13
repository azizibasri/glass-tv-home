package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * The speedometer, in a sporty dial style: a dark round face inside a brushed-metal rim, slanted numbers,
 * white ticks that turn red in the top sixth of the dial, and a tapered red needle with a glowing tip that
 * sweeps from 0 to the top and back when the dashboard opens, like a car's cluster at start-up. The speed
 * in digits, its unit and its source (CAN BUS or GPS) sit under the hub.
 */
final class SpeedGauge extends View {
    private static final int RED = 0xFFFF2D2D;
    private static final long SWEEP_MS = 1_400;
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint redZone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF(), zoneOval = new RectF();
    private final Path needlePath = new Path();
    private final float max, redFrom;
    private final String unit;
    private float target, shown, cx, cy, r;
    private int source = SpeedMath.NONE;
    private long lastFrame, sweepStart;
    private String status = "";

    SpeedGauge(Context ctx, int accent, float max, String unit) {
        super(ctx);
        this.max = max;
        this.redFrom = Math.round(max * 5f / 6f / 10f) * 10f;
        this.unit = unit;
        rim.setStyle(Paint.Style.STROKE);
        redZone.setStyle(Paint.Style.STROKE);
        redZone.setColor(RED);
        redZone.setStrokeCap(Paint.Cap.BUTT);
        tick.setStrokeCap(Paint.Cap.BUTT);
        Typeface sporty = Typeface.create("sans-serif-condensed", Typeface.BOLD_ITALIC);
        num.setTypeface(sporty);
        num.setTextAlign(Paint.Align.CENTER);
        big.setColor(0xFFFFFFFF);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(sporty);
        small.setColor(0xA6FFFFFF);
        small.setTextAlign(Paint.Align.CENTER);
        small.setLetterSpacing(0.15f);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        needle.setColor(RED);
        needle.setStyle(Paint.Style.FILL);
        hub.setStyle(Paint.Style.FILL);
        hubRing.setStyle(Paint.Style.STROKE);
        hubRing.setColor(RED);
        // The needle's glow is a shadow, which needs a software layer on this one small view.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    /** New reading; {@code source} is SpeedMath.CAN, GPS or NONE. */
    void set(float speed, int source, String status) {
        this.target = source == SpeedMath.NONE || Float.isNaN(speed) ? 0f : speed;
        this.source = source;
        this.status = status;
        postInvalidateOnAnimation();
    }

    /** The start-up sweep: 0 to the top of the dial and back. */
    void sweep() {
        sweepStart = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h);
        cx = w / 2f;
        cy = h / 2f;
        r = size / 2f * 0.96f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        face.setShader(new RadialGradient(cx, cy - r * 0.25f, r * 1.1f,
                new int[]{0xFF262A33, 0xFF101217, 0xFF050608}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        rim.setStrokeWidth(size * 0.022f);
        rim.setShader(new SweepGradient(cx, cy, new int[]{0xFF8A8F99, 0xFFE6E9EF, 0xFF5A5E66, 0xFFC9CDD6,
                0xFF4A4D55, 0xFFE6E9EF, 0xFF8A8F99}, null));
        float zr = r * 0.86f;
        zoneOval.set(cx - zr, cy - zr, cx + zr, cy + zr);
        redZone.setStrokeWidth(size * 0.022f);
        num.setTextSize(size * 0.068f);
        big.setTextSize(size * 0.15f);
        small.setTextSize(size * 0.042f);
        hubRing.setStrokeWidth(size * 0.012f);
        hub.setShader(new RadialGradient(cx, cy - size * 0.01f, size * 0.06f, 0xFF3A3E47, 0xFF0B0C10, Shader.TileMode.CLAMP));
        needle.setShadowLayer(size * 0.03f, 0, 0, 0xCCFF2D2D);
        if (sweepStart == 0) sweep();
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        shown = SpeedMath.smooth(shown, target, lastFrame == 0 ? 16 : now - lastFrame);
        lastFrame = now;
        float value = shown;
        boolean sweeping = sweepStart > 0 && now - sweepStart < SWEEP_MS;
        if (sweeping) {
            float t = (now - sweepStart) / (float) SWEEP_MS;
            value = max * (float) Math.sin(Math.PI * t);
        }

        c.drawCircle(cx, cy, r, face);
        c.drawCircle(cx, cy, r - rim.getStrokeWidth() / 2, rim);

        // Red zone band, then ticks every 5 or 10 and slanted numbers every 20.
        float zoneStart = SpeedMath.START_DEG + SpeedMath.SWEEP_DEG * redFrom / max;
        c.drawArc(zoneOval, zoneStart, SpeedMath.START_DEG + SpeedMath.SWEEP_DEG - zoneStart, false, redZone);
        int major = max <= 160 ? 10 : 20;
        int minor = major / 4;
        for (int v = 0; v <= max; v += minor) {
            double a = Math.toRadians(SpeedMath.START_DEG + SpeedMath.SWEEP_DEG * v / max);
            boolean isMajor = v % major == 0, isMid = v % (major / 2) == 0;
            boolean red = v >= redFrom;
            tick.setColor(red ? RED : isMajor ? 0xFFFFFFFF : 0xB3FFFFFF);
            tick.setStrokeWidth(r * (isMajor ? 0.022f : 0.010f));
            float r1 = r * 0.90f, r2 = r1 - r * (isMajor ? 0.12f : isMid ? 0.07f : 0.045f);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tick);
            if (isMajor) {
                float rn = r * 0.66f;
                num.setColor(red ? RED : 0xFFFFFFFF);
                c.drawText(String.valueOf(v), cx + (float) Math.cos(a) * rn,
                        cy + (float) Math.sin(a) * rn - (num.ascent() + num.descent()) / 2, num);
            }
        }

        // Digits under the hub.
        String digits = source == SpeedMath.NONE && !sweeping ? "--" : String.valueOf(SpeedMath.display(value));
        float dy = cy + r * 0.42f;
        c.drawText(digits, cx, dy, big);
        c.drawText(unit.toUpperCase(java.util.Locale.ROOT), cx, dy + small.getTextSize() * 2.1f, small);
        c.drawText(status, cx, dy + small.getTextSize() * 3.7f, small);

        // Needle: a tapered red blade from just behind the hub to the ticks, with a glow.
        float frac = SpeedMath.fraction(value, max);
        float deg = SpeedMath.START_DEG + SpeedMath.SWEEP_DEG * frac;
        float tip = r * 0.88f, tail = r * 0.14f, base = r * 0.035f, tipW = r * 0.008f;
        needlePath.reset();
        needlePath.moveTo(-tail, -base);
        needlePath.lineTo(tip, -tipW);
        needlePath.lineTo(tip, tipW);
        needlePath.lineTo(-tail, base);
        needlePath.close();
        int save = c.save();
        c.translate(cx, cy);
        c.rotate(deg);
        c.drawPath(needlePath, needle);
        c.restoreToCount(save);
        float hubR = r * 0.075f;
        c.drawCircle(cx, cy, hubR, hub);
        c.drawCircle(cx, cy, hubR, hubRing);

        if (sweeping || Math.abs(shown - target) > 0.05f) postInvalidateOnAnimation();
    }
}
