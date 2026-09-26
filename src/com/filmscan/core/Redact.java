package com.filmscan.core;

/**
 * Anonymising regions: black box, blur or pixelate. Sizes are relative to the whole image, so the
 * on-screen preview and the full-resolution export look the same.
 */
public final class Redact {
    private Redact() {}

    public static final int BOX = 0, BLUR = 1, PIXELATE = 2;

    /** Region type; older saves have only 4 numbers and are black boxes. */
    public static int type(float[] r) {
        if (r.length < 5) return BOX;
        int t = Math.round(r[4]);
        return t == BLUR || t == PIXELATE ? t : BOX;
    }

    public static int blurRadius(int imgW, int imgH) {
        return Math.max(3, Math.round(Math.max(imgW, imgH) * 0.008f));
    }

    public static int blockSize(int imgW, int imgH) {
        return Math.max(4, Math.round(Math.max(imgW, imgH) * 0.014f));
    }

    /** Three passes of a box blur of radius r (close to a Gaussian), in place, on ARGB pixels. */
    public static void blur(int[] px, int w, int h, int r) {
        if (r < 1 || w < 1 || h < 1) return;
        int[] tmp = new int[Math.max(w, h)];
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < h; y++) boxLine(px, y * w, 1, w, r, tmp);
            for (int x = 0; x < w; x++) boxLine(px, x, w, h, r, tmp);
        }
    }

    /** Sliding-window average along one row or column (edges clamped). */
    private static void boxLine(int[] px, int start, int stride, int n, int r, int[] out) {
        long sa = 0, sr = 0, sg = 0, sb = 0;
        int win = 2 * r + 1;
        for (int k = -r; k <= r; k++) {
            int p = px[start + clamp(k, n) * stride];
            sa += (p >>> 24); sr += (p >> 16) & 255; sg += (p >> 8) & 255; sb += p & 255;
        }
        for (int i = 0; i < n; i++) {
            out[i] = (int) (sa / win) << 24 | (int) (sr / win) << 16 | (int) (sg / win) << 8 | (int) (sb / win);
            int add = px[start + clamp(i + r + 1, n) * stride], sub = px[start + clamp(i - r, n) * stride];
            sa += (add >>> 24) - (sub >>> 24);
            sr += ((add >> 16) & 255) - ((sub >> 16) & 255);
            sg += ((add >> 8) & 255) - ((sub >> 8) & 255);
            sb += (add & 255) - (sub & 255);
        }
        for (int i = 0; i < n; i++) px[start + i * stride] = out[i];
    }

    private static int clamp(int v, int n) { return v < 0 ? 0 : (v >= n ? n - 1 : v); }

    /** Average colour of each cell of a cols x rows grid laid over a w x h image. */
    public static int[] blockAverages(int[] px, int w, int h, int cols, int rows) {
        int[] out = new int[cols * rows];
        for (int by = 0; by < rows; by++) {
            int y0 = by * h / rows, y1 = Math.max(y0 + 1, (by + 1) * h / rows);
            for (int bx = 0; bx < cols; bx++) {
                int x0 = bx * w / cols, x1 = Math.max(x0 + 1, (bx + 1) * w / cols);
                long r = 0, g = 0, b = 0, n = 0;
                for (int y = y0; y < Math.min(y1, h); y++) {
                    for (int x = x0; x < Math.min(x1, w); x++) {
                        int p = px[y * w + x];
                        r += (p >> 16) & 255; g += (p >> 8) & 255; b += p & 255; n++;
                    }
                }
                if (n == 0) n = 1;
                out[by * cols + bx] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }
}
