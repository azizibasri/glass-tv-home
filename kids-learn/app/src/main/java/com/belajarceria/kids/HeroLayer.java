package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * The Hero Squad's stage, over every screen: heroes run along the bottom, fly across the sky, and on a right answer
 * one flies in and zaps it with a sparkly star-laser. Draws nothing, and costs nothing, when no hero is out.
 * Touches pass straight through.
 */
final class HeroLayer extends View {
    private static final int RUN_ACROSS = 0, FLY_ACROSS = 1, ZAP = 2;

    private static final class Actor {
        HeroArt.Hero hero;
        int kind;
        long start, delay;
        float duration, size, y, tx, ty;
        boolean right; // travelling to the right
    }

    private static final class Spark {
        float x, y, vx, vy, life, r;
        int color;
    }

    private final ArrayList<Actor> actors = new ArrayList<>();
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final Random rnd = new Random();
    private final Paint beam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path star = new Path();

    HeroLayer(Context c) {
        super(c);
        beam.setStrokeCap(Paint.Cap.ROUND);
    }

    private float heroSize() {
        return Math.min(getHeight() * 0.3f, Ui.dp(130));
    }

    /** A hero sprints along the bottom of the screen. */
    void runAcross(HeroArt.Hero h) {
        Actor a = actor(h, RUN_ACROSS, 3.4f);
        a.size = heroSize() * 0.85f;
        a.y = getHeight() - a.size * 1.08f;
        add(a);
    }

    /** A hero zooms across the sky. */
    void flyAcross(HeroArt.Hero h, long delay, float yFraction) {
        Actor a = actor(h, FLY_ACROSS, 2.6f);
        a.delay = delay;
        a.size = heroSize() * 0.8f;
        a.y = getHeight() * yFraction;
        add(a);
    }

    /** The whole squad flies across together, one after another: the end of a round. */
    void squad() {
        boolean right = rnd.nextBoolean();
        for (int i = 0; i < HeroArt.SQUAD.length; i++) {
            Actor a = actor(HeroArt.SQUAD[i], FLY_ACROSS, 2.8f);
            a.right = right;
            a.delay = i * 180L;
            a.size = heroSize() * 0.75f;
            a.y = getHeight() * (0.08f + 0.17f * i);
            add(a);
        }
    }

    /** A hero flies in beside the target, fires a star-laser at it with a burst of sparkles, and flies off. */
    void zap(HeroArt.Hero h, float tx, float ty) {
        Actor a = actor(h, ZAP, 2.1f);
        a.tx = tx;
        a.ty = ty;
        a.size = heroSize() * 0.8f;
        // Come from the side with more room, facing the target.
        a.right = tx > getWidth() / 2f;
        add(a);
        Sfx.zap();
    }

    private Actor actor(HeroArt.Hero h, int kind, float duration) {
        Actor a = new Actor();
        a.hero = h;
        a.kind = kind;
        a.duration = duration;
        a.right = rnd.nextBoolean();
        return a;
    }

    private void add(Actor a) {
        a.start = SystemClock.uptimeMillis();
        actors.add(a);
        postInvalidateOnAnimation();
    }

    private boolean demo;

    /** For screenshots: freezes a running, a flying and a zapping hero in place. */
    void demo() {
        demo = true;
        invalidate();
    }

    boolean busy() {
        return !actors.isEmpty();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (demo) {
            float w = getWidth(), h = getHeight();
            Actor run = actor(HeroArt.SQUAD[0], RUN_ACROSS, 1f);
            run.size = heroSize() * 0.85f;
            run.y = h - run.size * 1.08f;
            run.right = true;
            drawActor(c, run, 0.3f);
            Actor fly = actor(HeroArt.SQUAD[3], FLY_ACROSS, 1f);
            fly.size = heroSize() * 0.8f;
            fly.y = h * 0.08f;
            fly.right = true;
            drawActor(c, fly, 0.62f);
            Actor zap = actor(HeroArt.SQUAD[1], ZAP, 1f);
            zap.size = heroSize() * 0.8f;
            zap.tx = w * 0.62f;
            zap.ty = h * 0.55f;
            zap.right = true;
            drawActor(c, zap, 0.5f);
            return;
        }
        long now = SystemClock.uptimeMillis();
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
        drawSparks(c);
        if (!actors.isEmpty() || !sparks.isEmpty()) postInvalidateOnAnimation();
    }

    private void drawActor(Canvas c, Actor a, float t) {
        float w = getWidth(), s = a.size / 230f, boxW = 200 * s, p = t / a.duration;
        float x, y = a.y;
        int pose;
        switch (a.kind) {
            case RUN_ACROSS:
                pose = HeroArt.RUN;
                x = lerp(-boxW, w, p);
                break;
            case FLY_ACROSS:
                pose = HeroArt.FLY;
                x = lerp(-boxW * 1.2f, w + boxW * 0.2f, p);
                y += (float) Math.sin(p * Math.PI * 2) * Ui.dp(14);
                break;
            default: { // ZAP: fly in (0-0.25), fire (0.25-0.65), fly away up (0.65-1)
                // Hover beside the target, at its height, a little way off, never off screen.
                float stationX = a.right ? clamp(a.tx - boxW * 2.2f, Ui.dp(8), w - boxW)
                        : clamp(a.tx + boxW * 1.2f, 0, w - boxW - Ui.dp(8));
                float stationY = clamp(a.ty - a.size * 0.55f, Ui.dp(4), getHeight() - a.size);
                if (p < 0.25f) {
                    pose = HeroArt.FLY;
                    float k = ease(p / 0.25f);
                    x = lerp(a.right ? -boxW : w, stationX, k);
                    y = lerp(stationY - a.size, stationY, k);
                } else if (p < 0.65f) {
                    pose = HeroArt.LASER;
                    x = stationX;
                    y = stationY;
                    float k = (p - 0.25f) / 0.4f;
                    float hx = a.right ? x + HeroArt.HAND_X * s : x + (200 - HeroArt.HAND_X) * s;
                    float hy = y + HeroArt.HAND_Y * s;
                    laser(c, a.hero, hx, hy, a.tx, a.ty, k);
                    if (rnd.nextInt(2) == 0) burst(a.tx, a.ty, 2, a.hero);
                } else {
                    pose = HeroArt.FLY;
                    float k = ease((p - 0.65f) / 0.35f);
                    x = stationX + (a.right ? 1 : -1) * k * boxW * 0.5f;
                    y = lerp(stationY, -a.size * 1.3f, k);
                }
                break;
            }
        }
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        if (!a.right) c.scale(-1, 1, 100, 115);
        HeroArt.draw(c, a.hero, pose, t);
        c.restore();
    }

    /** A glowing beam in the hero's colours, growing towards the target, with little stars along it. */
    private void laser(Canvas c, HeroArt.Hero h, float x1, float y1, float x2, float y2, float k) {
        float grow = Math.min(1f, k * 3f);
        float ex = lerp(x1, x2, grow), ey = lerp(y1, y2, grow);
        float pulse = 1f + 0.25f * (float) Math.sin(k * 40f);
        beam.setShader(null);
        beam.setColor((h.suit2 & 0x00FFFFFF) | 0x55000000);
        beam.setStrokeWidth(Ui.dp(16) * pulse);
        c.drawLine(x1, y1, ex, ey, beam);
        beam.setShader(new LinearGradient(x1, y1, ex, ey, h.trim, h.suit2, Shader.TileMode.CLAMP));
        beam.setStrokeWidth(Ui.dp(8) * pulse);
        c.drawLine(x1, y1, ex, ey, beam);
        beam.setShader(null);
        beam.setColor(0xF2FFFFFF);
        beam.setStrokeWidth(Ui.dp(3));
        c.drawLine(x1, y1, ex, ey, beam);
        paint.setColor(0xFFFFFFFF);
        for (int i = 1; i <= 3; i++) {
            float f = ((k * 2.5f + i / 3f) % 1f) * grow;
            drawStar(c, lerp(x1, x2, f), lerp(y1, y2, f), Ui.dp(5), 0xFFFFF6B0);
        }
        if (grow >= 1f) drawStar(c, x2, y2, Ui.dp(16) * pulse, 0xFFFFF176);
    }

    private void burst(float x, float y, int n, HeroArt.Hero h) {
        int[] colors = {h.trim, h.suit, h.suit2, 0xFFFFFFFF};
        for (int i = 0; i < n; i++) {
            Spark s = new Spark();
            double a = rnd.nextDouble() * Math.PI * 2;
            float v = Ui.dp(2) + rnd.nextFloat() * Ui.dp(5);
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(a) * v;
            s.vy = (float) Math.sin(a) * v;
            s.life = 1f;
            s.r = Ui.dp(4) + rnd.nextFloat() * Ui.dp(5);
            s.color = colors[rnd.nextInt(colors.length)];
            sparks.add(s);
        }
    }

    private void drawSparks(Canvas c) {
        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark s = sparks.get(i);
            s.life -= 0.03f;
            if (s.life <= 0) {
                sparks.remove(i);
                continue;
            }
            s.x += s.vx;
            s.y += s.vy;
            s.vy += 0.08f;
            drawStar(c, s.x, s.y, s.r * s.life, (s.color & 0x00FFFFFF) | ((int) (255 * s.life) << 24));
        }
    }

    private void drawStar(Canvas c, float x, float y, float r, int color) {
        star.reset();
        HeroArt.star(star, x, y, r, 0.45f);
        paint.setColor(color);
        c.drawPath(star, paint);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float ease(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
