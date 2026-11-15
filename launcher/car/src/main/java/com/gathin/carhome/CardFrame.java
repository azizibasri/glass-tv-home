package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A dashboard panel as a resizable glass card. The card sits at the top of its space, against the
 * screen's outer side; hold it and drag to resize it (see {@link ResizeGesture}). What is on the card is
 * laid out for the full space and scaled down with the card, so every widget on it follows its size.
 */
final class CardFrame extends FrameLayout implements ResizeGesture.Target {
    interface Listener {
        /** The size changed (while dragging: done false; at the end: done true, to save). */
        void onCardSize(float width, float height, boolean done);

        void onEditing(boolean editing);
    }

    private final View card, content;
    private final boolean anchorRight;
    private final float radius, d;
    private final ResizeGesture gesture;
    private final RectF box = new RectF();
    private float cardW = 1f, cardH = 1f;
    private Listener listener;

    CardFrame(Context ctx, View content, boolean anchorRight, float radius) {
        super(ctx);
        this.content = content;
        this.anchorRight = anchorRight;
        this.radius = radius;
        d = getResources().getDisplayMetrics().density;
        card = new View(ctx);
        addView(card);
        addView(content);
        gesture = new ResizeGesture(this, this, anchorRight ? -1f : 1f, 1f);
        setClipChildren(false);
    }

    /** The glass card behind the content: give it its background. */
    View card() {
        return card;
    }

    void setListener(Listener l) {
        listener = l;
    }

    void setCardSize(float w, float h) {
        cardW = ResizeGesture.clamp(w);
        cardH = ResizeGesture.clamp(h);
        requestLayout();
        invalidate();
    }

    void finishEditing() {
        gesture.finish();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        card.measure(MeasureSpec.makeMeasureSpec(Math.round(w * cardW), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.round(h * cardH), MeasureSpec.EXACTLY));
        // The content keeps the full-size layout; it is scaled to the card when laid out.
        content.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int bw = Math.round(w * cardW), bh = Math.round(h * cardH);
        int x = anchorRight ? w - bw : 0;
        box.set(x, 0, x + bw, bh);
        card.layout(x, 0, x + bw, bh);
        content.layout(0, 0, w, h);
        // Scale evenly (never stretched) to fit the card, centred in it.
        float s = Math.min(bw / (float) Math.max(1, w), bh / (float) Math.max(1, h));
        content.setPivotX(0);
        content.setPivotY(0);
        content.setScaleX(s);
        content.setScaleY(s);
        content.setTranslationX(x + (bw - w * s) / 2f);
        content.setTranslationY((bh - h * s) / 2f);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (gesture.isEditing()) ResizeGesture.drawEditFrame(c, box, radius, d, anchorRight);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return gesture.isEditing();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // Only a hold on the card itself starts a resize.
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && !gesture.isEditing()
                && !box.contains(e.getX(), e.getY())) return false;
        return gesture.onTouch(e, getWidth(), getHeight());
    }

    @Override
    public float boxWidth() {
        return cardW;
    }

    @Override
    public float boxHeight() {
        return cardH;
    }

    @Override
    public void setBox(float width, float height) {
        setCardSize(width, height);
        if (listener != null) listener.onCardSize(cardW, cardH, false);
    }

    @Override
    public void onEditing(boolean editing) {
        if (listener != null) {
            listener.onEditing(editing);
            if (!editing) listener.onCardSize(cardW, cardH, true);
        }
    }
}
