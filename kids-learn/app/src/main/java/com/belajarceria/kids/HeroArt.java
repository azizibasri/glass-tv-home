package com.belajarceria.kids;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * The Hero Squad: four original kid heroes, drawn in code in a 200 x 230 box, in four poses that animate with time.
 * Kapten Ceria (star), Puteri Bintang (heart), Wira Khaizuran (K, bamboo staff), Si Kilat (lightning, goggles).
 */
final class HeroArt {
    static final int STAND = 0, RUN = 1, FLY = 2, LASER = 3;
    static final int HAIR_SPIKY = 0, HAIR_CURLY = 1, HAIR_PONY = 2, HAIR_BUNS = 3;
    static final int EYES_MASK = 0, EYES_STARMASK = 1, EYES_GOGGLES = 2;
    static final int EMB_STAR = 0, EMB_HEART = 1, EMB_K = 2, EMB_BOLT = 3;
    /** Where the pointing hand is in the LASER pose, in the 200 x 230 box (facing right). */
    static final float HAND_X = 165f, HAND_Y = 126f;

    static final class Hero {
        final String name;
        final int skin, hair, suit, suit2, trim, badge, cape, boots, mask;
        final int hairStyle, eyes, emblem;
        final boolean hasCape, scarf, skirt, staff;

        Hero(String name, int[] c, int hairStyle, int eyes, int emblem, boolean hasCape, boolean scarf, boolean skirt,
             boolean staff) {
            this.name = name;
            skin = c[0];
            hair = c[1];
            suit = c[2];
            suit2 = c[3];
            trim = c[4];
            badge = c[5];
            cape = c[6];
            boots = c[7];
            mask = c[8];
            this.hairStyle = hairStyle;
            this.eyes = eyes;
            this.emblem = emblem;
            this.hasCape = hasCape;
            this.scarf = scarf;
            this.skirt = skirt;
            this.staff = staff;
        }
    }

    // colours: skin, hair, suit, suit2, trim, badge, cape, boots, mask
    static final Hero KAPTEN_CERIA = new Hero("Kapten Ceria",
            new int[]{0xFFF6C9A0, 0xFF5A3A22, 0xFF11A3A3, 0xFFFF8A1C, 0xFFFFC727, 0xFFFFFFFF, 0xFFFF7A2E, 0xFFFF8A1C, 0xFFFF8A1C},
            HAIR_SPIKY, EYES_STARMASK, EMB_STAR, true, false, false, false);
    static final Hero PUTERI_BINTANG = new Hero("Puteri Bintang",
            new int[]{0xFFF3C39C, 0xFF3B2418, 0xFF8A4FFF, 0xFFFF6FB5, 0xFFFFD447, 0xFFFFFFFF, 0xFFFF6FB5, 0xFFFFD447, 0xFFFF6FB5},
            HAIR_PONY, EYES_MASK, EMB_HEART, true, false, true, false);
    static final Hero WIRA_KHAIZURAN = new Hero("Wira Khaizuran",
            new int[]{0xFFD9A57B, 0xFF2E1D14, 0xFF1F9D55, 0xFFFFC72C, 0xFFFFC72C, 0xFFFFFFFF, 0xFFFFC72C, 0xFF14713B, 0xFF14713B},
            HAIR_CURLY, EYES_MASK, EMB_K, true, false, false, true);
    static final Hero SI_KILAT = new Hero("Si Kilat",
            new int[]{0xFFF5CFA8, 0xFF1F1A3A, 0xFFFF7A1A, 0xFF2A3D8F, 0xFF2A3D8F, 0xFFFFF4C2, 0xFF2A3D8F, 0xFF2A3D8F, 0xFF2A3D8F},
            HAIR_BUNS, EYES_GOGGLES, EMB_BOLT, false, true, false, false);

    static final Hero[] SQUAD = {KAPTEN_CERIA, PUTERI_BINTANG, WIRA_KHAIZURAN, SI_KILAT};

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint S = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    static {
        S.setStyle(Paint.Style.STROKE);
        S.setStrokeCap(Paint.Cap.ROUND);
        P.setTextAlign(Paint.Align.CENTER);
        P.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
    }

    private HeroArt() {}

    /**
     * Draws the hero into the 200 x 230 box at the canvas origin (scale the canvas first).
     * @param t seconds, for the animation of the pose
     */
    static void draw(Canvas c, Hero h, int pose, float t) {
        c.save();
        float bob, lean = 0f;
        float armL, armR, legL, legR, flutter;
        switch (pose) {
            case RUN: {
                float s = (float) Math.sin(t * 11f);
                bob = -Math.abs(s) * 5f;
                lean = 9f;
                armL = 35f + 40f * s;
                armR = 35f - 40f * s;
                legL = 28f * s;
                legR = -28f * s;
                flutter = 10f;
                break;
            }
            case FLY:
                bob = (float) Math.sin(t * 4f) * 3f;
                armL = 25f;
                armR = 168f;
                legL = 6f;
                legR = -4f;
                flutter = 16f;
                c.rotate(78f, 100f, 140f); // head first, to the right
                break;
            case LASER:
                bob = (float) Math.sin(t * 6f) * 1.5f;
                armL = 140f;
                armR = 90f;
                legL = -8f;
                legR = 8f;
                flutter = 4f;
                break;
            default: // STAND: arms up, waving a little
                bob = (float) Math.sin(t * 3f) * 3f;
                armL = 135f + (float) Math.sin(t * 5f) * 8f;
                armR = 135f - (float) Math.sin(t * 5f + 1f) * 8f;
                legL = 0f;
                legR = 0f;
                flutter = 3f;
                break;
        }
        c.translate(0, bob);
        if (lean != 0f) c.rotate(lean, 100f, 200f);
        float wave = (float) Math.sin(t * 9f) * flutter;

        if (h.hasCape) cape(c, h, wave);
        leg(c, h, 89.5f, legL);
        leg(c, h, 110.5f, legR);
        arm(c, h, 80f, 128f, -1, armL);
        arm(c, h, 120f, 128f, 1, armR);
        if (h.staff) staff(c, h, 120f, 128f, armR);
        body(c, h);
        if (h.scarf) scarf(c, h, wave);
        head(c, h, t, pose);
        c.restore();
    }

    /** Where the right hand is, in the box, for a given pose (used to aim the laser). */
    static float[] hand() {
        return new float[]{HAND_X, HAND_Y};
    }

    private static void cape(Canvas c, Hero h, float wave) {
        PATH.reset();
        PATH.moveTo(74, 122);
        PATH.cubicTo(62, 150, 50, 186, 44 - wave * 0.5f, 214 + wave * 0.3f);
        PATH.cubicTo(62, 206 - wave, 78, 214 + wave, 100, 206);
        PATH.cubicTo(122, 214 - wave, 138, 206 + wave, 156 + wave * 0.5f, 214 - wave * 0.3f);
        PATH.cubicTo(150, 186, 138, 150, 126, 122);
        PATH.close();
        fill(c, h.cape, PATH);
    }

    private static void leg(Canvas c, Hero h, float x, float angle) {
        c.save();
        c.rotate(angle, x, 176);
        P.setColor(h.suit);
        R.set(x - 7.5f, 172, x + 7.5f, 206);
        c.drawRoundRect(R, 7, 7, P);
        P.setColor(h.boots);
        R.set(x - 11.5f, 198, x + 9.5f, 214);
        c.drawRoundRect(R, 7, 7, P);
        c.restore();
    }

    /** An arm from the shoulder; angle 0 points straight down, 90 straight out to the side, 180 straight up. */
    private static void arm(Canvas c, Hero h, float sx, float sy, int side, float angle) {
        double a = Math.toRadians(angle);
        float hx = sx + side * (float) Math.sin(a) * 42f, hy = sy + (float) Math.cos(a) * 42f;
        S.setColor(h.suit);
        S.setStrokeWidth(14);
        c.drawLine(sx, sy, hx, hy, S);
        P.setColor(h.boots);
        c.drawCircle(hx, hy, 9, P);
    }

    private static void staff(Canvas c, Hero h, float sx, float sy, float angle) {
        double a = Math.toRadians(angle);
        float hx = sx + (float) Math.sin(a) * 42f, hy = sy + (float) Math.cos(a) * 42f;
        c.save();
        c.rotate(-28f, hx, hy);
        P.setColor(0xFF7CB342);
        R.set(hx - 5, hy - 64, hx + 5, hy + 62);
        c.drawRoundRect(R, 5, 5, P);
        P.setColor(0xFF558B2F);
        for (int i = -2; i <= 2; i++) {
            R.set(hx - 6, hy + i * 28 - 2, hx + 6, hy + i * 28 + 2);
            c.drawRoundRect(R, 2, 2, P);
        }
        c.restore();
    }

    private static void body(Canvas c, Hero h) {
        P.setColor(h.suit);
        R.set(72, 118, 128, 180);
        c.drawRoundRect(R, 20, 20, P);
        PATH.reset();
        PATH.moveTo(76, 124);
        PATH.quadTo(100, 136, 124, 124);
        PATH.lineTo(124, 132);
        PATH.quadTo(100, 144, 76, 132);
        PATH.close();
        fill(c, (h.suit2 & 0x00FFFFFF) | 0xE6000000, PATH);
        if (h.skirt) {
            PATH.reset();
            PATH.moveTo(74, 168);
            PATH.lineTo(126, 168);
            PATH.lineTo(134, 186);
            PATH.lineTo(66, 186);
            PATH.close();
            fill(c, h.suit2, PATH);
        }
        P.setColor(h.trim);
        R.set(73, 164, 127, 172);
        c.drawRoundRect(R, 4, 4, P);
        emblem(c, h);
    }

    private static void emblem(Canvas c, Hero h) {
        P.setColor(h.badge);
        c.drawCircle(100, 146, 14, P);
        S.setColor(h.trim);
        S.setStrokeWidth(3);
        c.drawCircle(100, 146, 14, S);
        PATH.reset();
        switch (h.emblem) {
            case EMB_STAR:
                star(PATH, 100, 146.5f, 9.5f, 0.45f);
                fill(c, h.trim, PATH);
                break;
            case EMB_HEART:
                PATH.moveTo(100, 155);
                PATH.cubicTo(90, 148, 88, 141, 92, 138);
                PATH.cubicTo(95, 135.5f, 99, 137, 100, 140);
                PATH.cubicTo(101, 137, 105, 135.5f, 108, 138);
                PATH.cubicTo(112, 141, 110, 148, 100, 155);
                PATH.close();
                fill(c, h.trim, PATH);
                break;
            case EMB_K:
                P.setColor(h.trim);
                P.setTextSize(17);
                c.drawText("K", 100, 152.5f, P);
                break;
            default:
                PATH.moveTo(103, 134);
                PATH.lineTo(93, 148);
                PATH.lineTo(100, 148);
                PATH.lineTo(96, 159);
                PATH.lineTo(108, 143);
                PATH.lineTo(101, 143);
                PATH.close();
                fill(c, h.trim, PATH);
                break;
        }
    }

    private static void scarf(Canvas c, Hero h, float wave) {
        PATH.reset();
        PATH.moveTo(82, 116);
        PATH.quadTo(100, 126, 118, 116);
        PATH.lineTo(120, 124);
        PATH.quadTo(100, 134, 80, 124);
        PATH.close();
        fill(c, h.cape, PATH);
        PATH.reset();
        PATH.moveTo(116, 120);
        PATH.cubicTo(134, 118 + wave * 0.3f, 150, 108 - wave, 166, 112 + wave);
        PATH.cubicTo(152, 118 + wave, 146, 128, 128, 130);
        PATH.close();
        fill(c, h.cape, PATH);
    }

    private static void head(Canvas c, Hero h, float t, int pose) {
        P.setColor(h.skin);
        c.drawCircle(62, 88, 7, P);
        c.drawCircle(138, 88, 7, P);
        c.drawCircle(100, 84, 38, P);
        hair(c, h);
        eyes(c, h, t);
        P.setColor(0x73FF8A8A);
        c.drawCircle(76, 102, 5.5f, P);
        c.drawCircle(124, 102, 5.5f, P);
        PATH.reset();
        if (pose == LASER) {
            // a determined little "o"
            P.setColor(0xFF7A2E2E);
            c.drawOval(new RectF(95, 103, 105, 112), P);
        } else {
            PATH.moveTo(88, 104);
            PATH.quadTo(100, 117, 112, 104);
            PATH.quadTo(100, 110, 88, 104);
            PATH.close();
            fill(c, 0xFF7A2E2E, PATH);
        }
    }

    private static void hair(Canvas c, Hero h) {
        PATH.reset();
        switch (h.hairStyle) {
            case HAIR_SPIKY:
                PATH.moveTo(62, 80);
                PATH.cubicTo(60, 52, 78, 38, 100, 38);
                PATH.cubicTo(124, 38, 141, 52, 138, 80);
                float[] spikes = {131, 66, 126, 76, 118, 60, 111, 73, 102, 56, 95, 72, 86, 58, 80, 74, 72, 62, 68, 78};
                for (int i = 0; i < spikes.length; i += 2) PATH.lineTo(spikes[i], spikes[i + 1]);
                PATH.close();
                fill(c, h.hair, PATH);
                break;
            case HAIR_CURLY:
                P.setColor(h.hair);
                c.drawCircle(72, 66, 13, P);
                c.drawCircle(86, 52, 14, P);
                c.drawCircle(103, 47, 15, P);
                c.drawCircle(120, 53, 14, P);
                c.drawCircle(132, 68, 12, P);
                break;
            case HAIR_PONY:
                PATH.moveTo(136, 66);
                PATH.cubicTo(156, 70, 162, 98, 150, 122);
                PATH.cubicTo(146, 104, 142, 92, 134, 86);
                PATH.close();
                PATH.moveTo(62, 84);
                PATH.cubicTo(58, 54, 78, 40, 100, 40);
                PATH.cubicTo(124, 40, 142, 54, 138, 84);
                PATH.cubicTo(128, 66, 112, 60, 100, 62);
                PATH.cubicTo(88, 60, 72, 66, 62, 84);
                PATH.close();
                fill(c, h.hair, PATH);
                P.setColor(h.trim);
                c.drawCircle(138, 68, 6, P);
                break;
            default:
                P.setColor(h.hair);
                c.drawCircle(66, 54, 14, P);
                c.drawCircle(134, 54, 14, P);
                PATH.moveTo(62, 84);
                PATH.cubicTo(58, 54, 78, 42, 100, 42);
                PATH.cubicTo(122, 42, 142, 54, 138, 84);
                PATH.cubicTo(130, 64, 112, 60, 100, 61);
                PATH.cubicTo(88, 60, 70, 64, 62, 84);
                PATH.close();
                fill(c, h.hair, PATH);
                break;
        }
    }

    private static void eyes(Canvas c, Hero h, float t) {
        PATH.reset();
        switch (h.eyes) {
            case EYES_STARMASK:
                star(PATH, 84, 86, 17, 0.62f);
                star(PATH, 116, 86, 17, 0.62f);
                PATH.addRect(92, 83, 108, 88, Path.Direction.CW);
                fill(c, h.mask, PATH);
                break;
            case EYES_GOGGLES:
                P.setColor(h.mask);
                R.set(60, 80, 140, 87);
                c.drawRoundRect(R, 3.5f, 3.5f, P);
                c.drawCircle(84, 86, 14, P);
                c.drawCircle(116, 86, 14, P);
                P.setColor(0xFFDFF4FF);
                c.drawCircle(84, 86, 10.5f, P);
                c.drawCircle(116, 86, 10.5f, P);
                break;
            default:
                PATH.moveTo(64, 84);
                PATH.cubicTo(70, 72, 90, 72, 100, 80);
                PATH.cubicTo(110, 72, 130, 72, 136, 84);
                PATH.cubicTo(132, 98, 116, 100, 100, 92);
                PATH.cubicTo(84, 100, 68, 98, 64, 84);
                PATH.close();
                fill(c, h.mask, PATH);
                break;
        }
        // A blink every few seconds keeps them alive.
        boolean blink = (t % 3.7f) < 0.12f;
        for (float x : new float[]{84, 116}) {
            if (blink) {
                S.setColor(0xFF3B2A20);
                S.setStrokeWidth(2.5f);
                c.drawLine(x - 6, 88, x + 6, 88, S);
                continue;
            }
            P.setColor(0xFFFFFFFF);
            c.drawOval(new RectF(x - 7.5f, 78.5f, x + 7.5f, 95.5f), P);
            P.setColor(0xFF3B2A20);
            c.drawCircle(x + 1, 88, 5, P);
            P.setColor(0xFFFFFFFF);
            c.drawCircle(x + 2.5f, 85.5f, 1.8f, P);
        }
    }

    static void star(Path p, float cx, float cy, float r, float inner) {
        for (int i = 0; i < 10; i++) {
            double a = Math.PI / 5 * i - Math.PI / 2;
            float rr = i % 2 == 1 ? r * inner : r;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) p.moveTo(x, y);
            else p.lineTo(x, y);
        }
        p.close();
    }

    private static void fill(Canvas c, int color, Path p) {
        P.setColor(color);
        c.drawPath(p, P);
    }
}
