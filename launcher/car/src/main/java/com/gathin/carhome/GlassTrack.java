package com.gathin.carhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * A slider track in the glass style: a thin frosted bar centred in the slider, brighter up to the knob. It
 * draws its own height, so it looks the same on every screen density (a slider's own track can shrink to
 * nothing on dense screens).
 */
final class GlassTrack extends Drawable {
    private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float thickness;

    GlassTrack(float thicknessPx, float rimPx) {
        thickness = thicknessPx;
        back.setColor(0x33FFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, rimPx));
        rim.setColor(0x33FFFFFF);
        done.setColor(0x8CFFFFFF);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float cy = b.exactCenterY(), rad = thickness / 2;
        r.set(b.left, cy - rad, b.right, cy + rad);
        c.drawRoundRect(r, rad, rad, back);
        c.drawRoundRect(r, rad, rad, rim);
        r.right = b.left + b.width() * getLevel() / 10000f;
        if (r.width() > 0) c.drawRoundRect(r, rad, rad, done);
    }

    @Override
    protected boolean onLevelChange(int level) {
        invalidateSelf();
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        back.setAlpha(alpha);
        done.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        done.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
