package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

/** One hero, animating in place: the mascot in a game's top bar and on the home screen's cards. */
final class HeroView extends View {
    private final HeroArt.Hero hero;
    private final int pose;
    private final long start = SystemClock.uptimeMillis() - (long) (Math.random() * 4000);

    HeroView(Context c, HeroArt.Hero hero, int pose) {
        super(c);
        this.hero = hero;
        this.pose = pose;
        setContentDescription(hero.name);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth() / 200f, getHeight() / 230f);
        c.save();
        c.translate((getWidth() - 200 * s) / 2f, (getHeight() - 230 * s) / 2f);
        c.scale(s, s);
        HeroArt.draw(c, hero, pose, (SystemClock.uptimeMillis() - start) / 1000f);
        c.restore();
        if (isShown()) postInvalidateOnAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidate();
    }
}
