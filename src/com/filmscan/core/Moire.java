package com.filmscan.core;

/**
 * Monitor Mode: reduces moiré (rainbow ripples and fine interference patterns) in photos of a screen.
 * Colour fringes are smoothed strongly (moiré is mostly false colour), brightness only gently, so
 * anatomy and text stay sharp. Strength 0..100; sizes scale with the image, so a preview matches the
 * full-resolution export. Works in row strips with carried-over rows, like the other filters.
 */
public final class Moire {
    private Moire() {}

    public static final int LOW = 30, MEDIUM = 55, HIGH = 80;

    public static String label(int strength) {
        if (strength <= 0) return "Off";
        if (strength == LOW) return "Low";
        if (strength == MEDIUM) return "Medium";
        if (strength == HIGH) return "High";
        return "Custom " + strength;
    }

    static int lumaRadius(int w, int h, float s) { return Math.max(1, Math.round(Math.max(w, h) * 0.0022f * s)); }

    static int chromaRadius(int w, int h, float s) { return Math.max(2, Math.round(Math.max(w, h) * 0.004f * s)); }

    public static void apply(PixelSource img, int strength) {
        if (strength <= 0) return;
        final int w = img.width(), h = img.height();
        float s = Math.min(100, strength) / 100f;
        final int rY = lumaRadius(w, h, s), rC = chromaRadius(w, h, s);
        final float aY = 0.3f + 0.6f * s;               // how far brightness moves towards its smoothed value
        final float aC = Math.min(1f, 0.5f + 0.7f * s); // how far colour moves towards its smoothed value
        final int ov = 2 * Math.max(rY, rC);
        int rows = Math.min(h, Math.max(Filters.bandRows(w, h), 2 * ov + 1));
        int maxN = rows + 2 * ov;
        int[] buf = new int[w * maxN];
        float[] Y = new float[w * maxN], Cb = new float[w * maxN], Cr = new float[w * maxN];
        float[] Yb = new float[w * maxN], Cbb = new float[w * maxN], Crb = new float[w * maxN], T = new float[w * maxN];
        int[] carry = new int[w * ov];
        int carryRows = 0;
        for (int y0 = 0; y0 < h; y0 += rows) {
            int y1 = Math.min(h, y0 + rows);
            int top = y0 - carryRows;
            int bot = Math.min(h, y1 + ov);
            int n = bot - top;
            System.arraycopy(carry, 0, buf, 0, carryRows * w);
            img.read(buf, carryRows * w, y0, bot - y0);
            int nc = Math.min(ov, y1);
            System.arraycopy(buf, (y1 - nc - top) * w, carry, 0, nc * w);
            for (int i = 0, e = n * w; i < e; i++) {
                int p = buf[i];
                float r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                Y[i] = 0.299f * r + 0.587f * g + 0.114f * b;
                Cb[i] = -0.168736f * r - 0.331264f * g + 0.5f * b;
                Cr[i] = 0.5f * r - 0.418688f * g - 0.081312f * b;
            }
            blur(Y, Yb, T, w, n, rY);
            blur(Cb, Cbb, T, w, n, rC);
            blur(Cr, Crb, T, w, n, rC);
            for (int y = y0; y < y1; y++) {
                int o = (y - top) * w;
                for (int x = 0; x < w; x++) {
                    int i = o + x;
                    float yy = Y[i] + aY * (Yb[i] - Y[i]);
                    float cb = Cb[i] + aC * (Cbb[i] - Cb[i]);
                    float cr = Cr[i] + aC * (Crb[i] - Cr[i]);
                    int rr = Filters.c255(yy + 1.402f * cr);
                    int gg = Filters.c255(yy - 0.344136f * cb - 0.714136f * cr);
                    int bb = Filters.c255(yy + 1.772f * cb);
                    buf[i] = (buf[i] & 0xff000000) | (rr << 16) | (gg << 8) | bb;
                }
            }
            img.write(buf, (y0 - top) * w, y0, y1 - y0);
            carryRows = nc;
        }
    }

    /** Two box-blur passes in each direction (close to a Gaussian): src -> dst, using tmp. */
    private static void blur(float[] src, float[] dst, float[] tmp, int w, int n, int r) {
        boxH(src, tmp, w, n, r); boxH(tmp, dst, w, n, r);
        boxV(dst, tmp, w, n, r); boxV(tmp, dst, w, n, r);
    }

    private static void boxH(float[] s, float[] d, int w, int n, int r) {
        float inv = 1f / (2 * r + 1);
        for (int y = 0; y < n; y++) {
            int o = y * w;
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += s[o + clamp(k, w)];
            for (int x = 0; x < w; x++) {
                d[o + x] = sum * inv;
                sum += s[o + clamp(x + r + 1, w)] - s[o + clamp(x - r, w)];
            }
        }
    }

    private static void boxV(float[] s, float[] d, int w, int n, int r) {
        float inv = 1f / (2 * r + 1);
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += s[clamp(k, n) * w + x];
            for (int y = 0; y < n; y++) {
                d[y * w + x] = sum * inv;
                sum += s[clamp(y + r + 1, n) * w + x] - s[clamp(y - r, n) * w + x];
            }
        }
    }

    private static int clamp(int v, int n) { return v < 0 ? 0 : (v >= n ? n - 1 : v); }
}
