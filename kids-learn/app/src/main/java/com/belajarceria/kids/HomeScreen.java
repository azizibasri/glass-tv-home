package com.belajarceria.kids;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** The first screen: the app's name and one big glossy card per subject. */
final class HomeScreen extends Screen {
    private final ArrayList<View> cards = new ArrayList<>();
    private final ArrayList<View> icons = new ArrayList<>();
    private final ArrayList<Animator> bobs = new ArrayList<>();
    private boolean entered;

    HomeScreen(MainActivity a) {
        super(a);
        int cardH = (int) Math.min(Math.max(Ui.shortSide(a) * 0.5f, Ui.dp(170)), Ui.dp(300));
        int cardW = (int) (cardH * 0.8f);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        Ui.noClip(col);

        TextView title = Ui.text(a, "Belajar Ceria", 40, Ui.INK, Ui.BLACK);
        Ui.gradientText(title, new int[]{0xFFFF4F86, 0xFF8A5CFF, 0xFF3FA8FF});
        col.addView(title, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView sub = Ui.text(a, "Let's learn and play!  🌈", 18, Ui.MUTED, Ui.BOLD);
        col.addView(sub, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setFillViewport(true);
        sv.setHorizontalScrollBarEnabled(false);
        Ui.noClip(sv);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        row.setPadding(Ui.dp(24), Ui.dp(22), Ui.dp(24), Ui.dp(30));
        Ui.noClip(row);
        for (Content.Subject s : Content.SUBJECTS) {
            View c = card(s, cardW, cardH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
            lp.leftMargin = lp.rightMargin = Ui.dp(10);
            row.addView(c, lp);
            cards.add(c);
        }
        sv.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        col.addView(sv, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private View card(Content.Subject s, int w, int h) {
        FrameLayout card = new FrameLayout(getContext());
        float r = w * 0.18f;
        card.setBackground(Ui.gradient(s.colors, r));
        card.setForeground(Ui.shine(r));
        Ui.lift(card, 12, s.colors[0]);
        card.setContentDescription(s.title);

        TextView icon = Ui.fit(getContext(), s.icon, 64, Ui.WHITE, Ui.BLACK);
        icon.setMaxLines(1);
        icon.setShadowLayer(Ui.dp(8), 0, Ui.dp(3), 0x33000000);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, (int) (h * 0.46f), Gravity.TOP);
        ip.topMargin = (int) (h * 0.1f);
        ip.leftMargin = ip.rightMargin = Ui.dp(14);
        card.addView(icon, ip);
        icons.add(icon);

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.fit(getContext(), s.title, 22, Ui.WHITE, Ui.BLACK);
        t.setMaxLines(1);
        labels.addView(t, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(30)));
        TextView st = Ui.fit(getContext(), s.subtitle, 14, 0xEEFFFFFF, Ui.BOLD);
        st.setMaxLines(1);
        labels.addView(st, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(20)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        lp.bottomMargin = (int) (h * 0.09f);
        lp.leftMargin = lp.rightMargin = Ui.dp(10);
        card.addView(labels, lp);

        Ui.press(card, () -> act.push(new SubjectScreen(act, s)));
        return card;
    }

    @Override
    int[] palette() {
        return Content.HOME;
    }

    @Override
    void onShown() {
        if (!entered) {
            entered = true;
            Ui.enter(cards, 150);
        }
        // The pictures on the cards bob gently, each at its own pace, so the screen feels alive.
        for (int i = 0; i < icons.size(); i++) {
            ObjectAnimator a = ObjectAnimator.ofFloat(icons.get(i), View.TRANSLATION_Y, -Ui.dp(5), Ui.dp(5));
            a.setDuration(1300 + i * 170L);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setInterpolator(new AccelerateDecelerateInterpolator());
            a.start();
            bobs.add(a);
        }
    }

    @Override
    void onHidden() {
        for (Animator a : bobs) a.cancel();
        bobs.clear();
    }
}
