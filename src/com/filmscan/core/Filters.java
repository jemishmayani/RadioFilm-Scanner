package com.filmscan.core;

/**
 * Image filters for film and document scans. Every filter streams the image in row bands, so it
 * runs on 40+ megapixel outputs without large Java heap allocations. All parameters are relative
 * to image size, so a preview looks the same as the full-resolution result.
 */
public final class Filters {
    private Filters() {}

    public static final int ORIGINAL = 0, AUTO = 1, PRO_COLOR = 2, GRAY = 3, XRAY = 4, XRAY_DETAIL = 5,
            NEGATIVE = 6, HIGH_CONTRAST = 7, BRIGHTEN = 8, WHITEBOARD = 9, BW = 10;
    public static final int COUNT = 11;
    public static final String[] NAMES = {
            "No effects", "Auto", "Pro color", "Grayscale", "X-ray enhance", "X-ray detail",
            "Negative", "High contrast", "Brighten", "Whiteboard", "B&W document"
    };

    public static void apply(PixelSource img, int filter, int brightness, int contrast, int sharpness) {
        switch (filter) {
            case AUTO: auto(img); break;
            case PRO_COLOR: illumination(img, MODE_PRO); break;
            case GRAY: grayLut(img, identity()); break;
            case XRAY: clahe(img, 2.0f, 0.7f); break;
            case XRAY_DETAIL: clahe(img, 3.0f, 0.85f); unsharp(img, 0.9f, radius(img)); break;
            case NEGATIVE: negative(img); break;
            case HIGH_CONTRAST: highContrast(img); break;
            case BRIGHTEN: brighten(img); break;
            case WHITEBOARD: illumination(img, MODE_WHITEBOARD); break;
            case BW: illumination(img, MODE_BW); break;
            default: break;
        }
        if (sharpness > 0) unsharp(img, sharpness / 40f, radius(img));
        if (brightness != 0 || contrast != 0) channelLut(img, adjustLut(brightness, contrast), 1f);
    }

    /** True if the filter's output is grayscale. */
    public static boolean isGray(int filter) {
        return filter == GRAY || filter == XRAY || filter == XRAY_DETAIL || filter == NEGATIVE
                || filter == HIGH_CONTRAST || filter == BW;
    }

    static int radius(PixelSource img) {
        return Math.max(1, Math.round(Math.max(img.width(), img.height()) / 1400f));
    }

    static int BAND_OVERRIDE = 0; // tests only

    static int bandRows(int w, int h) {
        if (BAND_OVERRIDE > 0) return Math.min(h, BAND_OVERRIDE);
        return Math.max(8, Math.min(h, (1 << 20) / Math.max(1, w)));
    }

    static int lum(int p) {
        return (((p >> 16) & 255) * 77 + ((p >> 8) & 255) * 150 + (p & 255) * 29) >> 8;
    }

    static int c255(float v) { return v <= 0 ? 0 : (v >= 255 ? 255 : (int) (v + 0.5f)); }

    static int c255(double v) { return v <= 0 ? 0 : (v >= 255 ? 255 : (int) (v + 0.5)); }

    static int[] identity() {
        int[] l = new int[256];
        for (int i = 0; i < 256; i++) l[i] = i;
        return l;
    }

    // ------------------------------------------------------------------ statistics & LUTs

    static int[] lumHist(PixelSource img) {
        int w = img.width(), h = img.height(), rows = bandRows(w, h);
        int[] buf = new int[w * rows];
        int[] hist = new int[256];
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int i = 0, e = n * w; i < e; i++) hist[lum(buf[i])]++;
        }
        return hist;
    }

    static int pct(int[] hist, double p) {
        long total = 0;
        for (int v : hist) total += v;
        long target = (long) (total * p), acc = 0;
        for (int i = 0; i < 256; i++) {
            acc += hist[i];
            if (acc > target) return i;
        }
        return 255;
    }

    /** Linear stretch lo..hi to 0..1. */
    static double stretch(int i, int lo, int hi) {
        if (hi <= lo) return i / 255.0;
        double v = (i - lo) / (double) (hi - lo);
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Gray output from a luminance LUT. */
    static void grayLut(PixelSource img, int[] lut) {
        int w = img.width(), h = img.height(), rows = bandRows(w, h);
        int[] buf = new int[w * rows];
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int i = 0, e = n * w; i < e; i++) {
                int v = lut[lum(buf[i])];
                buf[i] = 0xff000000 | (v << 16) | (v << 8) | v;
            }
            img.write(buf, 0, y, n);
        }
    }

    /** Same LUT on each channel, then optional saturation scale around luminance. */
    static void channelLut(PixelSource img, int[] lut, float sat) {
        int w = img.width(), h = img.height(), rows = bandRows(w, h);
        int[] buf = new int[w * rows];
        boolean doSat = Math.abs(sat - 1f) > 1e-3f;
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int i = 0, e = n * w; i < e; i++) {
                int p = buf[i];
                int r = lut[(p >> 16) & 255], g = lut[(p >> 8) & 255], b = lut[p & 255];
                if (doSat) {
                    float Y = (r * 77 + g * 150 + b * 29) / 256f;
                    r = c255(Y + (r - Y) * sat); g = c255(Y + (g - Y) * sat); b = c255(Y + (b - Y) * sat);
                }
                buf[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
            img.write(buf, 0, y, n);
        }
    }

    static int[] adjustLut(int brightness, int contrast) {
        double f = contrast >= 0 ? 1 + contrast / 60.0 : 1 + contrast / 125.0;
        int[] l = new int[256];
        for (int i = 0; i < 256; i++) l[i] = c255((i - 128) * f + 128 + brightness * 0.9);
        return l;
    }

    // ------------------------------------------------------------------ simple filters

    /** Per-channel auto levels (removes lightbox / lamp colour casts) plus a mild gamma. */
    static void auto(PixelSource img) {
        int w = img.width(), h = img.height(), rows = bandRows(w, h);
        int[] buf = new int[w * rows];
        int[][] ch = new int[3][256];
        int[] lh = new int[256];
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int i = 0, e = n * w; i < e; i++) {
                int p = buf[i];
                ch[0][(p >> 16) & 255]++; ch[1][(p >> 8) & 255]++; ch[2][p & 255]++;
                lh[lum(p)]++;
            }
        }
        int llo = pct(lh, 0.003), lhi = pct(lh, 0.997);
        int[][] luts = new int[3][256];
        int med = pct(lh, 0.5);
        double m = Math.max(0.05, Math.min(0.95, stretch(med, llo, lhi)));
        double g = Math.log(0.5) / Math.log(m);
        g = 1 + (g - 1) * 0.5;
        g = Math.max(0.7, Math.min(1.4, g));
        for (int c = 0; c < 3; c++) {
            int lo = pct(ch[c], 0.003), hi = pct(ch[c], 0.997);
            // do not let one channel stretch wildly (strongly coloured subjects)
            lo = Math.max(lo, llo - 40); hi = Math.min(hi, lhi + 40);
            lo = Math.min(lo, llo + 40); hi = Math.max(hi, lhi - 40);
            if (hi - lo < 24) { lo = Math.max(0, lo - 12); hi = Math.min(255, hi + 12); }
            for (int i = 0; i < 256; i++) luts[c][i] = c255(255 * Math.pow(stretch(i, lo, hi), g));
        }
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int i = 0, e = n * w; i < e; i++) {
                int p = buf[i];
                int r = luts[0][(p >> 16) & 255], gg = luts[1][(p >> 8) & 255], b = luts[2][p & 255];
                float Y = (r * 77 + gg * 150 + b * 29) / 256f;
                r = c255(Y + (r - Y) * 1.1f); gg = c255(Y + (gg - Y) * 1.1f); b = c255(Y + (b - Y) * 1.1f);
                buf[i] = 0xff000000 | (r << 16) | (gg << 8) | b;
            }
            img.write(buf, 0, y, n);
        }
    }

    static void negative(PixelSource img) {
        int[] hist = lumHist(img);
        int lo = pct(hist, 0.005), hi = pct(hist, 0.995);
        int[] l = new int[256];
        for (int i = 0; i < 256; i++) l[i] = c255(255 * (1 - stretch(i, lo, hi)));
        grayLut(img, l);
    }

    static void highContrast(PixelSource img) {
        int[] hist = lumHist(img);
        int lo = pct(hist, 0.01), hi = pct(hist, 0.99);
        double k = 7, s0 = 1 / (1 + Math.exp(k * 0.5)), s1 = 1 / (1 + Math.exp(-k * 0.5));
        int[] l = new int[256];
        for (int i = 0; i < 256; i++) {
            double v = stretch(i, lo, hi);
            double s = 1 / (1 + Math.exp(-k * (v - 0.5)));
            l[i] = c255(255 * (s - s0) / (s1 - s0));
        }
        grayLut(img, l);
    }

    static void brighten(PixelSource img) {
        int[] hist = lumHist(img);
        int lo = pct(hist, 0.002), hi = pct(hist, 0.998);
        int[] l = new int[256];
        for (int i = 0; i < 256; i++) l[i] = c255(255 * Math.pow(stretch(i, lo, hi), 0.62));
        channelLut(img, l, 1.05f);
    }

    // ------------------------------------------------------------------ CLAHE (X-ray enhance)

    /**
     * Contrast-limited adaptive histogram equalisation on luminance, blended with a global
     * stretch so black film background stays black. Grayscale output.
     */
    static void clahe(PixelSource img, float clip, float blend) {
        int w = img.width(), h = img.height();
        int tx = w >= h ? 8 : Math.max(2, Math.round(8f * w / h));
        int ty = h >= w ? 8 : Math.max(2, Math.round(8f * h / w));
        int tw = (w + tx - 1) / tx, th = (h + ty - 1) / ty;
        int[][] hist = new int[tx * ty][256];
        int rows = bandRows(w, h);
        int[] buf = new int[w * rows];
        int[] tileOfX = new int[w];
        for (int x = 0; x < w; x++) tileOfX[x] = Math.min(tx - 1, x / tw);
        int[] gh = new int[256];
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int k = 0; k < n; k++) {
                int trow = Math.min(ty - 1, (y + k) / th) * tx;
                int o = k * w;
                for (int x = 0; x < w; x++) {
                    int v = lum(buf[o + x]);
                    hist[trow + tileOfX[x]][v]++;
                    gh[v]++;
                }
            }
        }
        int glo = pct(gh, 0.003), ghi = pct(gh, 0.997);
        float[] global = new float[256];
        for (int i = 0; i < 256; i++) global[i] = (float) (255 * stretch(i, glo, ghi)) * (1 - blend);
        float[][] map = new float[tx * ty][256];
        for (int t = 0; t < tx * ty; t++) {
            int[] hs = hist[t];
            int total = 0;
            for (int v : hs) total += v;
            if (total == 0) { for (int i = 0; i < 256; i++) map[t][i] = i; continue; }
            int limit = Math.max(1, (int) (clip * total / 256f));
            int excess = 0;
            for (int i = 0; i < 256; i++) if (hs[i] > limit) { excess += hs[i] - limit; hs[i] = limit; }
            int add = excess / 256, rem = excess % 256;
            for (int i = 0; i < 256; i++) hs[i] += add + (i < rem ? 1 : 0);
            long cdf = 0;
            for (int i = 0; i < 256; i++) { cdf += hs[i]; map[t][i] = 255f * cdf / total; }
        }
        int[] x0 = new int[w], x1 = new int[w];
        float[] ax = new float[w];
        for (int x = 0; x < w; x++) {
            float f = (x + 0.5f) / tw - 0.5f;
            int a = (int) Math.floor(f);
            float fr = f - a;
            if (a < 0) { a = 0; fr = 0; }
            if (a >= tx - 1) { a = tx - 1; fr = 0; }
            x0[x] = a; x1[x] = Math.min(tx - 1, a + 1); ax[x] = fr;
        }
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int k = 0; k < n; k++) {
                float f = (y + k + 0.5f) / th - 0.5f;
                int a = (int) Math.floor(f);
                float ay = f - a;
                if (a < 0) { a = 0; ay = 0; }
                if (a >= ty - 1) { a = ty - 1; ay = 0; }
                int b = Math.min(ty - 1, a + 1);
                int ra = a * tx, rb = b * tx;
                int o = k * w;
                for (int x = 0; x < w; x++) {
                    int v = lum(buf[o + x]);
                    float fx = ax[x];
                    float top = map[ra + x0[x]][v] * (1 - fx) + map[ra + x1[x]][v] * fx;
                    float bot = map[rb + x0[x]][v] * (1 - fx) + map[rb + x1[x]][v] * fx;
                    int g = c255((top * (1 - ay) + bot * ay) * blend + global[v]);
                    buf[o + x] = 0xff000000 | (g << 16) | (g << 8) | g;
                }
            }
            img.write(buf, 0, y, n);
        }
    }

    // ------------------------------------------------------------------ illumination (documents)

    static final int MODE_PRO = 0, MODE_WHITEBOARD = 1, MODE_BW = 2;

    /**
     * Estimates the paper/background colour with a local maximum + blur on a small copy, divides
     * it out (removes shadows and uneven light), then applies a mode specific tone curve.
     */
    static void illumination(PixelSource img, int mode) {
        int w = img.width(), h = img.height();
        int f = Math.max(1, (int) Math.ceil(Math.max(w, h) / 256.0));
        int sw = (w + f - 1) / f, sh = (h + f - 1) / f;
        float[][] bg = new float[3][sw * sh];
        int[] cnt = new int[sw * sh];
        int rows = bandRows(w, h);
        int[] buf = new int[w * rows];
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int k = 0; k < n; k++) {
                int so = ((y + k) / f) * sw;
                int o = k * w;
                for (int x = 0; x < w; x++) {
                    int p = buf[o + x], c = so + x / f;
                    bg[0][c] += (p >> 16) & 255; bg[1][c] += (p >> 8) & 255; bg[2][c] += p & 255;
                    cnt[c]++;
                }
            }
        }
        int rd = Math.max(2, Math.round(0.03f * Math.max(sw, sh)));
        for (int c = 0; c < 3; c++) {
            float[] a = bg[c];
            for (int i = 0; i < a.length; i++) a[i] = cnt[i] > 0 ? a[i] / cnt[i] : 0;
            maxFilter(a, sw, sh, rd);
            EdgeMap.boxBlur(a, sw, sh, rd);
            EdgeMap.boxBlur(a, sw, sh, rd);
        }
        int[] sx0 = new int[w], sx1 = new int[w];
        float[] sax = new float[w];
        for (int x = 0; x < w; x++) {
            float fx = (x + 0.5f) / f - 0.5f;
            int a = (int) Math.floor(fx);
            float fr = fx - a;
            if (a < 0) { a = 0; fr = 0; }
            if (a >= sw - 1) { a = sw - 1; fr = 0; }
            sx0[x] = a; sx1[x] = Math.min(sw - 1, a + 1); sax[x] = fr;
        }
        float[][] rowBg = new float[3][sw];
        for (int y = 0; y < h; y += rows) {
            int n = Math.min(rows, h - y);
            img.read(buf, 0, y, n);
            for (int k = 0; k < n; k++) {
                float fy = (y + k + 0.5f) / f - 0.5f;
                int a = (int) Math.floor(fy);
                float ay = fy - a;
                if (a < 0) { a = 0; ay = 0; }
                if (a >= sh - 1) { a = sh - 1; ay = 0; }
                int b = Math.min(sh - 1, a + 1);
                for (int c = 0; c < 3; c++) {
                    float[] src = bg[c], dst = rowBg[c];
                    for (int x = 0; x < sw; x++) dst[x] = src[a * sw + x] * (1 - ay) + src[b * sw + x] * ay;
                }
                int o = k * w;
                for (int x = 0; x < w; x++) {
                    int p = buf[o + x];
                    float fx = sax[x];
                    int i0 = sx0[x], i1 = sx1[x];
                    float br = Math.max(8f, rowBg[0][i0] * (1 - fx) + rowBg[0][i1] * fx);
                    float bgg = Math.max(8f, rowBg[1][i0] * (1 - fx) + rowBg[1][i1] * fx);
                    float bb = Math.max(8f, rowBg[2][i0] * (1 - fx) + rowBg[2][i1] * fx);
                    float vr = Math.min(1f, ((p >> 16) & 255) / br);
                    float vg = Math.min(1f, ((p >> 8) & 255) / bgg);
                    float vb = Math.min(1f, (p & 255) / bb);
                    int r, g, bl;
                    if (mode == MODE_BW) {
                        float v = vr * 0.299f + vg * 0.587f + vb * 0.114f;
                        float t = (v - 0.60f) / 0.25f;
                        int gv = c255(255f * (t < 0 ? 0 : (t > 1 ? 1 : t)));
                        r = g = bl = gv;
                    } else if (mode == MODE_WHITEBOARD) {
                        r = c255(255f * wb(vr)); g = c255(255f * wb(vg)); bl = c255(255f * wb(vb));
                        float Y = (r * 77 + g * 150 + bl * 29) / 256f;
                        r = c255(Y + (r - Y) * 1.5f); g = c255(Y + (g - Y) * 1.5f); bl = c255(Y + (bl - Y) * 1.5f);
                    } else {
                        r = c255(255f * pro(vr)); g = c255(255f * pro(vg)); bl = c255(255f * pro(vb));
                        float Y = (r * 77 + g * 150 + bl * 29) / 256f;
                        r = c255(Y + (r - Y) * 1.35f); g = c255(Y + (g - Y) * 1.35f); bl = c255(Y + (bl - Y) * 1.35f);
                    }
                    buf[o + x] = 0xff000000 | (r << 16) | (g << 8) | bl;
                }
            }
            img.write(buf, 0, y, n);
        }
    }

    /** Whiteboard curve: near-white snaps to white, ink gets deeper. */
    private static float wb(float v) {
        float t = Math.max(0f, 0.94f - v) / 0.94f;
        return 1f - Math.min(1f, t * 2.1f);
    }

    private static float pro(float v) {
        float t = (v - 0.06f) / 0.92f;
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return (float) Math.pow(t, 1.3);
    }

    /** Separable max filter (dilation) with radius r. */
    static void maxFilter(float[] a, int w, int h, int r) {
        float[] tmp = new float[Math.max(w, h)];
        for (int y = 0; y < h; y++) {
            int o = y * w;
            for (int x = 0; x < w; x++) {
                float m = 0;
                for (int k = Math.max(0, x - r), e = Math.min(w - 1, x + r); k <= e; k++) if (a[o + k] > m) m = a[o + k];
                tmp[x] = m;
            }
            System.arraycopy(tmp, 0, a, o, w);
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                float m = 0;
                for (int k = Math.max(0, y - r), e = Math.min(h - 1, y + r); k <= e; k++) if (a[k * w + x] > m) m = a[k * w + x];
                tmp[y] = m;
            }
            for (int y = 0; y < h; y++) a[y * w + x] = tmp[y];
        }
    }

    // ------------------------------------------------------------------ unsharp mask

    /** Unsharp mask on luminance (added equally to each channel), streamed with row overlap. */
    static void unsharp(PixelSource img, float amount, int r) {
        if (amount <= 0 || r < 1) return;
        final int w = img.width(), h = img.height();
        final int ov = 2 * r;
        int rows = Math.min(h, Math.max(bandRows(w, h), 2 * ov + 1));
        int maxN = rows + 2 * ov;
        int[] buf = new int[w * maxN];
        float[] L = new float[w * maxN], T = new float[w * maxN];
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
            for (int i = 0, e = n * w; i < e; i++) L[i] = lum(buf[i]);
            boxH(L, T, w, n, r); boxH(T, L, w, n, r);
            boxV(L, T, w, n, r); boxV(T, L, w, n, r);
            for (int y = y0; y < y1; y++) {
                int o = (y - top) * w;
                for (int x = 0; x < w; x++) {
                    int i = o + x, p = buf[i];
                    float d = amount * (lum(p) - L[i]);
                    int rr = c255(((p >> 16) & 255) + d), gg = c255(((p >> 8) & 255) + d), bb = c255((p & 255) + d);
                    buf[i] = 0xff000000 | (rr << 16) | (gg << 8) | bb;
                }
            }
            img.write(buf, (y0 - top) * w, y0, y1 - y0);
            carryRows = nc;
        }
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
