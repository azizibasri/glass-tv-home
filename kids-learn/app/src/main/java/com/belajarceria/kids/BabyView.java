package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

/** One baby, sitting and wobbling happily: the mascot in a game's top bar and on the home screen's cards. */
final class BabyView extends View {
    private final int baby;
    private final long start = SystemClock.uptimeMillis() - (long) (Math.random() * 5000);

    BabyView(Context c, int baby) {
        super(c);
        this.baby = baby;
        setContentDescription("baby " + (baby + 1));
    }

    @Override
    protected void onDraw(Canvas c) {
        float t = (SystemClock.uptimeMillis() - start) / 1000f;
        float h = getHeight() * 0.92f;
        // A gentle side-to-side rock, and every few seconds a happy little bounce.
        float rock = (float) Math.sin(t * 2.2f) * 5f;
        float hopPhase = t % 4f;
        float hop = hopPhase < 0.5f ? (float) Math.sin(hopPhase / 0.5f * Math.PI) : 0f;
        float squash = 1f + 0.05f * (float) Math.sin(t * 4.4f) - 0.06f * hop;
        BabyArt.draw(c, getContext(), baby, getWidth() / 2f, getHeight() - hop * getHeight() * 0.08f, h, rock, squash);
        if (isShown()) postInvalidateOnAnimation();
    }
}
