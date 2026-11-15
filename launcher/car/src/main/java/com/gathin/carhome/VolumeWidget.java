package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

/**
 * Volume: minus, mute and plus, evenly spaced. Tap minus or plus for one step, hold to keep changing it;
 * tap mute to mute or unmute. The buttons press the unit's own volume keys, so the unit shows its own
 * volume bar. Resize and move it like every widget (hold, then drag).
 */
final class VolumeWidget extends LinearLayout {
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MuteIcon muteIcon;
    private boolean muted;

    VolumeWidget(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        int pad = Math.round(12 * d);
        setPadding(pad, pad, pad, pad);
        int btn = Math.round(56 * d);
        addView(button(ctx, "\u2212", -1), new LayoutParams(btn, btn));
        addView(new Space(ctx), new LayoutParams(0, 1, 1f));
        muteIcon = new MuteIcon(ctx);
        addView(muteButton(ctx), new LayoutParams(btn, btn));
        addView(new Space(ctx), new LayoutParams(0, 1, 1f));
        addView(button(ctx, "+", 1), new LayoutParams(btn, btn));
    }

    /** The three buttons as big as the widget allows, with an even gap of at least a third of a button. */
    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        if (w > 0 && h > 0) {
            int availW = w - getPaddingLeft() - getPaddingRight(), availH = h - getPaddingTop() - getPaddingBottom();
            int btn = Math.max(1, Math.min(availH, Math.round(availW / 3.67f)));
            for (int i = 0; i < getChildCount(); i += 2) {
                LayoutParams lp = (LayoutParams) getChildAt(i).getLayoutParams();
                lp.width = lp.height = btn;
                View c = getChildAt(i);
                if (c instanceof TextView) ((TextView) c).setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, btn * 0.5f);
                else {
                    int in = Math.round(btn * 0.26f);
                    c.setPadding(in, in, in, in);
                }
            }
        }
        super.onMeasure(wSpec, hSpec);
    }

    private static GradientDrawable round(float d) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x26FFFFFF);
        g.setStroke(Math.max(1, Math.round(d)), 0x4DFFFFFF);
        return g;
    }

    private View muteButton(Context ctx) {
        float d = getResources().getDisplayMetrics().density;
        FrameLayout b = new FrameLayout(ctx);
        b.setBackground(round(d));
        int in = Math.round(14 * d);
        b.addView(muteIcon, new FrameLayout.LayoutParams(-1, -1));
        b.setPadding(in, in, in, in);
        b.setContentDescription("Mute");
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start();
                    return true;
                case MotionEvent.ACTION_UP:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    toggleMute();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    return true;
                default:
                    return true;
            }
        });
        return b;
    }

    private void toggleMute() {
        muted = !muted;
        muteIcon.setMuted(muted);
        // KEYCODE_VOLUME_MUTE, as the unit's own mute key; or Android's mute for the media volume.
        press(164, () -> {
            if (audio == null) return;
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.FLAG_SHOW_UI);
            } catch (SecurityException ignored) {
            }
        });
    }

    private View button(Context ctx, String sign, int dir) {
        TextView b = new TextView(ctx);
        b.setText(sign);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(30);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        b.setBackground(round(getResources().getDisplayMetrics().density));
        b.setContentDescription(dir > 0 ? "Volume up" : "Volume down");
        // Tap: one step. Hold: a step every 150 ms after a short wait.
        Runnable repeat = new Runnable() {
            @Override
            public void run() {
                step(dir);
                main.postDelayed(this, 220);
            }
        };
        b.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).start();
                    step(dir);
                    main.postDelayed(repeat, 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    main.removeCallbacks(repeat);
                    return true;
                default:
                    return true;
            }
        });
        return b;
    }

    /** One volume step; a step up or down also ends mute, as on the unit. */
    private void step(int dir) {
        if (muted) {
            muted = false;
            muteIcon.setMuted(false);
        }
        press(dir > 0 ? 24 : 25, () -> androidStep(dir));
    }

    /**
     * A key press through the unit's debugging service (the one cache cleaning uses), so head units that
     * keep their volume outside Android (in the radio chip) change too and show their own volume bar.
     * Where that service is off or not allowed, the fallback: Android's media volume.
     */
    private void press(int keyCode, Runnable fallback) {
        Context app = getContext().getApplicationContext();
        KEYS.execute(() -> {
            boolean viaKey = false;
            if (!shellRefused) {
                try {
                    viaKey = LocalShell.shell(app, false, "shell:input keyevent " + keyCode);
                    if (!viaKey) {
                        shellRefused = true;   // not allowed yet: don't try each tap
                        askOnce(app);
                    }
                } catch (Exception e) {
                    shellRefused = true;   // debugging service off
                }
            }
            if (!viaKey) main.post(fallback);
        });
    }

    private void androidStep(int dir) {
        if (audio == null) return;
        try {
            // The media volume (music, radio, navigation voice), with the unit's volume bar as feedback.
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
        } catch (SecurityException ignored) {
            // Do Not Disturb can refuse it.
        }
    }

    /**
     * Once ever: asks the unit to allow Glass Car ("Allow USB debugging?", tick Always allow), after this
     * step has already gone through Android's volume. Once allowed, the next taps use the volume key.
     */
    private void askOnce(Context app) {
        android.content.SharedPreferences p = app.getSharedPreferences("car", Context.MODE_PRIVATE);
        if (p.getBoolean("volume_asked", false)) return;
        p.edit().putBoolean("volume_asked", true).apply();
        new Thread(() -> {
            try {
                if (LocalShell.shell(app, true, null)) shellRefused = false;
            } catch (Exception ignored) {
            }
        }, "volume-allow").start();
    }

    /** Key presses one after another, off the main thread. */
    private static final java.util.concurrent.ExecutorService KEYS = java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Set when the unit's debugging service is off or doesn't allow Glass Car: Android's volume then. */
    private static volatile boolean shellRefused;

    void start() {
    }

    void stop() {
        main.removeCallbacksAndMessages(null);
    }

    /** A speaker with sound waves, or with a cross when muted. */
    private static final class MuteIcon extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path speaker = new Path();
        private final RectF r = new RectF();
        private boolean muted;

        MuteIcon(Context ctx) {
            super(ctx);
            p.setColor(0xFFFFFFFF);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void setMuted(boolean m) {
            muted = m;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            if (s <= 0) return;
            float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
            speaker.reset();
            speaker.moveTo(ox + s * 0.08f, oy + s * 0.36f);
            speaker.lineTo(ox + s * 0.26f, oy + s * 0.36f);
            speaker.lineTo(ox + s * 0.50f, oy + s * 0.14f);
            speaker.lineTo(ox + s * 0.50f, oy + s * 0.86f);
            speaker.lineTo(ox + s * 0.26f, oy + s * 0.64f);
            speaker.lineTo(ox + s * 0.08f, oy + s * 0.64f);
            speaker.close();
            p.setStyle(Paint.Style.FILL);
            c.drawPath(speaker, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.08f);
            if (muted) {
                c.drawLine(ox + s * 0.64f, oy + s * 0.36f, ox + s * 0.92f, oy + s * 0.64f, p);
                c.drawLine(ox + s * 0.92f, oy + s * 0.36f, ox + s * 0.64f, oy + s * 0.64f, p);
            } else {
                r.set(ox + s * 0.38f, oy + s * 0.32f, ox + s * 0.74f, oy + s * 0.68f);
                c.drawArc(r, -50, 100, false, p);
                r.set(ox + s * 0.30f, oy + s * 0.16f, ox + s * 0.94f, oy + s * 0.84f);
                c.drawArc(r, -50, 100, false, p);
            }
        }
    }
}
