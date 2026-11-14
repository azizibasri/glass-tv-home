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

/**
 * The big digital speedometer: the speed in large digits (in the clock's font), its unit and source, and
 * under it the turbo boost: the value, and a bar from vacuum on the left through zero to full boost on the
 * right, blue below zero, orange to red above, with a mark at the highest boost of the last 10 seconds;
 * and under that engine torque, drawn the same way (same text sizes, same bar) from 0 to the peak torque.
 */
final class BigSpeedView extends View {
    private static final long PEAK_HOLD_MS = 10_000;
    private final float d;
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint torqueFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF(), lit = new RectF(), torqueBar = new RectF();
    private final float torqueMax;
    private float torque = Float.NaN, shownTorque;
    private boolean torqueEstimated;
    private final String unitText;
    private final boolean psi;
    private final float maxBar;
    private float speed = Float.NaN, shownSpeed, boost = Float.NaN, shownBoost, peak = Float.NaN;
    private long peakAt, lastFrame;
    private String source = "";

    BigSpeedView(Context ctx, String unitText, boolean psi, float maxBar, float torqueMaxNm) {
        super(ctx);
        this.torqueMax = torqueMaxNm;
        this.unitText = unitText;
        this.psi = psi;
        this.maxBar = maxBar;
        d = getResources().getDisplayMetrics().density;
        digits.setColor(0xFFFFFFFF);
        digits.setTextAlign(Paint.Align.CENTER);
        digits.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        unit.setColor(0xB3FFFFFF);
        unit.setLetterSpacing(0.12f);
        unit.setTextAlign(Paint.Align.CENTER);
        unit.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setColor(0xA6FFFFFF);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setColor(0xFFFFFFFF);
        value.setTextAlign(Paint.Align.RIGHT);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        track.setColor(0x26FFFFFF);
        mark.setColor(0xFFFFFFFF);
        mark.setStrokeWidth(Math.max(1.5f, 1.5f * d));
    }

    void setTypeface(Typeface t) {
        digits.setTypeface(t);
        invalidate();
    }

    /** Speed (NaN: none) with its source label, and boost in bar (NaN: none). */
    void set(float speedValue, String sourceLabel, float boostBar) {
        speed = speedValue;
        source = sourceLabel;
        boost = boostBar;
        long now = SystemClock.uptimeMillis();
        if (!Float.isNaN(boostBar) && (Float.isNaN(peak) || boostBar > peak || now - peakAt > PEAK_HOLD_MS)) {
            peak = boostBar;
            peakAt = now;
        }
        postInvalidateOnAnimation();
    }

    /** The space between the speed, the boost row, the torque row and what is around them. */
    private float gap;
    private float digitsBase, unitBase, boostBase, torqueBase;

    private float sizeScale = 1f, stretch = 1f;

    /**
     * The digits' size (0.4 to 1 of the space they have) and height (1 to 2.5: taller and narrower, still
     * inside their space). The spacing stays even: smaller digits leave more room between everything.
     */
    void setDigitStyle(float size, float height) {
        sizeScale = Math.max(0.4f, Math.min(1f, size));
        stretch = Math.max(1f, Math.min(2.5f, height));
        if (getWidth() > 0 && getHeight() > 0) onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        invalidate();
    }

    /** Sets the even spacing (at least {@code px}; more when there is room to spare). */
    void setGap(int px) {
        gap = px;
        requestLayout();
    }

    /** Engine torque in Nm (NaN: none); estimated: worked out from engine load. */
    void setTorque(float nm, boolean estimated) {
        torque = nm;
        torqueEstimated = estimated;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // Top to bottom: speed digits, unit and source, turbo boost row, torque row, with the same space
        // above, between and below them all.
        float rowH = Math.max(50 * d, h * 0.22f);
        label.setTextSize(Math.max(10 * d, rowH * 0.17f));
        value.setTextSize(Math.max(15 * d, rowH * 0.30f));
        unit.setTextSize(Math.max(11 * d, h * 0.045f));
        float bh = Math.max(6 * d, rowH * 0.13f), pad = 6 * d;
        float valueCap = capHeight(value, 1f), unitCap = capHeight(unit, 1f);
        float rowVis = valueCap + bh * 0.9f + bh;          // value top to bar bottom
        float unitGap = unit.getTextSize() * 0.8f;
        float g = Math.max(gap, 4 * d);
        float room = h - 4 * g - 2 * rowVis - unitGap - unitCap;
        float digitRatio = capHeight(digits, 100f) / 100f;
        // As tall as the room allows, as long as a three-digit speed still fits the width.
        digits.setTextSize(100f);
        float widthPer100 = digits.measureText("188") / 0.92f;
        float fit = Math.max(10 * d, Math.min(room / digitRatio, w * 100f / widthPer100));
        // Size first, then the stretch, which never takes the digits out of their space: past it they get
        // narrower instead.
        float digitCap = Math.min(Math.max(room, 4 * d), fit * digitRatio * sizeScale * stretch);
        digits.setTextSize(Math.max(4 * d, digitCap / (digitRatio * stretch)));
        // Room to spare (a wide speed hits its width limit first): share it evenly between the spaces.
        float spare = h - (digitCap + unitGap + unitCap + 2 * rowVis);
        g = Math.max(g, spare / 4f);
        digitsBase = g + digitCap;
        unitBase = digitsBase + unitGap + unitCap;
        boostBase = unitBase + g + valueCap;
        bar.set(pad, boostBase + bh * 0.9f, w - pad, boostBase + bh * 1.9f);
        torqueBase = bar.bottom + g + valueCap;
        torqueBar.set(pad, torqueBase + bh * 0.9f, w - pad, torqueBase + bh * 1.9f);
        torqueFill.setShader(new LinearGradient(torqueBar.left, 0, torqueBar.right, 0, 0xFF30D158, 0xFFFF9F0A,
                Shader.TileMode.CLAMP));
        fill.setShader(new LinearGradient(bar.left, 0, bar.right, 0,
                new int[]{0xFF64D2FF, 0xFF64D2FF, 0xFFFF9F0A, 0xFFFF453A},
                new float[]{0f, SpeedMath.fractionOf(0f, maxBar), SpeedMath.fractionOf(0.2f, maxBar), 1f},
                Shader.TileMode.CLAMP));
    }

    /** Height of capitals and digits drawn with {@code p}, scaled by {@code k}. */
    private final android.graphics.Rect bounds = new android.graphics.Rect();

    private float capHeight(Paint p, float sizeOrOne) {
        float old = p.getTextSize();
        if (sizeOrOne != 1f) p.setTextSize(sizeOrOne);
        p.getTextBounds("8H", 0, 2, bounds);
        float hgt = bounds.height();
        p.setTextSize(old);
        return hgt;
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        long dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
        lastFrame = now;
        shownSpeed = SpeedMath.smooth(shownSpeed, Float.isNaN(speed) ? 0f : speed, dt);
        shownBoost = SpeedMath.smooth(shownBoost, Float.isNaN(boost) ? 0f : boost, dt);
        shownTorque = SpeedMath.smooth(shownTorque, Float.isNaN(torque) ? 0f : torque, dt);
        int w = getWidth();

        // Speed.
        String text = Float.isNaN(speed) ? "--" : String.valueOf(SpeedMath.display(shownSpeed));
        if (stretch > 1.001f) {
            c.save();
            c.scale(1f, stretch, w / 2f, digitsBase);
            c.drawText(text, w / 2f, digitsBase, digits);
            c.restore();
        } else {
            c.drawText(text, w / 2f, digitsBase, digits);
        }
        c.drawText(unitText.toUpperCase(java.util.Locale.ROOT) + "  ·  " + source, w / 2f, unitBase, unit);

        // Turbo boost.
        float rowY = boostBase;
        c.drawText(getContext().getString(R.string.turbo_boost), bar.left, rowY, label);
        c.drawText(SpeedMath.boostText(Float.isNaN(boost) ? Float.NaN : shownBoost, psi), bar.right, rowY, value);
        float r = bar.height() / 2;
        c.drawRoundRect(bar, r, r, track);
        float zero = bar.left + bar.width() * SpeedMath.fractionOf(0f, maxBar);
        if (!Float.isNaN(boost)) {
            float at = bar.left + bar.width() * SpeedMath.boostFraction(shownBoost, maxBar);
            lit.set(Math.min(zero, at), bar.top, Math.max(zero, at), bar.bottom);
            if (lit.width() > 1) c.drawRoundRect(lit, r, r, fill);
            if (!Float.isNaN(peak) && peak > 0.05f && now - peakAt <= PEAK_HOLD_MS) {
                float px = bar.left + bar.width() * SpeedMath.boostFraction(peak, maxBar);
                c.drawLine(px, bar.top - 3 * d, px, bar.bottom + 3 * d, mark);
            }
        }
        // Zero mark: outside air pressure.
        mark.setAlpha(0x80);
        c.drawLine(zero, bar.top - 2 * d, zero, bar.bottom + 2 * d, mark);
        mark.setAlpha(0xFF);

        // Torque: the same row as the boost, from 0 to the engine's peak torque.
        float tRowY = torqueBase;
        c.drawText(getContext().getString(torqueEstimated ? R.string.torque_estimated : R.string.torque),
                torqueBar.left, tRowY, label);
        c.drawText(Float.isNaN(torque) ? "-- Nm" : Math.round(torque) + " Nm", torqueBar.right, tRowY, value);
        c.drawRoundRect(torqueBar, r, r, track);
        float tf = SpeedMath.fraction(Math.max(0f, shownTorque), torqueMax);
        if (!Float.isNaN(torque) && tf > 0.005f) {
            lit.set(torqueBar.left, torqueBar.top,
                    torqueBar.left + Math.max(torqueBar.height(), torqueBar.width() * tf), torqueBar.bottom);
            c.drawRoundRect(lit, r, r, torqueFill);
        }

        boolean moving = Math.abs(shownTorque - (Float.isNaN(torque) ? 0f : torque)) > 0.05f || Math.abs(shownSpeed - (Float.isNaN(speed) ? 0f : speed)) > 0.05f
                || Math.abs(shownBoost - (Float.isNaN(boost) ? 0f : boost)) > 0.002f;
        if (moving) postInvalidateOnAnimation();
        else lastFrame = 0;
    }
}
