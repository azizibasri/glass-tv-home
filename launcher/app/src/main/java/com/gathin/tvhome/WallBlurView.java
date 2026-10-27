package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

/**
 * A full-screen layer just above the wallpaper that paints the glass's blurred copy of it. Faded in
 * while an app tile has focus, so the picture softens a little behind the apps. It reuses the blurred
 * copy the glass already keeps (no extra bitmap), and is GONE while invisible so it costs nothing then.
 *
 * It also washes the picture in the focused app's colour (from its icon). The ambient glow alone was
 * lost on the user's own photos: they are shown undimmed, and on a bright picture a soft coloured
 * glow barely shows. A flat wash over the whole picture shows on any wallpaper.
 */
final class WallBlurView extends View {
    /** How strongly the app's colour washes over the picture (0..255), before the layer's own fade. */
    private static final int TINT_ALPHA = 0x24;

    private final Backdrop.Fill fill = new Backdrop.Fill();
    private final RectF rect = new RectF();
    private int tint;               // RGB of the focused app, 0 = none yet
    private ValueAnimator tintAnim;

    WallBlurView(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Moves the wash to {@code color} (the app's colour), blending across over {@code ms}. */
    void setTint(int color, long ms) {
        int to = color & 0xFFFFFF;
        if (tintAnim != null) tintAnim.cancel();
        if (ms <= 0 || tint == 0 || getVisibility() != VISIBLE) {
            tint = to;
            invalidate();
            return;
        }
        tintAnim = ValueAnimator.ofArgb(0xFF000000 | tint, 0xFF000000 | to);
        tintAnim.setDuration(ms);
        tintAnim.addUpdateListener(a -> {
            tint = ((int) a.getAnimatedValue()) & 0xFFFFFF;
            invalidate();
        });
        tintAnim.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        fill.draw(canvas, this, rect, 0f, 1f);
        if (tint != 0) canvas.drawColor((TINT_ALPHA << 24) | tint);
    }
}
