package com.gathin.carhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import java.io.File;

/**
 * The picture behind everything, and a blurred copy of it that the glass panels show through. Made once
 * per screen size on a background thread: the sharp one at screen size, the blurred one at a tenth of it,
 * so the glass costs one small bitmap and no live blur on the head unit's GPU.
 */
final class Wallpaper {
    final Bitmap sharp, blurred;

    private Wallpaper(Bitmap sharp, Bitmap blurred) {
        this.sharp = sharp;
        this.blurred = blurred;
    }

    /**
     * Which picture at which size: the chosen wallpaper's size and date, or the built-in one. The finished
     * wallpaper is kept under this name, so the next start shows it at once instead of rebuilding it.
     */
    private static String key(Context ctx, CarConfig cfg, int w, int h) {
        File d = CarConfig.dir(ctx);
        File f = d == null ? null : new File(d, "wallpaper.jpg");
        String src = cfg.wallpaper && f != null && f.isFile() ? f.length() + "-" + f.lastModified() : "aurora1";
        return src + "-" + w + "x" + h;
    }

    private static File cacheDir(Context ctx) {
        // Not the app's cache folder: the Clean button empties that.
        File dir = new File(ctx.getFilesDir(), "wallpaper");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    /** The finished wallpaper saved last time, if it matches; quick enough for the main thread. Else null. */
    static Wallpaper cached(Context ctx, CarConfig cfg, int w, int h) {
        try {
            File dir = cacheDir(ctx);
            File keyFile = new File(dir, "key"), sharpFile = new File(dir, "sharp.jpg"), blurFile = new File(dir, "blur.png");
            if (!keyFile.isFile() || !sharpFile.isFile() || !blurFile.isFile()) return null;
            byte[] k = new byte[(int) Math.min(512, keyFile.length())];
            try (java.io.FileInputStream in = new java.io.FileInputStream(keyFile)) {
                int n = in.read(k);
                if (n <= 0 || !new String(k, 0, n, "UTF-8").equals(key(ctx, cfg, w, h))) return null;
            }
            Bitmap sharp = BitmapFactory.decodeFile(sharpFile.getPath());
            Bitmap blur = BitmapFactory.decodeFile(blurFile.getPath());
            if (sharp == null || blur == null || sharp.getWidth() != w || sharp.getHeight() != h) return null;
            return new Wallpaper(sharp, blur);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    /** The small blurred wallpaper saved last time (whatever size), for the window to show from its first frame. */
    static Bitmap lastBlur(Context ctx) {
        try {
            return BitmapFactory.decodeFile(new File(cacheDir(ctx), "blur.png").getPath());
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    static Wallpaper make(Context ctx, CarConfig cfg, int w, int h) {
        Bitmap sharp = null;
        File d = CarConfig.dir(ctx);
        File f = d == null ? null : new File(d, "wallpaper.jpg");
        if (cfg.wallpaper && f != null && f.isFile()) sharp = loadCropped(f, w, h);
        if (sharp == null) sharp = aurora(w, h);
        int bw = Math.max(16, w / 10), bh = Math.max(16, h / 10);
        Bitmap small = Bitmap.createScaledBitmap(sharp, bw, bh, true);
        if (small == sharp) small = sharp.copy(Bitmap.Config.ARGB_8888, true);
        boxBlur(small, 3);
        boxBlur(small, 3);
        boxBlur(small, 2);
        save(ctx, key(ctx, cfg, w, h), sharp, small);
        return new Wallpaper(sharp, small);
    }

    private static void save(Context ctx, String key, Bitmap sharp, Bitmap blur) {
        try {
            File dir = cacheDir(ctx);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, "sharp.jpg"))) {
                sharp.compress(Bitmap.CompressFormat.JPEG, 92, out);
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, "blur.png"))) {
                blur.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(dir, "key"))) {
                out.write(key.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {
            // Not saved: it is simply made again next time.
        }
    }

    /** The built-in background: deep night blue with soft colour glows, in the iOS dark palette. */
    static Bitmap aurora(int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF070B1E, 0xFF140A2E, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        glow(c, p, w * 0.18f, h * 0.15f, Math.max(w, h) * 0.55f, 0x990A84FF);
        glow(c, p, w * 0.85f, h * 0.25f, Math.max(w, h) * 0.45f, 0x88BF5AF2);
        glow(c, p, w * 0.60f, h * 1.05f, Math.max(w, h) * 0.50f, 0x7764D2FF);
        glow(c, p, w * 0.05f, h * 0.95f, Math.max(w, h) * 0.35f, 0x5530D158);
        // Darker at the bottom, so labels stay readable.
        p.setShader(new LinearGradient(0, h * 0.5f, 0, h, 0x00000000, 0x66000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return b;
    }

    private static void glow(Canvas c, Paint p, float x, float y, float r, int color) {
        p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    private static Bitmap loadCropped(File f, int w, int h) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap src = BitmapFactory.decodeFile(f.getPath(), o);
            if (src == null) return null;
            // Phone photos are often stored sideways with a note saying which way is up: follow it.
            int degrees = 0;
            try {
                int orientation = new android.media.ExifInterface(f.getPath()).getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
                degrees = orientation == android.media.ExifInterface.ORIENTATION_ROTATE_90 ? 90
                        : orientation == android.media.ExifInterface.ORIENTATION_ROTATE_180 ? 180
                        : orientation == android.media.ExifInterface.ORIENTATION_ROTATE_270 ? 270 : 0;
            } catch (java.io.IOException ignored) {
            }
            if (degrees != 0) {
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.postRotate(degrees);
                Bitmap turned = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
                if (turned != src) src.recycle();
                src = turned;
            }
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
            float sw = w / scale, sh = h / scale;
            float sx = (src.getWidth() - sw) / 2f, sy = (src.getHeight() - sh) / 2f;
            Rect from = new Rect(Math.round(sx), Math.round(sy), Math.round(sx + sw), Math.round(sy + sh));
            new Canvas(out).drawBitmap(src, from, new RectF(0, 0, w, h), new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();
            return out;
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    /** One separable box blur pass of radius {@code r}, in place. */
    static void boxBlur(Bitmap b, int r) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        pass(px, tmp, w, h, r, true);
        pass(tmp, px, w, h, r, false);
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h;
        int div = 2 * r + 1;
        for (int line = 0; line < lines; line++) {
            int ra = 0, rr = 0, rg = 0, rb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[idx(line, clamp(i, len), w, horizontal)];
                ra += c >>> 24; rr += (c >> 16) & 0xFF; rg += (c >> 8) & 0xFF; rb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[idx(line, i, w, horizontal)] = (ra / div) << 24 | (rr / div) << 16 | (rg / div) << 8 | (rb / div);
                int add = in[idx(line, clamp(i + r + 1, len), w, horizontal)];
                int sub = in[idx(line, clamp(i - r, len), w, horizontal)];
                ra += (add >>> 24) - (sub >>> 24);
                rr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                rg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                rb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }
}
