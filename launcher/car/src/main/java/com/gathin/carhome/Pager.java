package com.gathin.carhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

/** Pages of app tiles side by side; swipe left or right to move one page, like a phone home screen. */
final class Pager extends ViewGroup {
    interface Listener {
        void onPage(int page, int count);
    }

    private final int touchSlop, minFling;
    private VelocityTracker velocity;
    private float downX, downY, startScroll;
    private boolean dragging;
    private int page;
    private ValueAnimator settle;
    private Listener listener;

    Pager(Context ctx) {
        super(ctx);
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 4;
        setClipChildren(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int page() {
        return page;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            c.layout(i * w, 0, (i + 1) * w, b - t);
        }
        page = Math.min(page, Math.max(0, getChildCount() - 1));
        if (settle == null || !settle.isRunning()) scrollTo(page * w, 0);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                startScroll = getScrollX();
                dragging = settle != null && settle.isRunning();
                if (dragging) settle.cancel();
                track(e);
                return dragging;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                if (dx > touchSlop && dx > dy) {
                    dragging = true;
                    downX = e.getX();
                    startScroll = getScrollX();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                track(e);
                return dragging;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        track(e);
        int w = getWidth();
        int max = Math.max(0, (getChildCount() - 1) * w);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                return true;
            case MotionEvent.ACTION_MOVE: {
                float x = startScroll - (e.getX() - downX);
                // A little give past the first and last page.
                if (x < 0) x = x / 3f;
                else if (x > max) x = max + (x - max) / 3f;
                scrollTo(Math.round(x), 0);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                int target = page;
                if (w > 0) {
                    velocity.computeCurrentVelocity(1000);
                    float vx = velocity.getXVelocity();
                    if (vx < -minFling) target = page + 1;
                    else if (vx > minFling) target = page - 1;
                    else target = Math.round(getScrollX() / (float) w);
                }
                velocity.recycle();
                velocity = null;
                dragging = false;
                snapTo(target);
                return true;
            }
            default:
                return true;
        }
    }

    void snapTo(int target) {
        int count = getChildCount();
        target = Math.max(0, Math.min(count - 1, target));
        page = target;
        if (settle != null) settle.cancel();
        settle = ValueAnimator.ofInt(getScrollX(), target * getWidth());
        settle.setDuration(280);
        settle.setInterpolator(new DecelerateInterpolator(1.6f));
        settle.addUpdateListener(a -> scrollTo((int) a.getAnimatedValue(), 0));
        settle.start();
        if (listener != null) listener.onPage(page, count);
    }

    private void track(MotionEvent e) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        else if (e.getActionMasked() == MotionEvent.ACTION_DOWN) velocity.clear();
        velocity.addMovement(e);
    }
}
