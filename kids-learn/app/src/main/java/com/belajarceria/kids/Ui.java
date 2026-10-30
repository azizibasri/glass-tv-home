package com.belajarceria.kids;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/** Small building blocks shared by every screen: sizes, glass cards, text, springy buttons. */
final class Ui {
    static final int INK = 0xFF2B2D42;
    static final int MUTED = 0xFF7A7F99;
    static final int GLASS = 0xEBFFFFFF;
    static final int WHITE = 0xFFFFFFFF;

    static float density;
    static Typeface BLACK, BOLD;

    private Ui() {}

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        BLACK = Typeface.create("sans-serif-black", Typeface.NORMAL);
        BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    /** The shorter side of the screen in pixels: everything is sized from it, so phones and tablets both fit. */
    static int shortSide(Context c) {
        android.util.DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return Math.min(dm.widthPixels, dm.heightPixels);
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    static GradientDrawable gradient(int[] colors, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setCornerRadius(radius);
        return g;
    }

    static GradientDrawable oval(int[] colors) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** The glossy highlight on the top half of a coloured card. */
    static GradientDrawable shine(float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55FFFFFF, 0x00FFFFFF});
        g.setCornerRadius(radius);
        return g;
    }

    /** A soft shadow, tinted with the card's own colour on Android 9+. */
    static void lift(View v, float elevationDp, int shadowColor) {
        v.setElevation(dp(elevationDp));
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineAmbientShadowColor(shadowColor);
            v.setOutlineSpotShadowColor(shadowColor);
        }
    }

    static void noClip(ViewGroup g) {
        g.setClipChildren(false);
        g.setClipToPadding(false);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** Text in a fixed-size box that shrinks to fit: long Arabic phrases and big letters use the same views. */
    static TextView fit(Context c, CharSequence s, int maxSp, int color, Typeface tf) {
        TextView t = text(c, s, maxSp, color, tf);
        t.setAutoSizeTextTypeUniformWithConfiguration(8, Math.max(9, maxSp), 1, TypedValue.COMPLEX_UNIT_SP);
        return t;
    }

    /** Shows a picture: an emoji as text, or a drawing for words that have no emoji. */
    static void picture(TextView t, String pic) {
        if (Content.XYLOPHONE.equals(pic)) {
            t.setText("");
            t.setBackground(new XylophoneDrawable());
        } else {
            t.setText(pic == null ? "" : pic);
            t.setBackground(null);
        }
    }

    /** Paints the text with a colour gradient instead of a flat colour. */
    static void gradientText(TextView t, int[] colors) {
        t.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol && b - top == ob - ot && t.getPaint().getShader() != null) return;
            t.getPaint().setShader(new LinearGradient(0, 0, r - l, b - top, colors, null, Shader.TileMode.CLAMP));
            t.invalidate();
        });
    }

    /** Presses in with a spring and a tick, pops back out, then acts: the touch feel of every button. */
    @SuppressLint("ClickableViewAccessibility")
    static void press(View v, Runnable action) {
        v.setOnTouchListener((view, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    Spring.scale(view, 0.9f, 700f, 0.7f);
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    return true;
                case MotionEvent.ACTION_UP:
                    Spring.scale(view, 1f, 420f, 0.42f);
                    if (e.getX() >= 0 && e.getY() >= 0 && e.getX() <= view.getWidth() && e.getY() <= view.getHeight()) {
                        Sfx.pop();
                        action.run();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    Spring.scale(view, 1f, 420f, 0.7f);
                    return true;
                default:
                    return true;
            }
        });
        // Switch Access and TalkBack still get a plain click.
        v.setOnClickListener(x -> action.run());
    }

    /** A wobble of joy: the view swells and settles like jelly. */
    static void jelly(View v, float amount) {
        Spring.of(v, View.SCALE_X).config(300f, 0.3f).kick(amount).to(1f);
        Spring.of(v, View.SCALE_Y).config(300f, 0.3f).kick(amount * 0.8f).to(1f);
    }

    /** "Not quite": a quick side-to-side shake. */
    static void shake(View v) {
        Spring.of(v, View.TRANSLATION_X).config(900f, 0.15f).kick(dp(700)).to(0f);
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /** Cards float in one after another from below, springing into place. */
    static void enter(List<View> views, long startDelay) {
        for (int i = 0; i < views.size(); i++) {
            final View c = views.get(i);
            c.setTranslationY(dp(110));
            c.setAlpha(0f);
            c.setScaleX(0.7f);
            c.setScaleY(0.7f);
            c.postDelayed(() -> {
                Spring.of(c, View.TRANSLATION_Y).config(220f, 0.62f).to(0f);
                Spring.of(c, View.ALPHA).config(220f, 1f).to(1f);
                scaleTo(c);
            }, startDelay + i * 75L);
        }
    }

    private static void scaleTo(View c) {
        Spring.scale(c, 1f, 220f, 0.58f);
    }

    /** A round glass button with a drawn chevron. */
    static View chevronButton(Context c, boolean left, int color, Runnable action) {
        IconView v = new IconView(c, left, color);
        v.setBackground(round(GLASS, dp(40)));
        lift(v, 6, color);
        v.setContentDescription(left ? "Back" : "Next");
        press(v, action);
        return v;
    }

    /** A round glass button with an emoji on it. */
    static TextView emojiButton(Context c, String emoji, int color, Runnable action) {
        TextView t = text(c, emoji, 24, INK, null);
        t.setBackground(round(GLASS, dp(40)));
        lift(t, 6, color);
        press(t, action);
        return t;
    }
}
