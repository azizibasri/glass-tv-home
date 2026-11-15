package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A clear glass box, centred in the space it is given, that the person resizes by holding and dragging it
 * (see {@link ResizeGesture}). Holds one child, laid out inside the box.
 */
final class ResizeFrame extends FrameLayout implements ResizeGesture.Target {
    interface Listener {
        /** The size changed (while dragging: done false; at the end: done true, to save). */
        void onBoxSize(float width, float height, boolean done);

        void onEditing(boolean editing);
    }

    private final float d;
    private final ResizeGesture gesture;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float boxW = 1f, boxH = 1f;
    private Listener listener;

    ResizeFrame(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        gesture = new ResizeGesture(this, this, true);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, d));
        setWillNotDraw(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setBoxSize(float w, float h) {
        boxW = ResizeGesture.clamp(w);
        boxH = ResizeGesture.clamp(h);
        requestLayout();
        invalidate();
    }

    void finishEditing() {
        gesture.finish();
    }

    float radius() {
        return 20 * d;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        int bw = Math.round(w * boxW), bh = Math.round(h * boxH);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(bw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(bh, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int bw = Math.round(w * boxW), bh = Math.round(h * boxH);
        int x = (w - bw) / 2, y = (h - bh) / 2;
        box.set(x, y, x + bw, y + bh);
        fill.setShader(new LinearGradient(0, box.top, 0, box.bottom, 0x2EFFFFFF, 0x0FFFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(box.left, box.top, box.right, box.bottom,
                new int[]{0x80FFFFFF, 0x1AFFFFFF, 0x4DFFFFFF}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            c.layout(x, y, x + bw, y + bh);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        ResizeGesture.drawGlassBox(c, box, radius(), d, fill, rim);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (gesture.isEditing()) ResizeGesture.drawEditFrame(c, box, radius(), d);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return gesture.isEditing();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gesture.onTouch(e, getWidth(), getHeight());
    }

    @Override
    public float boxWidth() {
        return boxW;
    }

    @Override
    public float boxHeight() {
        return boxH;
    }

    @Override
    public void setBox(float width, float height) {
        setBoxSize(width, height);
        if (listener != null) listener.onBoxSize(boxW, boxH, false);
    }

    @Override
    public void onEditing(boolean editing) {
        if (listener != null) {
            listener.onEditing(editing);
            if (!editing) listener.onBoxSize(boxW, boxH, true);
        }
    }
}
