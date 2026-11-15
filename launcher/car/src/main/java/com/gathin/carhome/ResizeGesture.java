package com.gathin.carhome;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

/**
 * Hold and drag to resize a glass box, like a phone's home-screen widget. Hold the box: it shows a dashed
 * outline and a corner handle. Drag: right and left change the width, down and up the height. Tap it (or
 * wait a few seconds) to finish; the new size is then saved. While resizing, the pages don't swipe.
 */
final class ResizeGesture {
    /** What is being resized: its size as fractions of the space it has (0.4 to 1). */
    interface Target {
        float boxWidth();

        float boxHeight();

        /** The new size; called while dragging. */
        void setBox(float width, float height);

        /** Resizing started (true) or finished (false); on finish, save the size. */
        void onEditing(boolean editing);
    }

    static final float MIN = 0.4f, MAX = 1f;
    private static final long IDLE_FINISH_MS = 6_000;

    private final View view;
    private final Target target;
    /** How a drag maps to the size: the box's full width and height in pixels at fraction 1. */
    private final boolean centred;
    private final int slop;
    private boolean editing, moved, editingAtDown;
    private float lastX, lastY, startX, startY, startW, startH;
    private final Runnable longPress = this::start;
    private final Runnable idleFinish = () -> finish();

    /**
     * @param centred the box grows from its middle (both sides move), else from its top edge (only the
     *                bottom moves)
     */
    ResizeGesture(View view, Target target, boolean centred) {
        this.view = view;
        this.target = target;
        this.centred = centred;
        slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    boolean isEditing() {
        return editing;
    }

    /**
     * The view's touches. fullW and fullH: the box's size in pixels at fraction 1. Returns true while the
     * touch belongs to the resize.
     */
    boolean onTouch(MotionEvent e, float fullW, float fullH) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = startX = e.getX();
                lastY = startY = e.getY();
                moved = false;
                editingAtDown = editing;
                if (editing) {
                    view.removeCallbacks(idleFinish);
                    startW = target.boxWidth();
                    startH = target.boxHeight();
                    disallowParent(true);
                } else {
                    view.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                lastX = e.getX();
                lastY = e.getY();
                if (!editing) {
                    if (Math.hypot(lastX - startX, lastY - startY) > slop) view.removeCallbacks(longPress);
                    return false;
                }
                float dx = lastX - startX, dy = lastY - startY;
                if (Math.hypot(dx, dy) > slop) moved = true;
                // The box stays centred across, so the width grows on both sides; the height grows on both
                // sides too when centred, else only downwards.
                float w = clamp(startW + 2f * dx / Math.max(1f, fullW));
                float h = clamp(startH + (centred ? 2f : 1f) * dy / Math.max(1f, fullH));
                target.setBox(w, h);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.removeCallbacks(longPress);
                if (!editing) return false;
                disallowParent(false);
                // A tap while resizing finishes it; after a drag, wait for more or finish when idle.
                if (editingAtDown && !moved && e.getActionMasked() == MotionEvent.ACTION_UP) finish();
                else view.postDelayed(idleFinish, IDLE_FINISH_MS);
                return true;
            default:
                return editing;
        }
    }

    private void start() {
        editing = true;
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // Drags from here on resize, starting at the size the box has now.
        startX = lastX;
        startY = lastY;
        startW = target.boxWidth();
        startH = target.boxHeight();
        disallowParent(true);
        target.onEditing(true);
        view.invalidate();
    }

    void finish() {
        view.removeCallbacks(idleFinish);
        view.removeCallbacks(longPress);
        if (!editing) return;
        editing = false;
        target.onEditing(false);
        view.invalidate();
    }

    private void disallowParent(boolean on) {
        ViewParent p = view.getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(on);
    }

    static float clamp(float f) {
        return Math.max(MIN, Math.min(MAX, f));
    }

    /** The resize look: a dashed outline round the box and a round handle on its bottom-right corner. */
    static void drawEditFrame(Canvas c, RectF box, float radius, float density) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * density);
        line.setColor(0xE6FFFFFF);
        line.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));
        c.drawRoundRect(box, radius, radius, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF0A84FF);
        float r = 11 * density;
        c.drawCircle(box.right - r * 0.6f, box.bottom - r * 0.6f, r, dot);
        dot.setColor(0xFFFFFFFF);
        c.drawCircle(box.right - r * 0.6f, box.bottom - r * 0.6f, r * 0.4f, dot);
    }

    /** The clear glass box itself: a light frosted fill, brighter at the top, and a thin bright rim. */
    static void drawGlassBox(Canvas c, RectF box, float radius, float density, Paint fill, Paint rim) {
        c.drawRoundRect(box, radius, radius, fill);
        float h = rim.getStrokeWidth() / 2;
        c.drawRoundRect(box.left + h, box.top + h, box.right - h, box.bottom - h, radius, radius, rim);
    }
}
