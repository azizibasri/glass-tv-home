package com.belajarceria.kids;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * A soft pastel backdrop with big blurred colour blobs that drift slowly, like the iPhone's dynamic wallpapers.
 * Its colours glide to each subject's palette when the child opens it. The blobs are soft anyway, so they are
 * painted into a bitmap a tenth of the screen's size and stretched: cheap enough for any tablet.
 */
final class BlobBackground extends View {
    private static final float[][] PATHS = {
            // x centre, x swing, x speed, y centre, y swing, y speed, phase
            {0.18f, 0.16f, 0.21f, 0.25f, 0.18f, 0.17f, 0f},
            {0.85f, 0.14f, 0.16f, 0.30f, 0.22f, 0.23f, 1.7f},
            {0.30f, 0.20f, 0.13f, 0.88f, 0.12f, 0.19f, 3.1f},
            {0.78f, 0.18f, 0.18f, 0.82f, 0.16f, 0.14f, 4.4f},
    };

    private static final int SHRINK = 10;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stretch = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect dest = new Rect();
    private Bitmap small;
    private Canvas smallCanvas;
    private final Matrix matrix = new Matrix();
    private final RadialGradient[] blobs = new RadialGradient[4];
    private final int[] shown = new int[6];
    private final long start = SystemClock.uptimeMillis();
    private int[] from = Theme.CLASSIC.home, to = Theme.CLASSIC.home;
    private float mix = 1f, radius;
    private LinearGradient base;
    private ValueAnimator fade;

    // The theme's decoration, drawn sharp over the soft blobs.
    private final Paint deco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();
    private float[][] buildings = new float[0][], flakes = new float[0][];

    BlobBackground(Context c) {
        super(c);
    }

    void setPalette(int[] palette) {
        int[] now = current();
        from = now;
        to = palette;
        if (fade != null) fade.cancel();
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(700);
        fade.addUpdateListener(a -> mix = (float) a.getAnimatedValue());
        fade.start();
        mix = 0f;
    }

    private int[] current() {
        int[] c = new int[6];
        for (int i = 0; i < 6; i++) c[i] = Content.mix(from[i], to[i], mix);
        return c;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        radius = Math.max(w, h) * 0.55f;
        if (w > 0 && h > 0) {
            small = Bitmap.createBitmap(w / SHRINK + 1, h / SHRINK + 1, Bitmap.Config.ARGB_8888);
            smallCanvas = new Canvas(small);
            smallCanvas.scale(small.getWidth() / (float) w, small.getHeight() / (float) h);
            dest.set(0, 0, w, h);
        }
        base = null; // rebuilt at the new size on the next frame
        java.util.Arrays.fill(blobs, null);
        layoutDecor(w, h);
    }

    /** A made-up skyline and a field of snowflakes, the same every time (fixed seed). */
    private void layoutDecor(int w, int h) {
        java.util.Random r = new java.util.Random(7);
        java.util.ArrayList<float[]> b = new java.util.ArrayList<>();
        for (float x = -Ui.dp(10); x < w; ) {
            float bw = Ui.dp(34) + r.nextFloat() * Ui.dp(50);
            b.add(new float[]{x, bw, h * (0.10f + r.nextFloat() * 0.17f)});
            x += bw + Ui.dp(3);
        }
        buildings = b.toArray(new float[0][]);
        flakes = new float[46][];
        for (int i = 0; i < flakes.length; i++) {
            // x, start y, fall speed (px/s), radius, sway phase
            flakes[i] = new float[]{r.nextFloat() * w, r.nextFloat() * h, Ui.dp(18) + r.nextFloat() * Ui.dp(30),
                    Ui.dp(2) + r.nextFloat() * Ui.dp(3.5f), r.nextFloat() * 6.3f};
        }
    }

    private void drawDecor(Canvas c, int w, int h, float t) {
        int d = Theme.current.deco;
        if (d == Theme.DECO_CITY) {
            // Comic stars drifting across the sky.
            deco.setColor(0x3DFFB300);
            for (int i = 0; i < 6; i++) {
                float x = ((i * 0.19f + t * 0.012f * (1 + i % 3)) % 1.1f) * w;
                float y = h * (0.12f + 0.11f * (i % 4));
                star(c, x, y, Ui.dp(10 + 4 * (i % 3)), t * 20 + i * 40);
            }
            // The skyline, with a few lit windows.
            for (float[] b : buildings) {
                deco.setColor(0x221A237E);
                c.drawRect(b[0], h - b[2], b[0] + b[1], h, deco);
                deco.setColor(0x33FFC400);
                float win = Ui.dp(5);
                for (float wy = h - b[2] + Ui.dp(8); wy < h - Ui.dp(10); wy += Ui.dp(14)) {
                    for (float wx = b[0] + Ui.dp(7); wx < b[0] + b[1] - Ui.dp(8); wx += Ui.dp(12)) {
                        if (((int) (wx * 7 + wy * 3)) % 3 == 0) c.drawRect(wx, wy, wx + win, wy + win, deco);
                    }
                }
            }
        } else if (d == Theme.DECO_SNOW) {
            castle(c, w * 0.78f, h, Math.min(w, h) * 0.34f);
            deco.setColor(0x8C9DBDFF);
            for (float[] f : flakes) {
                float y = (f[1] + f[2] * t) % (h + Ui.dp(20)) - Ui.dp(10);
                float x = f[0] + (float) Math.sin(t * 0.7f + f[4]) * Ui.dp(14);
                c.drawCircle(x, y, f[3], deco);
            }
        }
    }

    private void star(Canvas c, float cx, float cy, float r, float angle) {
        shape.reset();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(angle + i * 36 - 90);
            float rr = i % 2 == 0 ? r : r * 0.45f;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            if (i == 0) shape.moveTo(x, y);
            else shape.lineTo(x, y);
        }
        shape.close();
        c.drawPath(shape, deco);
    }

    /** A simple ice castle: a wall, three towers with pointed roofs. */
    private void castle(Canvas c, float cx, float bottom, float size) {
        deco.setColor(0x2E7E6BFF);
        shape.reset();
        float wall = size * 0.32f;
        shape.addRect(cx - size * 0.55f, bottom - wall, cx + size * 0.55f, bottom, Path.Direction.CW);
        float[][] towers = {{-0.42f, 0.55f, 0.16f}, {0f, 0.85f, 0.2f}, {0.42f, 0.55f, 0.16f}};
        for (float[] tw : towers) {
            float x = cx + tw[0] * size, top = bottom - tw[1] * size, half = tw[2] * size / 2;
            shape.addRect(x - half, top, x + half, bottom, Path.Direction.CW);
            shape.moveTo(x - half * 1.25f, top);
            shape.lineTo(x, top - size * 0.22f);
            shape.lineTo(x + half * 1.25f, top);
            shape.close();
        }
        c.drawPath(shape, deco);
    }

    @Override
    protected void onDraw(Canvas screen) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || small == null) return;
        Canvas canvas = smallCanvas;
        int[] c = current();
        if (c[0] != shown[0] || c[1] != shown[1] || base == null) {
            base = new LinearGradient(0, 0, 0, h, c[0], c[1], Shader.TileMode.CLAMP);
        }
        for (int i = 0; i < 4; i++) {
            if (c[i + 2] != shown[i + 2] || blobs[i] == null) {
                int col = c[i + 2];
                blobs[i] = new RadialGradient(0, 0, radius,
                        new int[]{(col & 0x00FFFFFF) | 0x8C000000, (col & 0x00FFFFFF) | 0x33000000, col & 0x00FFFFFF},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
            }
        }
        System.arraycopy(c, 0, shown, 0, 6);

        paint.setShader(base);
        canvas.drawRect(0, 0, w, h, paint);
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        for (int i = 0; i < 4; i++) {
            float[] p = PATHS[i];
            float x = w * (p[0] + p[1] * (float) Math.sin(t * p[2] + p[6]));
            float y = h * (p[3] + p[4] * (float) Math.cos(t * p[5] + p[6] * 0.7f));
            matrix.setTranslate(x, y);
            blobs[i].setLocalMatrix(matrix);
            paint.setShader(blobs[i]);
            canvas.drawCircle(x, y, radius, paint);
        }
        paint.setShader(null);
        screen.drawBitmap(small, null, dest, stretch);
        drawDecor(screen, w, h, t);
        postInvalidateOnAnimation();
    }
}
