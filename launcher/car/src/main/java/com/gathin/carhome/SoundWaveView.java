package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * A moving, colourful wave for the dashboard: a smooth line with a soft mirrored fill whose peaks rise and
 * fall at random, like an equaliser. It is decoration only: it does not listen to any sound. It moves only
 * while the dashboard is on screen (about 25 frames a second) and stands still otherwise.
 */
final class SoundWaveView extends View {
    /** One peak about every 8 dp, so a longer wave has more of them rather than wider ones. */
    private int bands = 24;
    /** How often each peak picks a new random height. */
    private static final long RETARGET_MS = 260;
    private float[] target = new float[24], shown = new float[24];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), fillPath = new Path();
    private final Random random = new Random();
    private boolean running;
    private long lastFrame, lastTarget;

    SoundWaveView(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        for (int b = 0; b < bands; b++) shown[b] = target[b] = 0.2f;
    }

    void start() {
        running = true;
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
    }

    /** New random heights: taller in the middle-left like music, with a little smoothing between bands. */
    private void retarget() {
        float prev = 0.3f;
        for (int b = 0; b < bands; b++) {
            float x = b / (float) (bands - 1);
            float shape = 0.55f + 0.45f * (float) Math.sin(Math.PI * Math.min(1f, x * 1.4f));
            float r = 0.15f + 0.85f * random.nextFloat();
            float v = Math.max(0.06f, Math.min(1f, shape * r));
            target[b] = 0.6f * v + 0.4f * prev;
            prev = target[b];
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        int n = Math.max(12, Math.min(240, Math.round(w / (8 * getResources().getDisplayMetrics().density))));
        if (n != bands) {
            float[] t = new float[n], sh = new float[n];
            for (int b = 0; b < n; b++) {
                int from = Math.min(bands - 1, Math.round(b * (bands - 1) / (float) Math.max(1, n - 1)));
                t[b] = target[from];
                sh[b] = shown[from];
            }
            bands = n;
            target = t;
            shown = sh;
        }
        line.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0xFF64D2FF, 0xFF0A84FF, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF9F0A}, null, Shader.TileMode.CLAMP));
        area.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{0x4064D2FF, 0x400A84FF, 0x40BF5AF2, 0x40FF375F, 0x40FF9F0A}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        long dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
        lastFrame = now;
        if (running && now - lastTarget > RETARGET_MS) {
            lastTarget = now;
            retarget();
        }
        float k = 1f - (float) Math.exp(-dt / 110.0);
        for (int b = 0; b < bands; b++) shown[b] += (target[b] - shown[b]) * k;

        int w = getWidth(), h = getHeight();
        float mid = h / 2f, amp = h * 0.46f;
        // The top line through the peaks (a smooth curve), mirrored below the middle.
        path.reset();
        fillPath.reset();
        float step = w / (float) (bands - 1);
        for (int b = 0; b < bands; b++) {
            float x = b * step, y = mid - shown[b] * amp;
            if (b == 0) {
                path.moveTo(x, y);
            } else {
                float px = (b - 1) * step, py = mid - shown[b - 1] * amp;
                path.cubicTo((px + x) / 2f, py, (px + x) / 2f, y, x, y);
            }
        }
        fillPath.addPath(path);
        for (int b = bands - 1; b >= 0; b--) {
            float x = b * step, y = mid + shown[b] * amp;
            if (b == bands - 1) {
                fillPath.lineTo(x, y);
            } else {
                float px = (b + 1) * step, py = mid + shown[b + 1] * amp;
                fillPath.cubicTo((px + x) / 2f, py, (px + x) / 2f, y, x, y);
            }
        }
        fillPath.close();
        c.drawPath(fillPath, area);
        c.drawPath(path, line);
        c.save();
        c.scale(1f, -1f, 0, mid);
        line.setAlpha(110);
        c.drawPath(path, line);
        line.setAlpha(255);
        c.restore();

        if (running) postInvalidateDelayed(40);   // about 25 frames a second, only while on screen
    }
}
