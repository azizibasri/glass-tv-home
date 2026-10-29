package com.belajarceria.kids;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * A soft pastel backdrop with big blurred colour blobs that drift slowly, like the iPhone's dynamic wallpapers.
 * Its colours glide to each subject's palette when the child opens it.
 */
final class BlobBackground extends View {
    private static final float[][] PATHS = {
            // x centre, x swing, x speed, y centre, y swing, y speed, phase
            {0.18f, 0.16f, 0.21f, 0.25f, 0.18f, 0.17f, 0f},
            {0.85f, 0.14f, 0.16f, 0.30f, 0.22f, 0.23f, 1.7f},
            {0.30f, 0.20f, 0.13f, 0.88f, 0.12f, 0.19f, 3.1f},
            {0.78f, 0.18f, 0.18f, 0.82f, 0.16f, 0.14f, 4.4f},
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RadialGradient[] blobs = new RadialGradient[4];
    private final int[] shown = new int[6];
    private final long start = SystemClock.uptimeMillis();
    private int[] from = Content.HOME, to = Content.HOME;
    private float mix = 1f, radius;
    private LinearGradient base;
    private ValueAnimator fade;

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
        base = null; // rebuilt at the new size on the next frame
        java.util.Arrays.fill(blobs, null);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
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
        postInvalidateOnAnimation();
    }
}
