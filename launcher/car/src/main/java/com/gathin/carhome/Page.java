package com.gathin.carhome;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** One page of the grid: equal cells, {@code columns} by {@code rows}, filled left to right. */
final class Page extends ViewGroup {
    private final int columns, rows;

    Page(Context ctx, int columns, int rows) {
        super(ctx);
        this.columns = columns;
        this.rows = rows;
        setClipChildren(false);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        int cw = w / columns, ch = h / rows;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cw = (r - l) / columns, ch = (b - t) / rows;
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            int col = i % columns, row = i / columns;
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) col = columns - 1 - col;
            c.layout(col * cw, row * ch, (col + 1) * cw, (row + 1) * ch);
        }
    }
}
