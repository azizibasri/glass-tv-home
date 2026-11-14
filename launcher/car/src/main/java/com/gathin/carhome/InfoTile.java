package com.gathin.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * A small car-info tile in the dark glass style of the music widget: an icon, a label ("COOLANT"), the value
 * in large digits ("90 °C"), a word for the state ("Normal"), and a thin bar coloured by that state.
 */
final class InfoTile extends View {
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF(), lit = new RectF();
    private final Drawable icon;
    private final String title;
    private String text = "--", state = "No data";
    private float fraction;
    private final float d;

    InfoTile(Context ctx, int iconRes, String title) {
        super(ctx);
        this.title = title;
        d = getResources().getDisplayMetrics().density;
        icon = ctx.getDrawable(iconRes);
        setBackground(NowPlaying.darkPanel(ctx));
        label.setColor(0xA6FFFFFF);
        label.setLetterSpacing(0.12f);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setColor(0xFFFFFFFF);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        note.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        note.setTextAlign(Paint.Align.RIGHT);
        track.setColor(0x26FFFFFF);
        fill.setColor(0xFF30D158);
    }

    /** Shows {@code text}; {@code fraction} fills the bar in {@code color}; NaN fraction = no data. */
    void set(String text, float fraction, int color, String state) {
        this.text = text;
        this.fraction = Float.isNaN(fraction) ? 0f : fraction;
        this.state = state;
        fill.setColor(color);
        note.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float pad = Math.min(14 * d, h * 0.18f);
        float iconSize = Math.min(h * 0.42f, 30 * d);
        if (icon != null) {
            int top = Math.round(pad);
            icon.setBounds(Math.round(pad), top, Math.round(pad + iconSize), Math.round(top + iconSize));
            icon.draw(c);
        }
        float x = pad + iconSize + pad * 0.8f;
        label.setTextSize(Math.max(9 * d, h * 0.14f));
        value.setTextSize(Math.max(16 * d, h * 0.30f));
        note.setTextSize(Math.max(10 * d, h * 0.15f));
        float labelY = pad - label.ascent();
        c.drawText(title, x, labelY, label);
        float valueY = labelY + value.getTextSize() * 1.05f;
        c.drawText(text, x, valueY, value);
        // The state word goes top right; on a narrow tile, next to the value instead; if neither fits, the
        // coloured bar still tells.
        float gap = 8 * d, stateW = note.measureText(state);
        if (x + label.measureText(title) + gap + stateW <= w - pad) {
            c.drawText(state, w - pad, labelY, note);
        } else if (x + value.measureText(text) + gap + stateW <= w - pad) {
            c.drawText(state, w - pad, valueY, note);
        }
        float bh = Math.max(3 * d, h * 0.06f);
        bar.set(x, h - pad - bh, w - pad, h - pad);
        c.drawRoundRect(bar, bh / 2, bh / 2, track);
        if (fraction > 0.005f) {
            lit.set(bar.left, bar.top, bar.left + Math.max(bh, bar.width() * fraction), bar.bottom);
            c.drawRoundRect(lit, bh / 2, bh / 2, fill);
        }
    }
}
