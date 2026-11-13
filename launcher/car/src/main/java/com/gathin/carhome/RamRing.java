package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The Clean button's face: a ring that fills with how much RAM is in use, and the percentage inside. */
final class RamRing extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float used = 0f;
    private float sweep = 0f;
    private String label = "";

    RamRing(Context ctx, int accent) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(4 * d);
        track.setColor(0x33FFFFFF);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(4 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(accent);
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(12 * d);
        text.setFakeBoldText(true);
    }

    /** {@code fraction} of RAM in use, 0 to 1. Turns orange from 75% and red from 90%. */
    void setUsed(float fraction, int accent) {
        used = Math.max(0f, Math.min(1f, fraction));
        label = Math.round(used * 100) + "%";
        arc.setColor(used >= 0.9f ? 0xFFFF453A : used >= 0.75f ? 0xFFFF9F0A : accent);
        animate().cancel();
        float from = sweep;
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(from, used * 360f);
        a.setDuration(600);
        a.addUpdateListener(v -> {
            sweep = (float) v.getAnimatedValue();
            invalidate();
        });
        a.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float inset = arc.getStrokeWidth() / 2 + 2;
        float size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f, top = (getHeight() - size) / 2f;
        oval.set(left + inset, top + inset, left + size - inset, top + size - inset);
        c.drawOval(oval, track);
        c.drawArc(oval, -90, sweep, false, arc);
        float y = getHeight() / 2f - (text.descent() + text.ascent()) / 2f;
        c.drawText(label, getWidth() / 2f, y, text);
    }
}
