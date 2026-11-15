package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

import java.util.Calendar;

/**
 * An analog clock in the style of Apple's, in three faces:
 * 1 "Classic": a white dial with black hour numbers and minute ticks, black hands, an orange second hand.
 * 2 "Night": a dark see-through dial with white numbers, white hands, an orange second hand.
 * 3 "Minimal": no dial, only twelve white hour marks, slim white hands, an orange second hand.
 * The second hand sweeps smoothly while Home is on screen; otherwise the clock stands still.
 */
final class AnalogClockView extends View {
    static final String[] FACES = {"analog1", "analog2", "analog3"};

    private final Paint dial = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hand = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint second = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Calendar cal = Calendar.getInstance();
    private int face = 1;
    private boolean running;

    AnalogClockView(Context ctx) {
        super(ctx);
        tick.setStrokeCap(Paint.Cap.ROUND);
        hand.setStrokeCap(Paint.Cap.ROUND);
        second.setStrokeCap(Paint.Cap.ROUND);
        second.setColor(0xFFFF9F0A);
        num.setTextAlign(Paint.Align.CENTER);
        num.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    /** Face 1, 2 or 3 (see the class). */
    void setFace(int f) {
        face = Math.max(1, Math.min(3, f));
        invalidate();
    }

    static int faceOf(String key) {
        for (int i = 0; i < FACES.length; i++) if (FACES[i].equals(key)) return i + 1;
        return 0;
    }

    void setRunning(boolean r) {
        running = r;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(cx, cy) * 0.94f;
        if (r <= 0) return;
        boolean light = face == 1;
        int ink = light ? 0xFF1C1C1E : 0xFFFFFFFF;

        if (face == 1) {
            dial.setColor(0xFFF5F5F7);
            c.drawCircle(cx, cy, r, dial);
        } else if (face == 2) {
            dial.setColor(0x59000000);
            c.drawCircle(cx, cy, r, dial);
            dial.setColor(0x33FFFFFF);
            dial.setStyle(Paint.Style.STROKE);
            dial.setStrokeWidth(Math.max(1f, r * 0.012f));
            c.drawCircle(cx, cy, r, dial);
            dial.setStyle(Paint.Style.FILL);
        }

        // Ticks: every minute on faces 1 and 2, only the hours on the minimal face.
        for (int i = 0; i < 60; i++) {
            boolean hour = i % 5 == 0;
            if (face == 3 && !hour) continue;
            double a = Math.toRadians(i * 6);
            float outer = r * (face == 3 ? 0.98f : 0.95f);
            float inner = outer - r * (face == 3 ? 0.14f : hour ? 0.08f : 0.04f);
            tick.setColor(face == 3 ? 0xFFFFFFFF : hour ? ink : (light ? 0x801C1C1E : 0x80FFFFFF));
            tick.setStrokeWidth(r * (face == 3 ? 0.035f : hour ? 0.018f : 0.008f));
            c.drawLine(cx + (float) Math.sin(a) * inner, cy - (float) Math.cos(a) * inner,
                    cx + (float) Math.sin(a) * outer, cy - (float) Math.cos(a) * outer, tick);
        }
        // Hour numbers on faces 1 and 2.
        if (face != 3) {
            num.setColor(ink);
            num.setTextSize(r * 0.2f);
            float nr = r * 0.72f;
            Paint.FontMetrics fm = num.getFontMetrics();
            float mid = (fm.ascent + fm.descent) / 2f;
            for (int h = 1; h <= 12; h++) {
                double a = Math.toRadians(h * 30);
                c.drawText(String.valueOf(h), cx + (float) Math.sin(a) * nr, cy - (float) Math.cos(a) * nr - mid, num);
            }
        }

        cal.setTimeInMillis(System.currentTimeMillis());
        float ms = cal.get(Calendar.MILLISECOND);
        float sec = cal.get(Calendar.SECOND) + (running ? ms / 1000f : 0f);
        float min = cal.get(Calendar.MINUTE) + sec / 60f;
        float hr = (cal.get(Calendar.HOUR) % 12) + min / 60f;

        hand.setColor(ink);
        float slim = face == 3 ? 0.7f : 1f;
        drawHand(c, cx, cy, hr * 30f, r * 0.5f, r * 0.055f * slim);
        drawHand(c, cx, cy, min * 6f, r * 0.78f, r * 0.04f * slim);
        // The orange second hand, with a short tail, over a small centre dot.
        double sa = Math.toRadians(sec * 6f);
        second.setStrokeWidth(Math.max(1.5f, r * 0.014f));
        c.drawLine(cx - (float) Math.sin(sa) * r * 0.18f, cy + (float) Math.cos(sa) * r * 0.18f,
                cx + (float) Math.sin(sa) * r * 0.88f, cy - (float) Math.cos(sa) * r * 0.88f, second);
        c.drawCircle(cx, cy, r * 0.035f, second);
        hand.setColor(light ? 0xFFF5F5F7 : 0xFF1C1C1E);
        c.drawCircle(cx, cy, r * 0.015f, hand);

        // About 15 frames a second: a smooth sweep, little work. Still otherwise (Home redraws it each minute).
        if (running) postInvalidateDelayed(66);
    }

    private void drawHand(Canvas c, float cx, float cy, float degrees, float length, float width) {
        double a = Math.toRadians(degrees);
        hand.setStrokeWidth(width);
        c.drawLine(cx - (float) Math.sin(a) * length * 0.12f, cy + (float) Math.cos(a) * length * 0.12f,
                cx + (float) Math.sin(a) * length, cy - (float) Math.cos(a) * length, hand);
    }
}
