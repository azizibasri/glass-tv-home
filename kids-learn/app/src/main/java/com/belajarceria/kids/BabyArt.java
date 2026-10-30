package com.belajarceria.kids;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * The Kawan Comel babies: four sitting babies cut out of the person's own artwork (res/drawable-nodpi/baby1-4).
 * Drawn from their bottom centre, so they can squash, tilt and bounce like they are really sitting there.
 */
final class BabyArt {
    static final int COUNT = 4;
    private static final int[] RES = {R.drawable.baby1, R.drawable.baby2, R.drawable.baby3, R.drawable.baby4};
    private static final Bitmap[] CACHE = new Bitmap[COUNT];
    private static final Paint PAINT = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final Matrix M = new Matrix();

    private BabyArt() {}

    static Bitmap get(Context c, int i) {
        if (CACHE[i] == null) CACHE[i] = BitmapFactory.decodeResource(c.getResources(), RES[i]);
        return CACHE[i];
    }

    /**
     * Draws baby i with its bottom centre at (cx, bottom), h pixels tall.
     * @param tilt degrees, around the bottom centre
     * @param squash 1 is normal; above 1 is squashed down and wider, below 1 stretched up
     */
    static void draw(Canvas c, Context ctx, int i, float cx, float bottom, float h, float tilt, float squash) {
        Bitmap b = get(ctx, i);
        float s = h / b.getHeight();
        M.reset();
        M.postTranslate(-b.getWidth() / 2f, -b.getHeight());
        M.postScale(s * squash, s / squash);
        M.postRotate(tilt);
        M.postTranslate(cx, bottom);
        c.drawBitmap(b, M, PAINT);
    }

    /** A speech bubble with a tail pointing down at (x, y). */
    static void bubble(Canvas c, String text, float x, float y, float textSize, Paint fill, Paint ink) {
        ink.setTextSize(textSize);
        ink.setTextAlign(Paint.Align.CENTER);
        float w = ink.measureText(text) + textSize * 1.2f, h = textSize * 1.9f;
        RectF r = new RectF(x - w / 2, y - h - textSize * 0.6f, x + w / 2, y - textSize * 0.6f);
        fill.setColor(0xF7FFFFFF);
        c.drawRoundRect(r, h / 2, h / 2, fill);
        android.graphics.Path tail = new android.graphics.Path();
        tail.moveTo(x - textSize * 0.4f, r.bottom - 1);
        tail.lineTo(x, y);
        tail.lineTo(x + textSize * 0.4f, r.bottom - 1);
        tail.close();
        c.drawPath(tail, fill);
        c.drawText(text, x, r.centerY() + textSize * 0.36f, ink);
    }
}
