package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.View;

/**
 * A full-screen layer just above the wallpaper that paints the glass's blurred copy of it. Faded in
 * while an app tile has focus, so the picture softens a little behind the apps. It reuses the blurred
 * copy the glass already keeps (no extra bitmap), and is GONE while invisible so it costs nothing then.
 */
final class WallBlurView extends View {
    private final Backdrop.Fill fill = new Backdrop.Fill();
    private final RectF rect = new RectF();

    WallBlurView(Context context) {
        super(context);
        setWillNotDraw(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        fill.draw(canvas, this, rect, 0f, 1f);
    }
}
