package com.gathin.carhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.audiofx.Visualizer;
import android.os.SystemClock;
import android.view.View;

/**
 * A moving picture of the sound: a smooth frequency line (bass on the left, treble on the right) with a
 * soft fill under it and its mirror, following the music that plays. It listens to the unit's sound
 * output through Android's Visualizer, which needs the microphone permission: the sound is only measured
 * live, nothing is recorded or kept. Without the permission, or on units that refuse it, a gentle wave
 * moves while music plays instead. Runs only while Home is on screen.
 */
final class SoundWaveView extends View {
    private static final int BANDS = 24;
    private final float[] level = new float[BANDS], shown = new float[BANDS];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), fillPath = new Path();
    private Visualizer viz;
    private boolean running, live;
    private long lastFrame;

    SoundWaveView(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.5f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        running = true;
        if (viz == null && hasPermission(getContext())) {
            try {
                viz = new Visualizer(0);   // the whole sound output
                viz.setEnabled(false);
                int[] range = Visualizer.getCaptureSizeRange();
                viz.setCaptureSize(Math.min(range[1], 512));
                viz.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(Visualizer v, byte[] w, int rate) {
                    }

                    @Override
                    public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                        onFft(fft);
                    }
                }, Math.min(Visualizer.getMaxCaptureRate(), 20_000), false, true);
                viz.setEnabled(true);
                live = true;
            } catch (RuntimeException e) {
                // Some units refuse the global output: fall back to the gentle wave.
                release();
            }
        }
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
        release();
    }

    private void release() {
        live = false;
        if (viz != null) {
            try {
                viz.setEnabled(false);
                viz.release();
            } catch (RuntimeException ignored) {
            }
            viz = null;
        }
    }

    /** FFT bytes (real, imaginary pairs) to band levels, spaced like hearing: more bands for the low notes. */
    private void onFft(byte[] fft) {
        int bins = fft.length / 2;
        if (bins < 4) return;
        for (int b = 0; b < BANDS; b++) {
            int lo = (int) Math.max(1, Math.pow(bins, b / (float) BANDS));
            int hi = (int) Math.max(lo + 1, Math.pow(bins, (b + 1) / (float) BANDS));
            float sum = 0;
            int n = 0;
            for (int k = lo; k < hi && k < bins; k++) {
                float re = fft[2 * k], im = fft[2 * k + 1];
                sum += (float) Math.hypot(re, im);
                n++;
            }
            float mag = n > 0 ? sum / n : 0;
            // Decibels, scaled to 0..1.
            float db = (float) (20 * Math.log10(1 + mag));
            level[b] = Math.max(0f, Math.min(1f, db / 40f));
        }
    }

    private boolean musicPlaying() {
        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        return am != null && am.isMusicActive();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
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
        boolean playing = running && musicPlaying();
        if (!live) {
            // The gentle wave: a few slow sines, only while music plays.
            double t = now / 1000.0;
            for (int b = 0; b < BANDS; b++) {
                float x = b / (float) BANDS;
                level[b] = playing ? (float) (0.35 + 0.25 * Math.sin(t * 2.1 + x * 7) + 0.15 * Math.sin(t * 3.7 - x * 11)) * (1f - 0.4f * x) : 0f;
            }
        } else if (!playing) {
            for (int b = 0; b < BANDS; b++) level[b] = 0f;
        }
        float k = 1f - (float) Math.exp(-dt / 70.0);
        boolean moving = false;
        for (int b = 0; b < BANDS; b++) {
            shown[b] += (level[b] - shown[b]) * k;
            if (Math.abs(level[b] - shown[b]) > 0.002f || level[b] > 0.01f) moving = true;
        }

        int w = getWidth(), h = getHeight();
        float mid = h / 2f, amp = h * 0.46f;
        // The top line through the band levels (a smooth curve), mirrored below the middle.
        path.reset();
        fillPath.reset();
        float step = w / (float) (BANDS - 1);
        for (int b = 0; b < BANDS; b++) {
            float x = b * step, y = mid - Math.max(0.02f, shown[b]) * amp;
            if (b == 0) {
                path.moveTo(x, y);
            } else {
                float px = (b - 1) * step, py = mid - Math.max(0.02f, shown[b - 1]) * amp;
                path.cubicTo((px + x) / 2f, py, (px + x) / 2f, y, x, y);
            }
        }
        fillPath.addPath(path);
        for (int b = BANDS - 1; b >= 0; b--) {
            float x = b * step, y = mid + Math.max(0.02f, shown[b]) * amp;
            if (b == BANDS - 1) {
                fillPath.lineTo(x, y);
            } else {
                float px = (b + 1) * step, py = mid + Math.max(0.02f, shown[b + 1]) * amp;
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

        if (running && (moving || playing)) postInvalidateDelayed(40);   // about 25 frames a second
        else if (running) postInvalidateDelayed(500);                   // idle: check now and then
    }
}
