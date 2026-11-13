package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Engine torque: "TORQUE", the value in Nm, and a bar from 0 to the engine's peak torque. */
final class TorqueBar extends View {
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF(), lit = new RectF();
    private final float max;
    private float target = Float.NaN, shown;
    private long lastFrame;

    TorqueBar(Context ctx, float maxNm) {
        super(ctx);
        max = maxNm;
        float d = getResources().getDisplayMetrics().density;
        label.setColor(0xB3FFFFFF);
        label.setTextSize(11 * d);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setColor(0xFFFFFFFF);
        value.setTextAlign(Paint.Align.RIGHT);
        value.setTextSize(20 * d);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        track.setColor(0x26FFFFFF);
    }

    void set(float nm) {
        target = nm;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float bh = Math.max(6f, h * 0.16f);
        bar.set(0, h - bh, w, h);
        fill.setShader(new LinearGradient(0, 0, w, 0, 0xFF30D158, 0xFFFF9F0A, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        boolean has = !Float.isNaN(target);
        shown = SpeedMath.smooth(shown, has ? target : 0f, lastFrame == 0 ? 16 : now - lastFrame);
        lastFrame = now;
        float base = bar.top - bar.height() * 0.9f;
        c.drawText("TORQUE", 0, base, label);
        c.drawText(has ? Math.round(target) + " Nm" : "-- Nm", getWidth(), base, value);
        float r = bar.height() / 2;
        c.drawRoundRect(bar, r, r, track);
        float frac = SpeedMath.fraction(Math.max(0f, shown), max);
        if (frac > 0.005f) {
            lit.set(bar.left, bar.top, bar.left + Math.max(bar.height(), bar.width() * frac), bar.bottom);
            c.drawRoundRect(lit, r, r, fill);
        }
        if (Math.abs(shown - (has ? target : 0f)) > 0.05f) postInvalidateOnAnimation();
    }
}
