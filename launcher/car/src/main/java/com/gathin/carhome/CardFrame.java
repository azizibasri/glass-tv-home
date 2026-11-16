package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * A dashboard panel as a glass card the person moves and resizes freely (see {@link ResizeGesture}). The
 * frame covers the whole dashboard; the card sits anywhere in it, kept on screen. What is on the card is
 * laid out at the panel's full size and scaled evenly with the card, so every widget on it follows.
 */
final class CardFrame extends FrameLayout implements ResizeGesture.Target {
    interface Listener {
        /** Size or place changed (while dragging: done false; at the end: done true, to save). */
        void onCard(CardFrame card, boolean done);

        void onEditing(boolean editing);
    }

    private final View card, content;
    /** The widget's first (full) size as fractions of the dashboard's. */
    private final float fullW, fullH;
    private final float radius, d;
    private final ResizeGesture gesture;
    private final RectF box = new RectF();
    private float cardW = 1f, cardH = 1f, posX, posY;
    private Listener listener;
    /** Stretch: the content fills the card at its real size (and the card can grow up to the whole dashboard). */
    private boolean stretch;

    CardFrame(Context ctx, View content, float fullWidth, float fullHeight, float defaultX, float defaultY,
              float radius) {
        super(ctx);
        this.content = content;
        this.fullW = fullWidth;
        this.fullH = fullHeight;
        this.posX = defaultX;
        this.posY = defaultY;
        this.radius = radius;
        d = getResources().getDisplayMetrics().density;
        card = new View(ctx);
        addView(card);
        addView(content);
        gesture = new ResizeGesture(this, this);
        setClipChildren(false);
    }

    /** The glass card behind the content: give it its background. */
    View card() {
        return card;
    }

    /**
     * For content that should get longer or taller with its box (the wave): laid out at the card's own
     * size instead of scaled evenly, and the card can be made as wide or tall as the dashboard.
     */
    void setStretch(boolean on) {
        stretch = on;
        requestLayout();
    }

    private float clampW(float f) {
        return stretch ? Math.max(ResizeGesture.MIN, Math.min(1f / Math.max(0.01f, fullW), f)) : ResizeGesture.clamp(f);
    }

    private float clampH(float f) {
        return stretch ? Math.max(ResizeGesture.MIN, Math.min(1f / Math.max(0.01f, fullH), f)) : ResizeGesture.clamp(f);
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Size (fractions of the full panel) and place (top-left corner, fractions of the dashboard). */
    void setCard(float w, float h, float x, float y) {
        cardW = clampW(w);
        cardH = clampH(h);
        posX = x;
        posY = y;
        requestLayout();
        invalidate();
    }

    float cardWidth() {
        return cardW;
    }

    float cardHeight() {
        return cardH;
    }

    void finishEditing() {
        gesture.finish();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        int full = Math.round(w * fullW), fullHt = Math.round(h * fullH);
        card.measure(MeasureSpec.makeMeasureSpec(Math.min(w, Math.round(full * cardW)), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.min(h, Math.round(fullHt * cardH)), MeasureSpec.EXACTLY));
        if (stretch) {
            content.measure(MeasureSpec.makeMeasureSpec(card.getMeasuredWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(card.getMeasuredHeight(), MeasureSpec.EXACTLY));
            return;
        }
        // The content keeps the full-size layout; it is scaled to the card when laid out.
        content.measure(MeasureSpec.makeMeasureSpec(full, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(fullHt, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l, h = b - t;
        int full = Math.round(w * fullW), fullHt = Math.round(h * fullH);
        int bw = Math.min(w, Math.round(full * cardW)), bh = Math.min(h, Math.round(fullHt * cardH));
        // Kept fully on the dashboard.
        int x = Math.max(0, Math.min(w - bw, Math.round(posX * w)));
        int y = Math.max(0, Math.min(h - bh, Math.round(posY * h)));
        box.set(x, y, x + bw, y + bh);
        card.layout(x, y, x + bw, y + bh);
        if (stretch) {
            content.setScaleX(1f);
            content.setScaleY(1f);
            content.setTranslationX(0);
            content.setTranslationY(0);
            content.layout(x, y, x + bw, y + bh);
            return;
        }
        content.layout(0, 0, full, fullHt);
        // Scale evenly (never stretched) to fit the card, centred in it.
        float s = Math.min(bw / (float) Math.max(1, full), bh / (float) Math.max(1, fullHt));
        content.setPivotX(0);
        content.setPivotY(0);
        content.setScaleX(s);
        content.setScaleY(s);
        content.setTranslationX(x + (bw - full * s) / 2f);
        content.setTranslationY(y + (bh - fullHt * s) / 2f);
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (gesture.isEditing()) ResizeGesture.drawEditFrame(c, box, radius, d);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        // The frame covers the whole dashboard: touches outside the card go to what is under it, unless
        // this card is being edited.
        if (!gesture.isEditing() && e.getActionMasked() == MotionEvent.ACTION_DOWN
                && !box.contains(e.getX(), e.getY())) return false;
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return gesture.isEditing();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth(), h = getHeight();
        return gesture.onTouch(e, w * fullW, h * fullH, w, h);
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
    public float boxX() {
        return getWidth() > 0 ? box.left / getWidth() : posX;
    }

    @Override
    public float boxY() {
        return getHeight() > 0 ? box.top / getHeight() : posY;
    }

    float posX() {
        return boxX();
    }

    float posY() {
        return boxY();
    }

    @Override
    public void setBox(float width, float height) {
        setCard(width, height, boxX(), boxY());
        if (listener != null) listener.onCard(this, false);
    }

    @Override
    public void setPos(float x, float y) {
        // Kept on the dashboard here too, so the saved place is where it shows.
        float maxX = 1f - Math.min(1f, fullW * cardW), maxY = 1f - Math.min(1f, fullH * cardH);
        setCard(cardW, cardH, Math.max(0f, Math.min(maxX, x)), Math.max(0f, Math.min(maxY, y)));
        if (listener != null) listener.onCard(this, false);
    }

    @Override
    public boolean onHandle(float x, float y) {
        float r = ResizeGesture.handleRadius(d) * 2.4f;   // generous: easy to hit with a finger
        float hx = box.right - ResizeGesture.handleRadius(d) * 0.6f, hy = box.bottom - ResizeGesture.handleRadius(d) * 0.6f;
        return Math.hypot(x - hx, y - hy) <= r;
    }

    @Override
    public void onEditing(boolean editing) {
        if (editing) bringToFront();
        if (listener != null) {
            listener.onEditing(editing);
            if (!editing) listener.onCard(this, true);
        }
    }
}
