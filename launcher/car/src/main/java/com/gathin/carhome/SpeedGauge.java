package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/**
 * The speedometer: a 240 degree dial with a tick every 10 and a number every 20, a lit arc up to the
 * current speed, a needle, and the speed in large digits in the middle with its source (CAN or GPS).
 */
final class SpeedGauge extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final int accent;
    private final float max;
    private final String unit;
    private float target, shown;
    private int source = SpeedMath.NONE;
    private long lastFrame;
    private String status = "";

    SpeedGauge(Context ctx, int accent, float max, String unit) {
        super(ctx);
        this.accent = accent;
        this.max = max;
        this.unit = unit;
        track.setStyle(Paint.Style.STROKE);
        track.setColor(0x26FFFFFF);
        track.setStrokeCap(Paint.Cap.ROUND);
        lit.setStyle(Paint.Style.STROKE);
        lit.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(0x99FFFFFF);
        tick.setStrokeCap(Paint.Cap.ROUND);
        num.setColor(0xCCFFFFFF);
        num.setTextAlign(Paint.Align.CENTER);
        big.setColor(0xFFFFFFFF);
        big.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        small.setColor(0xB3FFFFFF);
        small.setTextAlign(Paint.Align.CENTER);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        needle.setColor(0xFFFFFFFF);
        needle.setStrokeCap(Paint.Cap.ROUND);
    }

    /** New reading; {@code source} is SpeedMath.CAN, GPS or NONE. */
    void set(float speed, int source, String status) {
        this.target = source == SpeedMath.NONE || Float.isNaN(speed) ? 0f : speed;
        this.source = source;
        this.status = status;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float size = Math.min(w, h);
        float stroke = size * 0.045f;
        track.setStrokeWidth(stroke);
        lit.setStrokeWidth(stroke);
        tick.setStrokeWidth(Math.max(1f, size * 0.006f));
        needle.setStrokeWidth(size * 0.012f);
        num.setTextSize(size * 0.055f);
        big.setTextSize(size * 0.26f);
        small.setTextSize(size * 0.06f);
        float pad = stroke / 2 + size * 0.02f;
        float cx = w / 2f, cy = h / 2f + size * 0.04f, r = size / 2f - pad;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        SweepGradient g = new SweepGradient(cx, cy, new int[]{0xFF30D158, accent, 0xFFBF5AF2, 0xFFFF453A, 0xFF30D158},
                new float[]{0f, 0.25f, 0.5f, 0.66f, 1f});
        Matrix m = new Matrix();
        m.setRotate(SpeedMath.START_DEG, cx, cy);
        g.setLocalMatrix(m);
        lit.setShader(g);
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        shown = SpeedMath.smooth(shown, target, lastFrame == 0 ? 16 : now - lastFrame);
        lastFrame = now;
        float cx = oval.centerX(), cy = oval.centerY(), r = oval.width() / 2f;
        c.drawArc(oval, SpeedMath.START_DEG, SpeedMath.SWEEP_DEG, false, track);
        float frac = SpeedMath.fraction(shown, max);
        if (frac > 0.001f) c.drawArc(oval, SpeedMath.START_DEG, SpeedMath.SWEEP_DEG * frac, false, lit);

        // Ticks every 10, numbers every 20 (every 10 mph on a 160 dial).
        int step = max <= 160 ? 10 : 20;
        for (int v = 0; v <= max; v += step / 2) {
            double a = Math.toRadians(SpeedMath.START_DEG + SpeedMath.SWEEP_DEG * v / max);
            boolean major = v % step == 0;
            float r1 = r - track.getStrokeWidth() * 0.9f, r2 = r1 - r * (major ? 0.09f : 0.05f);
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tick);
            if (major) {
                float rn = r2 - r * 0.09f;
                c.drawText(String.valueOf(v), cx + (float) Math.cos(a) * rn,
                        cy + (float) Math.sin(a) * rn - (num.ascent() + num.descent()) / 2, num);
            }
        }
        // Needle from the inner edge of the numbers toward the arc.
        double na = Math.toRadians(SpeedMath.START_DEG + SpeedMath.SWEEP_DEG * frac);
        float n1 = r * 0.58f, n2 = r - track.getStrokeWidth() * 0.9f;
        c.drawLine(cx + (float) Math.cos(na) * n1, cy + (float) Math.sin(na) * n1,
                cx + (float) Math.cos(na) * n2, cy + (float) Math.sin(na) * n2, needle);

        String digits = source == SpeedMath.NONE ? "--" : String.valueOf(SpeedMath.display(shown));
        c.drawText(digits, cx, cy + big.getTextSize() * 0.32f, big);
        c.drawText(unit, cx, cy + big.getTextSize() * 0.32f + small.getTextSize() * 1.5f, small);
        c.drawText(status, cx, oval.bottom - small.getTextSize() * 0.2f, small);
        if (Math.abs(shown - target) > 0.05f) postInvalidateOnAnimation();
    }
}
