package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

/**
 * The dashboard clock: thin large digits filled with a rainbow that flows slowly across them, with a soft
 * glow behind that shifts colour with it and breathes. Animates at about 30 fps and only while Home is on
 * screen; with animations turned off in Android's settings it stays a still rainbow.
 */
final class RainbowClock extends View {
    private static final int[] COLORS = {0xFFFF375F, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF,
            0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F};
    /** One full trip of the colours across the digits. */
    private static final long CYCLE_MS = 6_000;
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final float[] hsv = {0f, 0.75f, 1f};
    private final boolean animate, rainbow;
    private LinearGradient gradient;
    private String text = "";
    private boolean running;

    RainbowClock(Context ctx, boolean rainbow) {
        super(ctx);
        float scale = Settings.Global.getFloat(ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        this.rainbow = rainbow;
        animate = rainbow && scale > 0f;
        Typeface t = Typeface.create("sans-serif-light", Typeface.NORMAL);
        for (Paint p : new Paint[]{glow, face}) {
            p.setTypeface(t);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
        }
        if (!rainbow) face.setShadowLayer(6, 0, 2, 0x99000000);
    }

    void setText(CharSequence s) {
        String n = String.valueOf(s);
        if (!n.equals(text)) {
            text = n;
            gradient = null;
            invalidate();
        }
    }

    void setTypeface(Typeface t) {
        glow.setTypeface(t);
        face.setTypeface(t);
        gradient = null;
        invalidate();
    }

    void setTextSizePx(float px) {
        glow.setTextSize(px);
        face.setTextSize(px);
        gradient = null;
        invalidate();
    }

    /** Runs the flow while Home is resumed; stops it (one still frame) otherwise. */
    void setRunning(boolean r) {
        running = r && animate;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (text.isEmpty()) return;
        float w = face.measureText(text);
        float cx = getWidth() / 2f;
        float y = getHeight() / 2f - (face.ascent() + face.descent()) / 2f;
        if (!rainbow) {
            c.drawText(text, cx, y, face);
            return;
        }
        if (gradient == null) {
            gradient = new LinearGradient(0, 0, w, 0, COLORS, null, Shader.TileMode.REPEAT);
            glow.setShader(gradient);
            face.setShader(gradient);
        }
        long now = SystemClock.uptimeMillis();
        float phase = animate ? (now % CYCLE_MS) / (float) CYCLE_MS : 0f;
        matrix.setTranslate(cx - w / 2f + phase * w, 0);
        gradient.setLocalMatrix(matrix);

        // Glow: the same rainbow, drawn under a coloured shadow that drifts through the hues and breathes.
        float breath = animate ? 0.5f + 0.5f * (float) Math.sin(now / 900.0) : 0.6f;
        hsv[0] = (phase * 360f + 200f) % 360f;
        int glowColor = Color.HSVToColor((int) (110 + 90 * breath), hsv);
        glow.setShadowLayer(face.getTextSize() * (0.10f + 0.06f * breath), 0, 0, glowColor);
        glow.setAlpha((int) (120 + 80 * breath));
        c.drawText(text, cx, y, glow);
        c.drawText(text, cx, y, face);
        if (running) postInvalidateDelayed(33);
    }
}
