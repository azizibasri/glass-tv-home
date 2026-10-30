package com.belajarceria.kids;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** A flat shape with a dark outline, as on the test paper: triangle, circle, rectangle, square, upside-down triangle. */
final class ShapeDrawable2D extends Drawable {
    private static final int[] FILLS = {0xFFFFC52E, 0xFFFF7EB6, 0xFF4FC3F7, 0xFF7ED67E, 0xFFB18CFF};

    private final int shape;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    ShapeDrawable2D(int shape) {
        this.shape = shape;
        fill.setColor(FILLS[shape % FILLS.length]);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFF2B2D42);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) * 0.8f;
        float cx = b.exactCenterX(), cy = b.exactCenterY(), h = s / 2;
        line.setStrokeWidth(Math.max(3f, s * 0.03f));
        path.reset();
        switch (shape) {
            case 0: // triangle
                path.moveTo(cx, cy - h);
                path.lineTo(cx + h, cy + h * 0.8f);
                path.lineTo(cx - h, cy + h * 0.8f);
                path.close();
                break;
            case 1: // circle
                path.addCircle(cx, cy, h * 0.9f, Path.Direction.CW);
                break;
            case 2: // rectangle: clearly wider than tall
                path.addRect(cx - h * 1.1f, cy - h * 0.5f, cx + h * 1.1f, cy + h * 0.5f, Path.Direction.CW);
                break;
            case 3: // square
                path.addRect(cx - h * 0.8f, cy - h * 0.8f, cx + h * 0.8f, cy + h * 0.8f, Path.Direction.CW);
                break;
            default: // upside-down triangle
                path.moveTo(cx - h, cy - h * 0.8f);
                path.lineTo(cx + h, cy - h * 0.8f);
                path.lineTo(cx, cy + h);
                path.close();
                break;
        }
        c.drawPath(path, fill);
        c.drawPath(path, line);
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        line.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        fill.setColorFilter(cf);
        line.setColorFilter(cf);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
