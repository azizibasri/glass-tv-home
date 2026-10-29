package com.belajarceria.kids;

import android.animation.TimeAnimator;
import android.util.Property;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Damped spring physics, the feel of iOS animations: no fixed duration, and a new target keeps the current
 * velocity, so a quick tap, a fling and a slow drag all settle naturally and can be interrupted at any time.
 */
final class Spring implements TimeAnimator.TimeListener {
    private static final Map<View, Map<Property<View, Float>, Spring>> ALL = new WeakHashMap<>();

    /** The one spring that drives this property of this view (created on first use). */
    static Spring of(View v, Property<View, Float> p) {
        Map<Property<View, Float>, Spring> m = ALL.get(v);
        if (m == null) {
            m = new HashMap<>();
            ALL.put(v, m);
        }
        Spring s = m.get(p);
        if (s == null) {
            s = new Spring(v, p);
            m.put(p, s);
        }
        return s;
    }

    static void scale(View v, float to, float stiffness, float damping) {
        of(v, View.SCALE_X).config(stiffness, damping).to(to);
        of(v, View.SCALE_Y).config(stiffness, damping).to(to);
    }

    static void scale(View v, float to) {
        scale(v, to, 500f, 0.55f);
    }

    static void move(View v, float x, float y, float stiffness, float damping) {
        of(v, View.TRANSLATION_X).config(stiffness, damping).to(x);
        of(v, View.TRANSLATION_Y).config(stiffness, damping).to(y);
    }

    /** Stops every spring on the view where it is, e.g. when a finger grabs it. */
    static void stop(View v) {
        Map<Property<View, Float>, Spring> m = ALL.get(v);
        if (m == null) return;
        for (Spring s : m.values()) s.cancel();
    }

    private final WeakReference<View> view;
    private final Property<View, Float> prop;
    private final float epsilon;
    private float stiffness = 400f, damping = 0.7f;
    private float value, velocity, target;
    private TimeAnimator animator;
    private Runnable pendingEnd, onEnd;

    private Spring(View v, Property<View, Float> p) {
        view = new WeakReference<>(v);
        prop = p;
        if (p == View.SCALE_X || p == View.SCALE_Y || p == View.ALPHA) epsilon = 0.002f;
        else if (p == View.ROTATION) epsilon = 0.05f;
        else epsilon = 0.4f;
    }

    /** @param damping 1 settles without overshoot, lower values bounce. */
    Spring config(float stiffness, float damping) {
        this.stiffness = stiffness;
        this.damping = damping;
        return this;
    }

    /** Adds velocity (units per second), for a fling or a jelly bounce. */
    Spring kick(float v) {
        velocity += v;
        return this;
    }

    /** Runs once this movement settles; a later {@link #to} without it replaces it. */
    Spring then(Runnable r) {
        pendingEnd = r;
        return this;
    }

    Spring to(float t) {
        View v = view.get();
        if (v == null) return this;
        target = t;
        onEnd = pendingEnd;
        pendingEnd = null;
        if (animator == null) {
            value = prop.get(v);
            animator = new TimeAnimator();
            animator.setTimeListener(this);
            animator.start();
        }
        return this;
    }

    void cancel() {
        if (animator != null) {
            animator.setTimeListener(null);
            animator.cancel();
            animator = null;
        }
        velocity = 0f;
        onEnd = null;
        pendingEnd = null;
    }

    @Override
    public void onTimeUpdate(TimeAnimator a, long totalTime, long deltaTime) {
        View v = view.get();
        if (v == null) {
            cancel();
            return;
        }
        float dt = Math.min(deltaTime, 48L) / 1000f;
        float omega = (float) Math.sqrt(stiffness);
        float c = 2f * damping * omega;
        int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
        float h = dt / steps;
        for (int i = 0; i < steps; i++) {
            float acc = -stiffness * (value - target) - c * velocity;
            velocity += acc * h;
            value += velocity * h;
        }
        if (Math.abs(value - target) < epsilon && Math.abs(velocity) < epsilon * 10f) {
            prop.set(v, target);
            Runnable end = onEnd;
            cancel();
            if (end != null) end.run();
            return;
        }
        prop.set(v, value);
    }
}
