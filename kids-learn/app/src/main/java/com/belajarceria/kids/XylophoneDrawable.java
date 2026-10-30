package com.belajarceria.kids;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * A toy xylophone: rainbow bars getting shorter from left to right, two rails, two mallets. Drawn in code because
 * there is no xylophone emoji, and X needs a picture that starts with X.
 */
final class XylophoneDrawable extends Drawable {
    private static final int[] BARS = {0xFFFF4F5E, 0xFFFF9F1C, 0xFFFFD23F, 0xFF2EC4B6, 0xFF3A86FF, 0xFF8338EC};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        // Fit a 100 x 80 design, centred, at 85% of the space.
        float s = Math.min(b.width() / 100f, b.height() / 80f) * 0.85f;
        c.save();
        c.translate(b.exactCenterX() - 50 * s, b.exactCenterY() - 40 * s);
        c.scale(s, s);

        // Two wooden rails the bars rest on.
        paint.setColor(0xFF8D5A3B);
        r.set(4, 22, 96, 28);
        c.drawRoundRect(r, 3, 3, paint);
        r.set(4, 58, 96, 64);
        c.drawRoundRect(r, 3, 3, paint);

        // Six bars, long on the left, short on the right, each with a soft shadow and two little pins.
        for (int i = 0; i < BARS.length; i++) {
            float x = 8 + i * 14.5f, inset = i * 3.2f;
            r.set(x, 12 + inset, x + 11, 74 - inset);
            paint.setColor(0x33000000);
            r.offset(1.2f, 1.8f);
            c.drawRoundRect(r, 4, 4, paint);
            r.offset(-1.2f, -1.8f);
            paint.setColor(BARS[i]);
            c.drawRoundRect(r, 4, 4, paint);
            paint.setColor(0x66FFFFFF);
            c.drawRoundRect(new RectF(r.left + 2, r.top + 2, r.left + 4.5f, r.bottom - 3), 2, 2, paint);
            paint.setColor(0xFFEDEDED);
            c.drawCircle(x + 5.5f, 25, 1.6f, paint);
            c.drawCircle(x + 5.5f, 61, 1.6f, paint);
        }

        // Two mallets leaning across the front.
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(3f);
        paint.setColor(0xFFB07A4F);
        c.drawLine(18, 80, 46, 52, paint);
        c.drawLine(82, 80, 58, 54, paint);
        paint.setStrokeWidth(0f);
        paint.setColor(0xFFE53935);
        c.drawCircle(46, 51, 5.5f, paint);
        paint.setColor(0xFF1E88E5);
        c.drawCircle(58, 53, 5.5f, paint);
        paint.setColor(0x80FFFFFF);
        c.drawCircle(44.5f, 49.5f, 1.8f, paint);
        c.drawCircle(56.5f, 51.5f, 1.8f, paint);
        c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
