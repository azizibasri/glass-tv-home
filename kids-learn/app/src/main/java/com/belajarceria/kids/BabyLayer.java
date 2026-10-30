package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * The Kawan Comel babies' playground, over every screen. Now and then a baby swings down on a swing, munches a donut,
 * plays with a bouncing ball, or works up to a big sneeze. On a right answer a baby pops up and sends hearts at it;
 * when a round is won all four bounce up in a row. Draws nothing, and costs nothing, when no baby is out.
 */
final class BabyLayer extends View {
    private static final int SWING = 0, DONUT = 1, TOY = 2, SNEEZE = 3, POP = 4, WAVE = 5;

    private static final class Actor {
        int baby, kind;
        long start, delay;
        float duration, x, tx, ty;
        boolean sneezed;
    }

    private static final class Bit {
        float x, y, vx, vy, life, r, grow;
        int color, shape; // 0 heart, 1 crumb, 2 puff
    }

    private final ArrayList<Actor> actors = new ArrayList<>();
    private final ArrayList<Bit> bits = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private long last;
    private boolean demo;

    BabyLayer(Context c) {
        super(c);
        ink.setColor(Ui.INK);
        ink.setFakeBoldText(true);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    private float babyH() {
        return Math.min(getHeight() * 0.3f, Ui.dp(130));
    }

    boolean busy() {
        return !actors.isEmpty();
    }

    /** One of the babies' games, picked at random: swinging, a donut, a ball, or a sneeze. */
    void play() {
        int kind = rnd.nextInt(4);
        float[] durations = {6f, 5f, 5f, 4.6f};
        Actor a = actor(rnd.nextInt(BabyArt.COUNT), kind, durations[kind]);
        float w = getWidth();
        a.x = kind == SWING ? w * (0.3f + rnd.nextFloat() * 0.4f) : rnd.nextBoolean() ? w * 0.12f : w * 0.86f;
        add(a);
    }

    /** A baby pops up under a right answer and sends hearts at it. */
    void pop(int baby, float tx, float ty) {
        Actor a = actor(baby, POP, 2f);
        a.tx = tx;
        a.ty = ty;
        float h = babyH();
        a.x = Math.max(h * 0.4f, Math.min(getWidth() - h * 0.4f, tx + (tx > getWidth() / 2f ? -h : h)));
        add(a);
        Sfx.pop();
    }

    /** All four babies bounce up along the bottom, one after another: a round is won. */
    void wave() {
        for (int i = 0; i < BabyArt.COUNT; i++) {
            Actor a = actor(i, WAVE, 2.8f);
            a.delay = i * 140L;
            a.x = getWidth() * (0.2f + 0.2f * i);
            add(a);
        }
    }

    /** For screenshots: one baby on the swing, one with a donut, one mid-sneeze, one with the ball. */
    void demo() {
        demo = true;
        invalidate();
    }

    private Actor actor(int baby, int kind, float duration) {
        Actor a = new Actor();
        a.baby = baby;
        a.kind = kind;
        a.duration = duration;
        return a;
    }

    private void add(Actor a) {
        a.start = SystemClock.uptimeMillis();
        actors.add(a);
        last = a.start;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (demo) {
            float w = getWidth();
            float[][] scene = {{SWING, 0.5f, 1.6f}, {DONUT, 0.13f, 2.2f}, {SNEEZE, 0.87f, 2.05f}, {TOY, 0.32f, 1.3f}};
            for (int i = 0; i < scene.length; i++) {
                Actor a = actor(i, (int) scene[i][0], 6f);
                a.x = w * scene[i][1];
                a.sneezed = true;
                drawActor(c, a, scene[i][2]);
            }
            return;
        }
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f);
        last = now;
        for (int i = actors.size() - 1; i >= 0; i--) {
            Actor a = actors.get(i);
            float t = (now - a.start - a.delay) / 1000f;
            if (t < 0) continue;
            if (t > a.duration) {
                actors.remove(i);
                continue;
            }
            drawActor(c, a, t);
        }
        drawBits(c, dt);
        if (!actors.isEmpty() || !bits.isEmpty()) postInvalidateOnAnimation();
    }

    /** How far up from the bottom edge a baby has come: slides up at the start and back down at the end. */
    private float rise(Actor a, float t) {
        float in = Math.min(1f, t / 0.45f), out = Math.min(1f, Math.max(0f, (a.duration - t) / 0.45f));
        float k = Math.min(in, out);
        // a little overshoot, like a spring
        return k >= 1f ? 1f : (float) (1 - Math.pow(1 - k, 3) + Math.sin(k * Math.PI) * 0.12f);
    }

    private void drawActor(Canvas c, Actor a, float t) {
        float h = babyH(), ground = getHeight() + h * 0.08f;
        switch (a.kind) {
            case SWING:
                swing(c, a, t, h);
                break;
            case DONUT: {
                float bottom = ground + (1 - rise(a, t)) * h * 1.2f;
                float munch = (t % 0.8f) < 0.2f ? 1.04f : 1f;
                BabyArt.draw(c, getContext(), a.baby, a.x, bottom, h, (float) Math.sin(t * 2) * 3, munch);
                int bites = t < 1.2f ? 0 : t < 2.0f ? 1 : t < 2.8f ? 2 : 3;
                for (float bt : new float[]{1.2f, 2.0f, 2.8f}) {
                    if (Math.abs(t - bt) < 0.02f) crumbs(a.x, bottom - h * 0.48f);
                }
                donut(c, a.x + h * 0.02f, bottom - h * 0.42f, h * 0.17f, bites, t);
                if (t > 1.1f && t < 3.6f) BabyArt.bubble(c, "Nyam nyam!", a.x, bottom - h * 1.02f, Ui.dp(15), paint, ink);
                break;
            }
            case TOY: {
                float bottom = ground + (1 - rise(a, t)) * h * 1.2f;
                float side = a.x < getWidth() / 2f ? 1 : -1;
                float bx = a.x + side * h * 0.62f, r = h * 0.15f;
                float bounce = Math.abs((float) Math.sin(t * 3.4f));
                float by = bottom - h * 0.12f - r - bounce * h * 0.55f;
                BabyArt.draw(c, getContext(), a.baby, a.x, bottom, h, side * (bounce - 0.5f) * 8, 1f + 0.04f * (1 - bounce));
                ball(c, bx, by, r, bounce < 0.08f ? 1.18f : 1f, t);
                if (t > 1.2f && t < 3.8f) BabyArt.bubble(c, "Hehe!", a.x, bottom - h * 1.02f, Ui.dp(15), paint, ink);
                break;
            }
            case SNEEZE:
                sneeze(c, a, t, h, ground);
                break;
            case POP: {
                float bottom = ground + (1 - rise(a, t)) * h * 1.2f;
                float clap = 1f + 0.06f * (float) Math.sin(t * 18f);
                BabyArt.draw(c, getContext(), a.baby, a.x, bottom, h, 0, clap);
                if (t > 0.3f && t < 1.3f && rnd.nextInt(3) == 0) heartTowards(a.x, bottom - h * 0.6f, a.tx, a.ty);
                if (t > 0.3f && t < 1.5f) BabyArt.bubble(c, "Yay!", a.x, bottom - h * 1.02f, Ui.dp(16), paint, ink);
                break;
            }
            default: { // WAVE
                float bottom = ground + (1 - rise(a, t)) * h * 1.2f;
                float hop = Math.abs((float) Math.sin(t * 5f + a.baby)) * h * 0.18f;
                BabyArt.draw(c, getContext(), a.baby, a.x, bottom - hop, h, (float) Math.sin(t * 5f + a.baby) * 6, 1f);
                if (rnd.nextInt(6) == 0) heartUp(a.x, bottom - h * 0.8f);
                break;
            }
        }
    }

    /** A swing drops from the top on two ropes; the baby on it swings back and forth, then goes back up. */
    private void swing(Canvas c, Actor a, float t, float h) {
        float len = h * 1.5f;
        float drop = Math.min(1f, t / 0.9f), lift = Math.min(1f, Math.max(0f, (a.duration - t) / 0.9f));
        float pivotY = -len * (1 - Math.min(drop, lift)) - Ui.dp(6);
        float env = Math.min(1f, t / 1.5f) * Math.min(1f, (a.duration - t) / 1.2f);
        float angle = 30f * (float) Math.sin(t * 2.4f) * Math.max(0f, env);
        c.save();
        c.rotate(angle, a.x, pivotY);
        float seatY = pivotY + len, seatW = h * 0.7f;
        line.setColor(0xFF9C6B45);
        line.setStrokeWidth(Ui.dp(3));
        c.drawLine(a.x - seatW * 0.42f, pivotY, a.x - seatW * 0.42f, seatY, line);
        c.drawLine(a.x + seatW * 0.42f, pivotY, a.x + seatW * 0.42f, seatY, line);
        BabyArt.draw(c, getContext(), a.baby, a.x, seatY + h * 0.02f, h * 0.9f, 0, 1f);
        paint.setColor(0xFFFF8FB8);
        rect.set(a.x - seatW / 2, seatY, a.x + seatW / 2, seatY + Ui.dp(9));
        c.drawRoundRect(rect, Ui.dp(4), Ui.dp(4), paint);
        c.restore();
        if (Math.abs(angle) > 24f && t > 1.5f) {
            BabyArt.bubble(c, "Wiii!", a.x + (angle > 0 ? -1 : 1) * h * 0.55f, seatY - h * 0.6f, Ui.dp(15), paint, ink);
        }
    }

    /** "Ah... ah..." (leaning back, swelling), "ACHOO!" (a jolt and a puff), then a giggle. */
    private void sneeze(Canvas c, Actor a, float t, float h, float ground) {
        float bottom = ground + (1 - rise(a, t)) * h * 1.2f;
        float tilt = 0f, squash = 1f;
        String say = null;
        if (t > 0.5f && t < 1.9f) {
            float k = (t - 0.5f) / 1.4f;
            tilt = -7f * k;
            squash = 1f - 0.06f * k;
            say = k < 0.5f ? "Ah..." : "Ah... ah...";
        } else if (t >= 1.9f && t < 2.9f) {
            float k = t - 1.9f;
            tilt = 13f * (float) Math.exp(-k * 4) * (float) Math.cos(k * 18);
            squash = 1f + 0.08f * (float) Math.exp(-k * 5);
            say = "ACHOO!";
            if (!a.sneezed) {
                a.sneezed = true;
                puff(a.x, bottom - h * 0.68f, a.x < getWidth() / 2f ? 1 : -1);
                Sfx.achoo();
            }
        } else if (t >= 2.9f && t < 3.9f) {
            squash = 1f + 0.05f * (float) Math.sin(t * 20);
            say = "Hehe!";
        }
        BabyArt.draw(c, getContext(), a.baby, a.x, bottom, h, tilt, squash);
        if (say != null) BabyArt.bubble(c, say, a.x, bottom - h * 1.02f, Ui.dp(say.startsWith("A") ? 17 : 15), paint, ink);
    }

    /** A pink-iced donut with sprinkles; each bite takes a round mouthful out of its edge. */
    private void donut(Canvas c, float x, float y, float r, int bites, float t) {
        path.reset();
        path.addCircle(x, y, r, Path.Direction.CW);
        Path hole = new Path();
        hole.addCircle(x, y, r * 0.36f, Path.Direction.CW);
        path.op(hole, Path.Op.DIFFERENCE);
        for (int i = 0; i < bites; i++) {
            Path bite = new Path();
            double ang = Math.toRadians(-40 + i * 34);
            bite.addCircle(x + (float) Math.cos(ang) * r * 1.05f, y + (float) Math.sin(ang) * r * 1.05f, r * 0.42f, Path.Direction.CW);
            path.op(bite, Path.Op.DIFFERENCE);
        }
        paint.setColor(0xFFE0A15A);
        c.drawPath(path, paint);
        c.save();
        c.clipPath(path);
        paint.setColor(0xFFFF8FB8);
        c.drawCircle(x, y - r * 0.08f, r * 0.9f, paint);
        int[] sprinkle = {0xFFFFFFFF, 0xFF7FD3FF, 0xFFFFD166, 0xFF8BE3B5};
        for (int i = 0; i < 10; i++) {
            double ang = i * 0.63;
            float d = r * (0.55f + 0.25f * (i % 2));
            paint.setColor(sprinkle[i % sprinkle.length]);
            c.drawCircle(x + (float) Math.cos(ang) * d, y + (float) Math.sin(ang) * d, r * 0.07f, paint);
        }
        c.restore();
    }

    /** A beach ball in three colours, squashing when it touches down. */
    private void ball(Canvas c, float x, float y, float r, float squash, float t) {
        c.save();
        c.translate(x, y);
        c.scale(squash, 1f / squash);
        c.rotate(t * 160f);
        rect.set(-r, -r, r, r);
        int[] colors = {0xFFFF6F91, 0xFF4F9BFF, 0xFFFFD166};
        for (int i = 0; i < 3; i++) {
            paint.setColor(colors[i]);
            c.drawArc(rect, i * 120f, 120f, true, paint);
        }
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(0, 0, r * 0.22f, paint);
        c.restore();
    }

    private void heartTowards(float x, float y, float tx, float ty) {
        Bit b = bit(x, y, 0);
        float d = (float) Math.hypot(tx - x, ty - y) + 1;
        float speed = Ui.dp(9);
        b.vx = (tx - x) / d * speed + (rnd.nextFloat() - 0.5f) * Ui.dp(2);
        b.vy = (ty - y) / d * speed + (rnd.nextFloat() - 0.5f) * Ui.dp(2);
        b.color = rnd.nextBoolean() ? 0xFFFF6F91 : 0xFFFF9BD2;
        b.r = Ui.dp(7) + rnd.nextFloat() * Ui.dp(4);
    }

    private void heartUp(float x, float y) {
        Bit b = bit(x + (rnd.nextFloat() - 0.5f) * Ui.dp(40), y, 0);
        b.vy = -Ui.dp(3) - rnd.nextFloat() * Ui.dp(2);
        b.vx = (rnd.nextFloat() - 0.5f) * Ui.dp(2);
        b.color = new int[]{0xFFFF6F91, 0xFFFF9BD2, 0xFFB57BFF, 0xFF7FD3FF}[rnd.nextInt(4)];
        b.r = Ui.dp(6) + rnd.nextFloat() * Ui.dp(5);
    }

    private void crumbs(float x, float y) {
        for (int i = 0; i < 6; i++) {
            Bit b = bit(x, y, 1);
            b.vx = (rnd.nextFloat() - 0.5f) * Ui.dp(5);
            b.vy = -rnd.nextFloat() * Ui.dp(3);
            b.color = rnd.nextBoolean() ? 0xFFC98A4B : 0xFFFF8FB8;
            b.r = Ui.dp(2) + rnd.nextFloat() * Ui.dp(2);
        }
    }

    private void puff(float x, float y, int side) {
        for (int i = 0; i < 14; i++) {
            Bit b = bit(x + side * Ui.dp(10), y, 2);
            b.vx = side * (Ui.dp(3) + rnd.nextFloat() * Ui.dp(5));
            b.vy = (rnd.nextFloat() - 0.6f) * Ui.dp(3);
            b.color = i % 3 == 0 ? 0xFFE6F3FF : 0xFFFFFFFF;
            b.r = Ui.dp(5) + rnd.nextFloat() * Ui.dp(6);
            b.grow = Ui.dp(0.6f);
        }
    }

    private Bit bit(float x, float y, int shape) {
        Bit b = new Bit();
        b.x = x;
        b.y = y;
        b.life = 1f;
        b.shape = shape;
        bits.add(b);
        return b;
    }

    private void drawBits(Canvas c, float dt) {
        float step = dt * 60f;
        for (int i = bits.size() - 1; i >= 0; i--) {
            Bit b = bits.get(i);
            b.life -= (b.shape == 2 ? 0.018f : 0.022f) * step;
            if (b.life <= 0) {
                bits.remove(i);
                continue;
            }
            b.x += b.vx * step;
            b.y += b.vy * step;
            if (b.shape == 1) b.vy += 0.35f * step;
            if (b.shape == 2) {
                b.vx *= 0.94f;
                b.vy *= 0.94f;
                b.r += b.grow * step;
            }
            int alpha = (int) (255 * Math.min(1f, b.life * 1.5f));
            paint.setColor((b.color & 0x00FFFFFF) | (alpha << 24));
            if (b.shape == 0) {
                heart(c, b.x, b.y, b.r);
            } else {
                c.drawCircle(b.x, b.y, b.r, paint);
                if (b.shape == 2) {
                    line.setStrokeWidth(Ui.dp(1.2f));
                    line.setColor((0xCFE2F7 & 0x00FFFFFF) | ((alpha / 2) << 24));
                    c.drawCircle(b.x, b.y, b.r, line);
                }
            }
        }
    }

    private void heart(Canvas c, float x, float y, float r) {
        path.reset();
        path.moveTo(x, y + r);
        path.cubicTo(x - r * 1.6f, y - r * 0.1f, x - r * 0.7f, y - r * 1.3f, x, y - r * 0.45f);
        path.cubicTo(x + r * 0.7f, y - r * 1.3f, x + r * 1.6f, y - r * 0.1f, x, y + r);
        path.close();
        c.drawPath(path, paint);
    }
}
