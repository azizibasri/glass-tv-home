package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A crisp rounded chevron (‹ or ›), drawn rather than typed so it sits exactly in the middle of its button. */
final class IconView extends View {
    private final boolean left;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    IconView(Context c, boolean left, int color) {
        super(c);
        this.left = left;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight());
        float s = size * 0.17f;
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dir = left ? 1f : -1f;
        paint.setStrokeWidth(size * 0.09f);
        path.reset();
        path.moveTo(cx + dir * s * 0.5f, cy - s);
        path.lineTo(cx - dir * s * 0.5f, cy);
        path.lineTo(cx + dir * s * 0.5f, cy + s);
        c.drawPath(path, paint);
    }
}
