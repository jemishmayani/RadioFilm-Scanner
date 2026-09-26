package com.filmscan.app;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.os.Build;
import android.util.Log;
import com.filmscan.core.EdgeDetector;
import com.filmscan.core.EdgeMap;
import com.filmscan.core.Filters;
import com.filmscan.core.Geom;
import com.filmscan.core.PixelSource;

/** Decoding, perspective correction and rendering of pages. */
public final class Imaging {
    private Imaging() {}

    public static final int PREVIEW_SIDE = 2048;  // upright working copy for crop / edit
    public static final int EDIT_SIDE = 1800;     // corrected preview shown while editing

    // ------------------------------------------------------------------ decoding

    public static int readExif(String path) {
        try {
            return new ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (Throwable t) {
            return 1;
        }
    }

    public static int[] rawSize(String path) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        return new int[]{o.outWidth, o.outHeight};
    }

    /** Fills a new page's size and orientation from its file. Returns false if it is not an image. */
    public static boolean probe(Page p) {
        int[] s = rawSize(p.file);
        if (s[0] <= 0 || s[1] <= 0) return false;
        p.rawW = s[0]; p.rawH = s[1];
        p.exif = readExif(p.file);
        return true;
    }

    /** Upright (EXIF applied) bitmap with its long side at most maxSide. Cached. */
    public static Bitmap oriented(Page p, int maxSide) {
        String key = p.file + "@" + maxSide;
        Bitmap b = App.get().cached(key);
        if (b != null) return b;
        b = decodeOriented(p.file, p.exif, maxSide);
        App.get().cache(key, b);
        return b;
    }

    public static Bitmap decodeOriented(String path, int exif, int maxSide) {
        int[] s = rawSize(path);
        if (s[0] <= 0) return null;
        int sample = 1;
        while (Math.max(s[0], s[1]) / (sample * 2) >= maxSide) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = null;
        for (int attempt = 0; attempt < 3 && b == null; attempt++) {
            try {
                b = BitmapFactory.decodeFile(path, o);
            } catch (OutOfMemoryError e) {
                o.inSampleSize *= 2;
            }
        }
        if (b == null) return null;
        float sc = Math.min(1f, maxSide / (float) Math.max(b.getWidth(), b.getHeight()));
        Matrix m = new Matrix();
        m.setScale(sc, sc);
        switch (exif) {
            case 2: m.postScale(-1, 1); break;
            case 3: m.postRotate(180); break;
            case 4: m.postScale(1, -1); break;
            case 5: m.postRotate(90); m.postScale(-1, 1); break;
            case 6: m.postRotate(90); break;
            case 7: m.postRotate(-90); m.postScale(-1, 1); break;
            case 8: m.postRotate(-90); break;
            default: break;
        }
        if (m.isIdentity()) return b;
        Bitmap r = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        if (r != b) b.recycle();
        return r;
    }

    /** Maps a point of the upright image (display coords) to stored pixel coords. */
    public static float[] dispToRaw(float X, float Y, int exif, int W, int H) {
        switch (exif) {
            case 2: return new float[]{W - X, Y};
            case 3: return new float[]{W - X, H - Y};
            case 4: return new float[]{X, H - Y};
            case 5: return new float[]{Y, X};
            case 6: return new float[]{Y, H - X};
            case 7: return new float[]{W - Y, H - X};
            case 8: return new float[]{W - Y, X};
            default: return new float[]{X, Y};
        }
    }

    public static float[] quadPx(Page p, float w, float h) {
        if (p.quad == null) return Geom.fullQuad(w, h);
        return Geom.scale(p.quad, w, h);
    }

    // ------------------------------------------------------------------ detection

    /** Detects the film/paper outline in an upright bitmap. Returns normalized quad or null. */
    public static float[] detect(Bitmap upright) {
        int bw = upright.getWidth(), bh = upright.getHeight();
        float s = 560f / Math.max(bw, bh);
        int sw = Math.max(8, Math.round(bw * s)), sh = Math.max(8, Math.round(bh * s));
        Bitmap small = Bitmap.createScaledBitmap(upright, sw, sh, true);
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        if (small != upright) small.recycle();
        EdgeDetector.Result r = EdgeDetector.detect(px, sw, sh);
        if (r == null) return null;
        float[] q = Geom.scale(r.quad, 1f / sw, 1f / sh);
        // refine on a larger copy for sub-pixel accurate edges
        EdgeMap em = edgeMap(upright, 1024);
        float[] qe = Geom.scale(q, em.w, em.h);
        float R = Math.max(3f, Math.max(em.w, em.h) * 0.008f);
        qe = em.refine(qe, R);
        qe = em.refine(qe, R * 0.5f);
        return Geom.scale(qe, 1f / em.w, 1f / em.h);
    }

    public static EdgeMap edgeMap(Bitmap upright, int side) {
        int bw = upright.getWidth(), bh = upright.getHeight();
        float s = Math.min(1f, side / (float) Math.max(bw, bh));
        int w = Math.max(8, Math.round(bw * s)), h = Math.max(8, Math.round(bh * s));
        Bitmap b = s < 1f ? Bitmap.createScaledBitmap(upright, w, h, true) : upright;
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        if (b != upright) b.recycle();
        return EdgeMap.fromArgb(px, w, h, 1);
    }

    /** Runs detection for a freshly added page and stores the result. */
    public static void autoCrop(Page p) {
        Bitmap b = oriented(p, 1280);
        if (b == null) return;
        float[] q = null;
        try {
            q = detect(b);
        } catch (Throwable t) {
            Log.w("FilmScan", "detect failed", t);
        }
        p.detected = q != null;
        p.quad = q != null ? q : Geom.fullQuad(1, 1);
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Perspective-warps the quad {@code sp} (TL,TR,BR,BL in src pixels) onto an outW x outH
     * rectangle, then rotates by {@code rot} quarter turns (folded into the same warp).
     */
    public static Bitmap warp(Bitmap src, float[] sp, int outW, int outH, int rot) {
        int fw = (rot & 1) == 0 ? outW : outH, fh = (rot & 1) == 0 ? outH : outW;
        fw = Math.max(1, fw); fh = Math.max(1, fh);
        float[] c = {0, 0, fw, 0, fw, fh, 0, fh};
        float[] dst = new float[8];
        for (int i = 0; i < 4; i++) {
            int j = (i + rot) & 3;
            dst[2 * i] = c[2 * j]; dst[2 * i + 1] = c[2 * j + 1];
        }
        Matrix m = new Matrix();
        if (!m.setPolyToPoly(sp, 0, dst, 0, 4)) {
            m.setRectToRect(new android.graphics.RectF(0, 0, src.getWidth(), src.getHeight()),
                    new android.graphics.RectF(0, 0, fw, fh), Matrix.ScaleToFit.FILL);
        }
        Bitmap out = Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(out);
        cv.drawColor(Color.BLACK);
        cv.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        return out;
    }

    /** Perspective-corrected preview (no filter) with the long side at most maxSide. */
    public static Bitmap renderPreview(Page p, Bitmap upright, int maxSide) {
        int bw = upright.getWidth(), bh = upright.getHeight();
        float[] q = quadPx(p, bw, bh);
        double[] sz = Geom.outputSize(q, bw, bh);
        double s = Math.min(1.0, maxSide / Math.max(sz[0], sz[1]));
        int ow = (int) Math.max(1, Math.round(sz[0] * s)), oh = (int) Math.max(1, Math.round(sz[1] * s));
        return warp(upright, q, ow, oh, p.rot);
    }

    /** Largest output (in pixels) that is safe to render on this device. */
    public static long maxOutputPixels(Context c, int userLimitMp) {
        long limit;
        if (Build.VERSION.SDK_INT >= 26) {
            // bitmap pixels live in native memory
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            limit = Math.min(48_000_000L, (long) (mi.availMem * 0.35 / 8));
        } else {
            limit = (long) (Runtime.getRuntime().maxMemory() * 0.45 / 8);
        }
        limit = Math.max(4_000_000L, limit);
        if (userLimitMp > 0) limit = Math.min(limit, userLimitMp * 1_000_000L);
        return limit;
    }

    /** Full resolution, perspective-corrected render straight from the original file. */
    public static Bitmap renderFull(Page p, long maxPixels) {
        int W = p.rawW, H = p.rawH, dw = p.dispW(), dh = p.dispH();
        float[] qd = quadPx(p, dw, dh);
        double[] sz = Geom.outputSize(qd, dw, dh);
        double f = Math.min(1.0, Math.sqrt(maxPixels / (sz[0] * sz[1])));
        int outW = (int) Math.max(1, Math.round(sz[0] * f)), outH = (int) Math.max(1, Math.round(sz[1] * f));
        float[] qr = new float[8];
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -1, maxY = -1;
        for (int i = 0; i < 4; i++) {
            float[] r = dispToRaw(qd[2 * i], qd[2 * i + 1], p.exif, W, H);
            qr[2 * i] = r[0]; qr[2 * i + 1] = r[1];
            minX = Math.min(minX, r[0]); maxX = Math.max(maxX, r[0]);
            minY = Math.min(minY, r[1]); maxY = Math.max(maxY, r[1]);
        }
        Rect rect = new Rect(Math.max(0, (int) Math.floor(minX) - 2), Math.max(0, (int) Math.floor(minY) - 2),
                Math.min(W, (int) Math.ceil(maxX) + 2), Math.min(H, (int) Math.ceil(maxY) + 2));
        if (rect.width() < 2 || rect.height() < 2) rect.set(0, 0, W, H);
        int sample = 1;
        double region = (double) rect.width() * rect.height();
        while (region / ((double) sample * sample) > maxPixels * 1.6 || sample * 2 <= 1.0 / f) sample *= 2;
        Bitmap src = decodeRegion(p.file, rect, sample);
        if (src == null) {
            // decoder without region support (some HEIF/PNG): decode everything
            rect.set(0, 0, W, H);
            src = decodeFull(p.file, sample);
        }
        if (src == null) return null;
        float sx = src.getWidth() / (float) rect.width(), sy = src.getHeight() / (float) rect.height();
        float[] sp = new float[8];
        for (int i = 0; i < 4; i++) {
            sp[2 * i] = (qr[2 * i] - rect.left) * sx;
            sp[2 * i + 1] = (qr[2 * i + 1] - rect.top) * sy;
        }
        Bitmap out = warp(src, sp, outW, outH, p.rot);
        src.recycle();
        return out;
    }

    private static Bitmap decodeRegion(String path, Rect rect, int sample) {
        BitmapRegionDecoder d = null;
        try {
            d = BitmapRegionDecoder.newInstance(path, false);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Rect r = new Rect(rect);
            r.intersect(0, 0, d.getWidth(), d.getHeight());
            if (!r.equals(rect)) return null;
            return d.decodeRegion(r, o);
        } catch (Throwable t) {
            Log.w("FilmScan", "region decode failed", t);
            return null;
        } finally {
            if (d != null) d.recycle();
        }
    }

    private static Bitmap decodeFull(String path, int sample) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try {
            return BitmapFactory.decodeFile(path, o);
        } catch (OutOfMemoryError e) {
            o.inSampleSize = sample * 2;
            return BitmapFactory.decodeFile(path, o);
        }
    }

    // ------------------------------------------------------------------ finishing

    public static void applyFilter(Bitmap b, Page p) {
        Filters.apply(new BitmapSource(b), p.filter, p.bright, p.contrast, p.sharp);
    }

    public static void drawRedactions(Bitmap b, Page p) {
        if (p.redact.isEmpty()) return;
        Canvas c = new Canvas(b);
        Paint paint = new Paint();
        paint.setColor(Color.BLACK);
        int w = b.getWidth(), h = b.getHeight();
        for (float[] r : p.redact) c.drawRect(r[0] * w, r[1] * h, r[2] * w, r[3] * h, paint);
    }

    /** Finished preview-sized render: warp + filter + redactions. */
    public static Bitmap renderFinished(Page p, int maxSide) {
        Bitmap up = oriented(p, maxSide <= 600 ? 1024 : PREVIEW_SIDE);
        if (up == null) return null;
        Bitmap b = renderPreview(p, up, maxSide);
        applyFilter(b, p);
        drawRedactions(b, p);
        return b;
    }

    /** Pixel access for the filter engine. */
    public static final class BitmapSource implements PixelSource {
        private final Bitmap b;

        public BitmapSource(Bitmap b) { this.b = b; }

        public int width() { return b.getWidth(); }

        public int height() { return b.getHeight(); }

        public void read(int[] dst, int off, int y, int rows) { b.getPixels(dst, off, b.getWidth(), 0, y, b.getWidth(), rows); }

        public void write(int[] src, int off, int y, int rows) { b.setPixels(src, off, b.getWidth(), 0, y, b.getWidth(), rows); }
    }
}
