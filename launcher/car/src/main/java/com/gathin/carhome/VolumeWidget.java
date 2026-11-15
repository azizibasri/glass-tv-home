package com.gathin.carhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Volume: a big minus and plus with the media volume between them as a bar and a number. Tap to change it
 * by one step, hold to keep changing it. Follows the volume when it changes elsewhere (the steering wheel,
 * the unit's own keys).
 */
final class VolumeWidget extends LinearLayout {
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Level level;
    private final TextView number;
    private BroadcastReceiver receiver;

    VolumeWidget(Context ctx) {
        super(ctx);
        float d = getResources().getDisplayMetrics().density;
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        // Two rows: minus, the number, plus; and the volume bar under them across the whole width.
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        int pad = Math.round(12 * d);
        setPadding(pad, pad, pad, pad);
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int btn = Math.round(52 * d);
        row.addView(button(ctx, "\u2212", -1), new LayoutParams(btn, btn));
        number = new TextView(ctx);
        number.setTextColor(0xFFFFFFFF);
        number.setTextSize(22);
        number.setGravity(Gravity.CENTER);
        number.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        number.setFontFeatureSettings("tnum");
        row.addView(number, new LayoutParams(0, -2, 1f));
        row.addView(button(ctx, "+", 1), new LayoutParams(btn, btn));
        addView(row, new LayoutParams(-1, -2));
        level = new Level(ctx);
        LayoutParams llp = new LayoutParams(-1, Math.round(8 * d));
        llp.topMargin = Math.round(12 * d);
        addView(level, llp);
        show();
    }

    private View button(Context ctx, String sign, int dir) {
        TextView b = new TextView(ctx);
        b.setText(sign);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(30);
        b.setGravity(Gravity.CENTER);
        b.setIncludeFontPadding(false);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(0x26FFFFFF);
        g.setStroke(Math.max(1, Math.round(getResources().getDisplayMetrics().density)), 0x4DFFFFFF);
        b.setBackground(g);
        b.setContentDescription(dir > 0 ? "Volume up" : "Volume down");
        // Tap: one step. Hold: a step every 150 ms after a short wait.
        Runnable repeat = new Runnable() {
            @Override
            public void run() {
                step(dir);
                main.postDelayed(this, 150);
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

    private void step(int dir) {
        if (audio == null) return;
        try {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    dir > 0 ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
        } catch (SecurityException ignored) {
            // Do Not Disturb can refuse it; the bar shows what it is.
        }
        show();
    }

    private void show() {
        if (audio == null) return;
        int max = Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        number.setText(String.valueOf(v));
        level.set(v / (float) max);
    }

    /** Follows volume changes made elsewhere while Home is on screen. */
    void start() {
        show();
        if (receiver != null) return;
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                show();
            }
        };
        getContext().registerReceiver(receiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    void stop() {
        main.removeCallbacksAndMessages(null);
        if (receiver != null) {
            try {
                getContext().unregisterReceiver(receiver);
            } catch (RuntimeException ignored) {
            }
            receiver = null;
        }
    }

    /** The volume as a rounded bar. */
    private static final class Level extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float frac;

        Level(Context ctx) {
            super(ctx);
            track.setColor(0x33FFFFFF);
            fill.setColor(0xFFFFFFFF);
        }

        void set(float f) {
            frac = Math.max(0f, Math.min(1f, f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float rad = getHeight() / 2f;
            r.set(0, 0, getWidth(), getHeight());
            c.drawRoundRect(r, rad, rad, track);
            if (frac > 0) {
                r.set(0, 0, Math.max(getHeight(), getWidth() * frac), getHeight());
                c.drawRoundRect(r, rad, rad, fill);
            }
        }
    }
}
