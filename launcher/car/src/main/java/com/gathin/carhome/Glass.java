package com.gathin.carhome;

import android.graphics.Bitmap;
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
 *
 * Built for slow head units: the finished panel (blur cut to the rounded shape, veil and rim) is drawn
 * once into its own bitmap and reused on every frame, so an animating clock next to it costs nothing here.
 * It is rebuilt only when the panel's size or the wallpaper changes, or shortly after it stops moving.
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
    private final Paint cachePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final Rect dst = new Rect();
    private final int[] at = new int[2], rootAt = new int[2];
    private final float stroke;
    private Bitmap cache;
    private Wallpaper cachedFor;
    private int cachedX = Integer.MIN_VALUE, cachedY = Integer.MIN_VALUE;
    private boolean rebuildPosted;
    private final Runnable rebuild = () -> {
        rebuildPosted = false;
        cachedFor = null;   // forces a fresh cut at the new place
        invalidateSelf();
    };

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
        clip.addRoundRect(new RectF(0, 0, b.width(), b.height()), radius, radius, Path.Direction.CW);
        rim.setShader(new LinearGradient(0, 0, b.width(), b.height(),
                new int[]{0x80FFFFFF, 0x14FFFFFF, 0x40FFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cachedFor = null;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        Wallpaper wp = source.wallpaper;
        View root = source.root;
        if (wp == null || root == null || root.getWidth() == 0) {
            // No wallpaper yet: just the veil and rim, which are cheap.
            c.save();
            c.translate(b.left, b.top);
            c.clipPath(clip);
            c.drawRect(0, 0, b.width(), b.height(), veil);
            c.restore();
            c.save();
            c.translate(b.left, b.top);
            rect.set(stroke / 2, stroke / 2, b.width() - stroke / 2, b.height() - stroke / 2);
            c.drawRoundRect(rect, radius, radius, rim);
            c.restore();
            return;
        }
        // Screen positions, so a panel in a dialog (its own window) lines up with the wallpaper too.
        owner.getLocationOnScreen(at);
        root.getLocationOnScreen(rootAt);
        int x = at[0] - rootAt[0] + b.left, y = at[1] - rootAt[1] + b.top;
        boolean sizeOk = cache != null && cache.getWidth() == b.width() && cache.getHeight() == b.height();
        if (!sizeOk || cachedFor != wp) {
            build(b, wp, root, x, y);
        } else if (x != cachedX || y != cachedY) {
            // Moving (a page swipe): keep showing the panel as it is, and cut it again once it settles.
            if (!rebuildPosted) {
                rebuildPosted = true;
                owner.postDelayed(rebuild, 180);
            } else {
                owner.removeCallbacks(rebuild);
                owner.postDelayed(rebuild, 180);
            }
        }
        c.drawBitmap(cache, b.left, b.top, cachePaint);
    }

    private void build(Rect b, Wallpaper wp, View root, int x, int y) {
        if (cache == null || cache.getWidth() != b.width() || cache.getHeight() != b.height()) {
            if (cache != null) cache.recycle();
            cache = Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888);
        } else {
            cache.eraseColor(0);
        }
        Canvas cc = new Canvas(cache);
        cc.save();
        cc.clipPath(clip);
        dst.set(-x, -y, root.getWidth() - x, root.getHeight() - y);
        cc.drawBitmap(wp.blurred, null, dst, bitmapPaint);
        cc.drawRect(0, 0, b.width(), b.height(), veil);
        cc.restore();
        rect.set(stroke / 2, stroke / 2, b.width() - stroke / 2, b.height() - stroke / 2);
        cc.drawRoundRect(rect, radius, radius, rim);
        cachedFor = wp;
        cachedX = x;
        cachedY = y;
    }

    @Override
    public void setAlpha(int alpha) {
        cachePaint.setAlpha(alpha);
        veil.setAlpha(Math.round(((veilColor >>> 24) * alpha) / 255f));
        rim.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        cachePaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
