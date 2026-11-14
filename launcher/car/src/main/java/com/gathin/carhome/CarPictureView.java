package com.gathin.carhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import java.io.File;

/**
 * The car under the dials: the owner's own picture of the car (picked from the gallery, kept on the unit as
 * {@link #PICTURE_FILE}) and under it the door status: which doors, boot or bonnet are open, or "All doors
 * closed". Redraws only when the picture or the status changes.
 */
final class CarPictureView extends View {
    static final String PICTURE_FILE = "car_picture.jpg";

    private final float d;
    private final Paint picPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();
    private final String[] names;
    private Bitmap picture;
    private boolean anyOpen, hasData;
    private String status;

    CarPictureView(Context ctx) {
        super(ctx);
        d = getResources().getDisplayMetrics().density;
        names = new String[]{"", ctx.getString(R.string.door_fl), ctx.getString(R.string.door_fr),
                ctx.getString(R.string.door_rl), ctx.getString(R.string.door_rr), ctx.getString(R.string.door_trunk),
                ctx.getString(R.string.door_hood)};
        hint.setColor(0x99FFFFFF);
        hint.setTextSize(12 * d);
        hint.setTextAlign(Paint.Align.CENTER);
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(Math.max(1f, d));
        frame.setColor(0x33FFFFFF);
        label.setTextSize(13 * d);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        status = ctx.getString(R.string.doors_no_data);
        setContentDescription(status);
        loadPicture();
    }

    static File pictureFile(Context ctx) {
        File dir = CarConfig.dir(ctx);
        File f = dir == null ? null : new File(dir, PICTURE_FILE);
        return f != null && f.isFile() ? f : null;
    }

    /** Loads the car picture off the main thread, at most about 1200 pixels wide. */
    void loadPicture() {
        File f = pictureFile(getContext());
        if (f == null) {
            setPicture(null);
            return;
        }
        new Thread(() -> {
            Bitmap b = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                o.inSampleSize = 1;
                while (o.outWidth / o.inSampleSize > 1200) o.inSampleSize *= 2;
                o.inJustDecodeBounds = false;
                b = BitmapFactory.decodeFile(f.getPath(), o);
            } catch (OutOfMemoryError | RuntimeException ignored) {
                // Not a picture or too big: the hint shows instead.
            }
            Bitmap done = b;
            post(() -> setPicture(done));
        }, "car-picture").start();
    }

    private void setPicture(Bitmap b) {
        if (picture != null && picture != b) picture.recycle();
        picture = b;
        picPaint.setShader(b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        invalidate();
    }

    /** Which openings are open (index = Doors constant), or {@code known} false when there is no door data. */
    void setDoors(boolean[] open, boolean known) {
        String s = Doors.status(getContext().getString(R.string.door_open), getContext().getString(R.string.doors_closed),
                getContext().getString(R.string.doors_no_data), names, open, known);
        boolean any = false;
        if (known && open != null) for (int p = 1; p < Doors.COUNT; p++) any |= open[p];
        if (s.equals(status) && any == anyOpen && known == hasData) return;
        status = s;
        anyOpen = any;
        hasData = known;
        setContentDescription(s);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float labelH = 26 * d, area = h - labelH - 6 * d;
        rect.set(0, 0, w, area);
        float r = 14 * d;
        if (picture != null) {
            // The whole picture, as large as fits, centred, with rounded corners.
            float scale = Math.min(w / (float) picture.getWidth(), area / picture.getHeight());
            float pw = picture.getWidth() * scale, ph = picture.getHeight() * scale;
            rect.set(w / 2f - pw / 2, area / 2f - ph / 2, w / 2f + pw / 2, area / 2f + ph / 2);
            matrix.setScale(scale, scale);
            matrix.postTranslate(rect.left, rect.top);
            picPaint.getShader().setLocalMatrix(matrix);
            c.drawRoundRect(rect, r, r, picPaint);
        } else {
            c.drawRoundRect(rect, r, r, frame);
            c.drawText(getContext().getString(R.string.car_picture_hint), w / 2f,
                    area / 2f - (hint.ascent() + hint.descent()) / 2, hint);
        }
        // Door status.
        float tw = Math.min(w, label.measureText(status) + 28 * d);
        rect.set(w / 2f - tw / 2, h - labelH, w / 2f + tw / 2, h - 2 * d);
        pill.setColor(anyOpen ? 0xE6FF9F0A : hasData ? 0x5930D158 : 0x33FFFFFF);
        c.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, pill);
        label.setColor(anyOpen ? 0xFF1C1C1E : 0xFFFFFFFF);
        c.drawText(status, w / 2f, rect.centerY() - (label.ascent() + label.descent()) / 2, label);
    }
}
