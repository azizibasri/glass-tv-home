package com.gathin.carhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Frosted glass: the blurred wallpaper that lies under this panel, a light veil, and a thin rim that
 * catches the light at the top-left. The blur is the one bitmap {@link Wallpaper} made, drawn at the
 * panel's own place on screen, so every panel lines up with the picture behind it.
 */
final class Glass extends Drawable {
    /** Shared by every panel: the root view the wallpaper fills, and the wallpaper once it is ready. */
    static final class Source {
        View root;
        Wallpaper wallpaper;
    }

    private final Source source;
    private final View owner;
    private final float radius;
    private final int veilColor;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final Rect dst = new Rect();
    private final int[] at = new int[2], rootAt = new int[2];
    private final float stroke;

    Glass(Source source, View owner, float radius, float density, int veilColor) {
        this.source = source;
        this.owner = owner;
        this.radius = radius;
        this.veilColor = veilColor;
        this.stroke = Math.max(1f, density);
        veil.setColor(veilColor);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
    }

    /** The usual panel: a 14% white veil. */
    Glass(Source source, View owner, float radius, float density) {
        this(source, owner, radius, density, 0x24FFFFFF);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        rect.set(b.left + stroke / 2, b.top + stroke / 2, b.right - stroke / 2, b.bottom - stroke / 2);
        clip.reset();
        clip.addRoundRect(new RectF(b), radius, radius, Path.Direction.CW);
        rim.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0x80FFFFFF, 0x14FFFFFF, 0x40FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int save = c.save();
        c.clipPath(clip);
        Wallpaper wp = source.wallpaper;
        View root = source.root;
        if (wp != null && root != null && root.getWidth() > 0) {
            // Screen positions, so a panel in a dialog (its own window) lines up with the wallpaper too.
            owner.getLocationOnScreen(at);
            root.getLocationOnScreen(rootAt);
            int x = at[0] - rootAt[0], y = at[1] - rootAt[1];
            dst.set(-x, -y, root.getWidth() - x, root.getHeight() - y);
            c.drawBitmap(wp.blurred, null, dst, bitmapPaint);
        }
        c.drawRect(b, veil);
        c.restoreToCount(save);
        c.drawRoundRect(rect, radius, radius, rim);
    }

    @Override
    public void setAlpha(int alpha) {
        veil.setAlpha(Math.round(((veilColor >>> 24) * alpha) / 255f));
        bitmapPaint.setAlpha(alpha);
        rim.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        veil.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
