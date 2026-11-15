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
    /** How a drag maps to the size: 2 grows on both sides, 1 towards the right or down, -1 towards the left. */
    private final float xFactor, yFactor;
    private final int slop;
    private boolean editing, moved, editingAtDown;
    private float lastX, lastY, startX, startY, startW, startH;
    private final Runnable longPress = this::start;
    private final Runnable idleFinish = () -> finish();

    /**
     * @param xFactor 2: the box grows on both sides; 1: its right edge moves; -1: its left edge moves
     * @param yFactor 2: the box grows up and down; 1: its bottom edge moves
     */
    ResizeGesture(View view, Target target, float xFactor, float yFactor) {
        this.view = view;
        this.target = target;
        this.xFactor = xFactor;
        this.yFactor = yFactor;
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
                float w = clamp(startW + xFactor * dx / Math.max(1f, fullW));
                float h = clamp(startH + yFactor * dy / Math.max(1f, fullH));
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
    static void drawEditFrame(Canvas c, RectF box, float radius, float density, boolean handleLeft) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * density);
        line.setColor(0xE6FFFFFF);
        line.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));
        c.drawRoundRect(box, radius, radius, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF0A84FF);
        float r = 11 * density;
        float hx = handleLeft ? box.left + r * 0.6f : box.right - r * 0.6f;
        c.drawCircle(hx, box.bottom - r * 0.6f, r, dot);
        dot.setColor(0xFFFFFFFF);
        c.drawCircle(hx, box.bottom - r * 0.6f, r * 0.4f, dot);
    }
}
