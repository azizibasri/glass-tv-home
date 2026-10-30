package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/** Confetti that tumbles and flips in 3D as it falls. Draws nothing, and costs nothing, when idle. */
final class Confetti extends View {
    private static final class Bit {
        float x, y, vx, vy, rot, vr, flip, vflip, size;
        int color;
        int shape; // 0 a paper strip, 1 a dot, 2 a snowflake
    }

    private final ArrayList<Bit> bits = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rnd = new Random();
    private long last;

    Confetti(Context c) {
        super(c);
    }

    /** A shower from the top of the screen. */
    void rain(int n) {
        int w = getWidth();
        for (int i = 0; i < n; i++) {
            add(rnd.nextFloat() * w, -Ui.dp(20) - rnd.nextFloat() * getHeight() * 0.6f,
                    (rnd.nextFloat() - 0.5f) * Ui.dp(160), Ui.dp(60) + rnd.nextFloat() * Ui.dp(180));
        }
        start();
    }

    /** A pop of confetti fanning upwards from one point. */
    void burst(float x, float y, int n) {
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + (rnd.nextFloat() - 0.5f) * 2.2f;
            float speed = Ui.dp(450) + rnd.nextFloat() * Ui.dp(750);
            add(x, y, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed);
        }
        start();
    }

    private void add(float x, float y, float vx, float vy) {
        Bit b = new Bit();
        b.x = x;
        b.y = y;
        b.vx = vx;
        b.vy = vy;
        b.rot = rnd.nextFloat() * 360f;
        b.vr = (rnd.nextFloat() - 0.5f) * 600f;
        b.flip = rnd.nextFloat() * 6f;
        b.vflip = 4f + rnd.nextFloat() * 8f;
        b.size = Ui.dp(7) + rnd.nextFloat() * Ui.dp(8);
        Theme t = Theme.current;
        b.color = t.confetti[rnd.nextInt(t.confetti.length)];
        b.shape = t.snowflakes && rnd.nextBoolean() ? 2 : rnd.nextInt(4) == 0 ? 1 : 0;
        bits.add(b);
    }

    private void start() {
        last = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    /** Three crossed bars with little branches: a six-armed snowflake. */
    private void snowflake(Canvas c, float r) {
        float w = Math.max(1.5f, r * 0.16f);
        for (int i = 0; i < 3; i++) {
            c.drawRect(-r, -w / 2, r, w / 2, paint);
            c.drawRect(r * 0.45f, -r * 0.3f, r * 0.45f + w, r * 0.3f, paint);
            c.drawRect(-r * 0.45f - w, -r * 0.3f, -r * 0.45f, r * 0.3f, paint);
            c.rotate(60);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        if (bits.isEmpty()) return;
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        float gravity = Ui.dp(1300), h = getHeight();
        for (int i = bits.size() - 1; i >= 0; i--) {
            Bit b = bits.get(i);
            b.vy += gravity * dt;
            b.vx *= 1f - 1.2f * dt;
            b.vy = Math.min(b.vy, Ui.dp(420)); // paper flutters instead of dropping like a stone
            b.x += b.vx * dt;
            b.y += b.vy * dt;
            b.rot += b.vr * dt;
            b.flip += b.vflip * dt;
            if (b.y > h + Ui.dp(40)) {
                bits.remove(i);
                continue;
            }
            paint.setColor(b.color);
            c.save();
            c.translate(b.x, b.y);
            c.rotate(b.rot);
            c.scale(1f, (float) Math.cos(b.flip));
            if (b.shape == 1) c.drawCircle(0, 0, b.size * 0.45f, paint);
            else if (b.shape == 2) snowflake(c, b.size * 0.8f);
            else c.drawRect(-b.size / 2, -b.size * 0.3f, b.size / 2, b.size * 0.3f, paint);
            c.restore();
        }
        if (!bits.isEmpty()) postInvalidateOnAnimation();
    }
}
