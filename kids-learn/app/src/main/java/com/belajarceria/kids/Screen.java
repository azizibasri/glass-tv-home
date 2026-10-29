package com.belajarceria.kids;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One full screen of the app. {@link MainActivity} keeps them in a stack and animates between them. */
abstract class Screen extends FrameLayout {
    final MainActivity act;

    Screen(MainActivity a) {
        super(a);
        act = a;
        Ui.noClip(this);
    }

    /** The background colours while this screen is in front. */
    abstract int[] palette();

    void onShown() {}

    void onHidden() {}

    /** The top bar: a round back button and a title pill. More can be added to its right. */
    LinearLayout header(String title, int accent) {
        LinearLayout bar = new LinearLayout(getContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(4));
        Ui.noClip(bar);
        bar.addView(Ui.chevronButton(getContext(), true, accent, act::back),
                new LinearLayout.LayoutParams(Ui.dp(52), Ui.dp(52)));
        TextView t = Ui.text(getContext(), title, 22, Ui.INK, Ui.BLACK);
        t.setBackground(Ui.round(Ui.GLASS, Ui.dp(26)));
        t.setPadding(Ui.dp(24), 0, Ui.dp(24), 0);
        Ui.lift(t, 4, accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(52));
        lp.leftMargin = Ui.dp(12);
        bar.addView(t, lp);
        return bar;
    }

    /** A flexible gap that pushes what follows to the right end of a bar. */
    static void addSpacer(LinearLayout bar) {
        bar.addView(new android.view.View(bar.getContext()), new LinearLayout.LayoutParams(0, 1, 1f));
    }
}
