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
 * Hold and drag to move or resize a panel, like a phone's home-screen widget. Hold the panel: it shows a
 * dashed outline and a round handle on its bottom-right corner, and the same drag moves it. Then drag the
 * panel to move it, or the handle to resize it. Tap it (or wait a few seconds) to finish; the place and
 * size are then saved. While editing, the pages don't swipe.
 */
final class ResizeGesture {
    /** What is being edited: its size (fractions of its full size) and place (fractions of its space). */
    interface Target {
        float boxWidth();

        float boxHeight();

        float boxX();

        float boxY();

        /** The new size; called while dragging the handle. */
        void setBox(float width, float height);

        /** The new place of the top-left corner; called while dragging the panel. */
        void setPos(float x, float y);

        /** True when (x, y) is on the resize handle. */
        boolean onHandle(float x, float y);

        /** Editing started (true) or finished (false); on finish, save. */
        void onEditing(boolean editing);
    }

    static final float MIN = 0.4f, MAX = 1f;
    private static final long IDLE_FINISH_MS = 6_000;

    private final View view;
    private final Target target;
    private final int slop;
    private boolean editing, moved, editingAtDown, resizing;
    private float lastX, lastY, startX, startY, startW, startH, startPX, startPY;
    private final Runnable longPress = this::start;
    private final Runnable idleFinish = this::finish;

    ResizeGesture(View view, Target target) {
        this.view = view;
        this.target = target;
        slop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
    }

    boolean isEditing() {
        return editing;
    }

    /**
     * The view's touches. sizeW and sizeH: the panel's size in pixels at full size; spaceW and spaceH: the
     * space it moves in. Returns true while the touch belongs to the editing.
     */
    boolean onTouch(MotionEvent e, float sizeW, float sizeH, float spaceW, float spaceH) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = startX = e.getX();
                lastY = startY = e.getY();
                moved = false;
                editingAtDown = editing;
                if (editing) {
                    view.removeCallbacks(idleFinish);
                    remember(target.onHandle(startX, startY));
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
                if (resizing) {
                    target.setBox(clamp(startW + dx / Math.max(1f, sizeW)), clamp(startH + dy / Math.max(1f, sizeH)));
                } else {
                    target.setPos(startPX + dx / Math.max(1f, spaceW), startPY + dy / Math.max(1f, spaceH));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                view.removeCallbacks(longPress);
                if (!editing) return false;
                disallowParent(false);
                // A tap while editing finishes it; after a drag, wait for more or finish when idle.
                if (editingAtDown && !moved && e.getActionMasked() == MotionEvent.ACTION_UP) finish();
                else view.postDelayed(idleFinish, IDLE_FINISH_MS);
                return true;
            default:
                return editing;
        }
    }

    private void remember(boolean onHandle) {
        resizing = onHandle;
        startW = target.boxWidth();
        startH = target.boxHeight();
        startPX = target.boxX();
        startPY = target.boxY();
    }

    private void start() {
        editing = true;
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        // The same drag moves the panel, starting where it is now.
        startX = lastX;
        startY = lastY;
        remember(false);
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

    /** The resize handle's radius. */
    static float handleRadius(float density) {
        return 13 * density;
    }

    /** The editing look: a dashed outline round the box and a round handle on its bottom-right corner. */
    static void drawEditFrame(Canvas c, RectF box, float radius, float density) {
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * density);
        line.setColor(0xE6FFFFFF);
        line.setPathEffect(new DashPathEffect(new float[]{8 * density, 6 * density}, 0));
        c.drawRoundRect(box, radius, radius, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0xFF0A84FF);
        float r = handleRadius(density);
        float hx = box.right - r * 0.6f, hy = box.bottom - r * 0.6f;
        c.drawCircle(hx, hy, r, dot);
        dot.setColor(0xFFFFFFFF);
        c.drawCircle(hx, hy, r * 0.4f, dot);
    }
}
