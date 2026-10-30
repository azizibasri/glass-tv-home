package com.belajarceria.kids;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** One subject's four ways to play: Learn, Draw, Drag and Match. */
final class SubjectScreen extends Screen {
    private static final String[][] ACTIVITIES = {
            {"👀", "Learn", "Look & listen"},
            {"✏️", "Draw", "Trace with a finger"},
            {"🧩", "Drag", "Drag & drop"},
            {"🔗", "Match", "Find the pairs"},
    };

    private final Content.Subject s;
    private final ArrayList<View> tiles = new ArrayList<>();
    private boolean entered;

    SubjectScreen(MainActivity a, Content.Subject s) {
        super(a);
        this.s = s;
        int tileH = (int) Math.min(Math.max(Ui.shortSide(a) * 0.52f, Ui.dp(160)), Ui.dp(290));
        int tileW = (int) (tileH * 0.8f);

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        Ui.noClip(col);
        col.addView(header(s.title + "  ·  " + s.subtitle, s),
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        HorizontalScrollView sv = new HorizontalScrollView(a);
        sv.setFillViewport(true);
        sv.setHorizontalScrollBarEnabled(false);
        Ui.noClip(sv);
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER);
        row.setPadding(Ui.dp(24), Ui.dp(16), Ui.dp(24), Ui.dp(24));
        Ui.noClip(row);
        for (int i = 0; i < ACTIVITIES.length; i++) {
            View t = tile(i, tileW, tileH);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.leftMargin = lp.rightMargin = Ui.dp(10);
            row.addView(t, lp);
            tiles.add(t);
        }
        sv.addView(row, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL));
        col.addView(sv, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private View tile(int which, int w, int h) {
        String[] info = ACTIVITIES[which];
        LinearLayout tile = new LinearLayout(getContext());
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setPadding(Ui.dp(10), (int) (h * 0.1f), Ui.dp(10), 0);
        tile.setBackground(Ui.round(Ui.GLASS, w * 0.18f));
        Ui.lift(tile, 10, s.colors[0]);
        tile.setContentDescription(info[1]);

        int circle = (int) (w * 0.56f);
        FrameLayout bubble = new FrameLayout(getContext());
        bubble.setBackground(Ui.oval(s.colors));
        bubble.setForeground(Ui.shine(circle));
        Ui.lift(bubble, 6, s.colors[0]);
        TextView e = Ui.fit(getContext(), info[0], 54, Ui.INK, null);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        ep.setMargins(circle / 6, circle / 6, circle / 6, circle / 6);
        bubble.addView(e, ep);
        tile.addView(bubble, new LinearLayout.LayoutParams(circle, circle));

        TextView t = Ui.fit(getContext(), info[1], 24, Ui.INK, Ui.BLACK);
        t.setMaxLines(1);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(34));
        tp.topMargin = (int) (h * 0.07f);
        tile.addView(t, tp);
        TextView st = Ui.fit(getContext(), info[2], 14, Ui.MUTED, Ui.BOLD);
        st.setMaxLines(1);
        tile.addView(st, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(20)));

        Ui.press(tile, () -> act.push(activity(act, s, which)));
        return tile;
    }

    static Screen activity(MainActivity a, Content.Subject s, int which) {
        switch (which) {
            case 0: return new LearnScreen(a, s);
            case 1: return new TraceScreen(a, s);
            case 2: return new DragScreen(a, s);
            default: return new MatchScreen(a, s);
        }
    }

    @Override
    int[] palette() {
        return s.palette();
    }

    @Override
    void onShown() {
        if (!entered) {
            entered = true;
            Ui.enter(tiles, 80);
        }
    }
}
