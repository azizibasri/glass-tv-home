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
    private final boolean motionAllowed;
    private boolean animate, rainbow;
    private LinearGradient gradient;
    private String text = "";
    private boolean running;
    /** Taller digits: 1 = normal, 2.5 = two and a half times as tall, same width. */
    private float stretch = 1f;
    private boolean glowOn = true;
    private final android.graphics.Rect bounds = new android.graphics.Rect();
    /**
     * The glow, blurred once into a bitmap (it only changes when the time, font or size does) and only
     * recoloured on each frame: blurring large text 20 times a second is what makes slow units stutter.
     */
    private android.graphics.Bitmap glowCache;
    private String glowKey = "";
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    /** About 20 frames a second: smooth enough for a slow colour flow, light on a head unit. */
    private static final long FRAME_MS = 50;

    RainbowClock(Context ctx, boolean rainbow) {
        super(ctx);
        float scale = Settings.Global.getFloat(ctx.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        motionAllowed = scale > 0f;
        this.rainbow = rainbow;
        animate = rainbow && motionAllowed;
        Typeface t = Typeface.create("sans-serif-light", Typeface.NORMAL);
        for (Paint p : new Paint[]{glow, face}) {
            p.setTypeface(t);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
        }
        applyColours();
    }

    /** Rainbow colours (flowing) or plain white digits. */
    void setRainbow(boolean on) {
        if (on == rainbow) return;
        rainbow = on;
        animate = on && motionAllowed;
        running = running && animate;
        applyColours();
        invalidate();
    }

    private void applyColours() {
        gradient = null;
        if (rainbow) {
            face.clearShadowLayer();
        } else {
            face.setShader(null);
            glow.setShader(null);
            face.setColor(0xFFFFFFFF);
            glow.setColor(0xFFFFFFFF);
            face.setShadowLayer(6, 0, 2, 0x99000000);
        }
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

    /** Stretches the digits up and down (1 to 2.5) without making them wider; kept inside the box. */
    void setStretch(float s) {
        stretch = Math.max(1f, Math.min(2.5f, s));
        invalidate();
    }

    /** The soft glow behind the digits, on or off. The rainbow keeps flowing either way. */
    void setGlow(boolean on) {
        glowOn = on;
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
        // Centre on the digits' real height, then stretch them up and down about the middle, never past
        // the box (the glow needs a little room).
        face.getTextBounds(text, 0, text.length(), bounds);
        float y = getHeight() / 2f - bounds.exactCenterY();
        float fit = bounds.height() > 0 ? getHeight() * 0.86f / bounds.height() : 1f;
        float sy = Math.max(1f, Math.min(stretch, fit));
        if (glowOn) drawGlow(c, cx, y, sy);
        int save = c.save();
        c.scale(1f, sy, cx, getHeight() / 2f);
        drawDigits(c, w, cx, y);
        c.restoreToCount(save);
        if (running) postInvalidateDelayed(FRAME_MS);
    }

    /** The cached glow, tinted with this frame's colour and brightness. */
    private void drawGlow(Canvas c, float cx, float y, float sy) {
        String key = text + "|" + face.getTextSize() + "|" + face.getTypeface() + "|" + sy + "|" + getWidth() + "x" + getHeight();
        if (glowCache == null || !key.equals(glowKey)) {
            if (glowCache != null) glowCache.recycle();
            glowCache = null;
            if (getWidth() <= 0 || getHeight() <= 0) return;
            glowCache = android.graphics.Bitmap.createBitmap(getWidth(), getHeight(), android.graphics.Bitmap.Config.ALPHA_8);
            Canvas gc = new Canvas(glowCache);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(face.getTypeface());
            p.setTextSize(face.getTextSize());
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
            p.setMaskFilter(new android.graphics.BlurMaskFilter(face.getTextSize() * 0.12f,
                    android.graphics.BlurMaskFilter.Blur.NORMAL));
            gc.scale(1f, sy, cx, getHeight() / 2f);
            gc.drawText(text, cx, y, p);
            glowKey = key;
        }
        long now = SystemClock.uptimeMillis();
        float breath = animate ? 0.5f + 0.5f * (float) Math.sin(now / 900.0) : 0.6f;
        int tint = 0xFFFFFFFF;
        if (rainbow) {
            float phase = animate ? (now % CYCLE_MS) / (float) CYCLE_MS : 0f;
            hsv[0] = (phase * 360f + 200f) % 360f;
            tint = Color.HSVToColor(0xFF, hsv);
        }
        glowPaint.setColor(tint);
        glowPaint.setAlpha((int) ((rainbow ? 150 : 110) + 70 * breath));
        c.drawBitmap(glowCache, 0, 0, glowPaint);
    }

    private void drawDigits(Canvas c, float w, float cx, float y) {
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

        c.drawText(text, cx, y, face);
    }
}
