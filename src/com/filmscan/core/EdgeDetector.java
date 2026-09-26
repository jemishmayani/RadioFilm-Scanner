package com.filmscan.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Finds the dominant quadrilateral (film sheet, document, report) in a small image.
 * Candidates come from Canny edge contours at several thresholds and from light/dark regions;
 * each candidate is scored by how much of its outline lies on real edges, then refined.
 */
public final class EdgeDetector {
    private EdgeDetector() {}

    public static boolean DEBUG = false;
    static final float MIN_SCORE = 0.12f;

    public static final class Result {
        public final float[] quad;  // TL, TR, BR, BL in input pixel coords
        public final float score;
        public final float support;
        Result(float[] q, float score, float support) { quad = q; this.score = score; this.support = support; }
    }

    /** Detection on packed ARGB pixels. Works best with the long side around 400-700 px. */
    public static Result detect(int[] argb, int w, int h) {
        EdgeMap em = EdgeMap.fromArgb(argb, w, h, Math.max(1, Math.round(Math.max(w, h) / 400f)));
        return detect(em);
    }

    public static Result detect(EdgeMap em) {
        int w = em.w, h = em.h;
        List<float[]> cands = new ArrayList<float[]>();
        byte[] nms = nonMaxSuppress(em);
        float[] highs = {0.93f, 0.85f, 0.72f};
        for (float hp : highs) {
            byte[] edges = hysteresis(em, nms, hp, 0.45f);
            dilate(edges, w, h);
            edgeComponents(edges, w, h, cands);
        }
        regionCandidates(em, cands);
        houghCandidates(em, nms, cands);

        float bestScore = 0; float[] best = null; float bestSup = 0;
        double imgArea = (double) w * h;
        List<float[]> good = new ArrayList<float[]>();
        List<float[]> goodInfo = new ArrayList<float[]>();
        for (float[] q0 : cands) {
            float[] q = Geom.orderCorners(q0);
            if (!Geom.isConvex(q)) continue;
            double af = Geom.area(q) / imgArea;
            if (af < 0.07) continue;
            if (!plausible(q, w, h)) continue;
            float[] sup = em.support(q);
            // Random lines through texture reach ~50% polarity-consistent support; real borders ~90%.
            float sMin = Math.max(0f, (sup[0] - 0.35f) / 0.65f), sMean = Math.max(0f, (sup[1] - 0.35f) / 0.65f);
            float score = (float) (sMean * sMean * (0.5 + 0.5 * sMin) * Math.sqrt(af));
            if (DEBUG) System.out.printf("  cand area=%.2f min=%.2f mean=%.2f score=%.3f %s%n", af, sup[0], sup[1], score, java.util.Arrays.toString(q));
            if (sup[0] < 0.3f || sup[1] < 0.55f) continue;
            good.add(q); goodInfo.add(new float[]{score, sup[0], sup[1], (float) af});
            if (score > bestScore) { bestScore = score; best = q; bestSup = sup[1]; }
        }
        if (best == null || bestScore < MIN_SCORE) return null;
        // Films carry strong internal structure (panel grids). If a well-supported quad encloses
        // the winner, the enclosing one is the sheet itself.
        float bestArea = (float) (Geom.area(best) / imgArea);
        float[] outer = null; float outerArea = bestArea;
        for (int i = 0; i < good.size(); i++) {
            float[] info = goodInfo.get(i);
            if (info[3] < bestArea * 1.12f || info[3] <= outerArea) continue;
            if (info[0] < bestScore * 0.55f || info[1] < 0.45f || info[2] < 0.75f) continue;
            if (!contains(good.get(i), best, Math.max(w, h) * 0.02f)) continue;
            outer = good.get(i); outerArea = info[3];
        }
        if (outer != null) { best = outer; bestSup = em.support(outer)[1]; }
        float R = Math.max(3f, Math.max(w, h) * 0.012f);
        best = em.refine(best, R);
        best = em.refine(best, Math.max(2f, R * 0.5f));
        return new Result(best, bestScore, bestSup);
    }

    /** True if every corner of {@code inner} lies inside {@code outer} (with tolerance). */
    static boolean contains(float[] outer, float[] inner, float tol) {
        for (int c = 0; c < 4; c++) {
            float x = inner[2 * c], y = inner[2 * c + 1];
            for (int s = 0; s < 4; s++) {
                int e = (s + 1) & 3;
                float ax = outer[2 * s], ay = outer[2 * s + 1], bx = outer[2 * e], by = outer[2 * e + 1];
                float len = (float) Math.hypot(bx - ax, by - ay);
                if (len < 1e-3f) return false;
                // signed distance, positive inside for clockwise (screen) order
                float d = ((bx - ax) * (y - ay) - (by - ay) * (x - ax)) / len;
                if (d < -tol) return false;
            }
        }
        return true;
    }

    /** Reject slivers and extremely distorted shapes. */
    static boolean plausible(float[] q, int w, int h) {
        double minSide = 0.12 * Math.min(w, h);
        for (int i = 0; i < 4; i++) {
            if (Geom.dist(q, i, (i + 1) & 3) < minSide) return false;
            // interior angle between 35 and 145 degrees
            int p = (i + 3) & 3, n = (i + 1) & 3;
            double ax = q[2 * p] - q[2 * i], ay = q[2 * p + 1] - q[2 * i + 1];
            double bx = q[2 * n] - q[2 * i], by = q[2 * n + 1] - q[2 * i + 1];
            double cos = (ax * bx + ay * by) / (Math.hypot(ax, ay) * Math.hypot(bx, by));
            if (cos > 0.82 || cos < -0.82) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ Canny

    private static byte[] nonMaxSuppress(EdgeMap em) {
        int w = em.w, h = em.h;
        byte[] out = new byte[w * h];
        float[] m = em.mag, gx = em.gx, gy = em.gy;
        final float t = 0.41421356f;
        for (int y = 2; y < h - 2; y++) {
            for (int x = 2; x < w - 2; x++) {
                int i = y * w + x;
                float v = m[i];
                if (v < 4) continue;
                float ax = Math.abs(gx[i]), ay = Math.abs(gy[i]);
                float n1, n2;
                if (ay <= ax * t) { n1 = m[i - 1]; n2 = m[i + 1]; }
                else if (ax <= ay * t) { n1 = m[i - w]; n2 = m[i + w]; }
                else if (gx[i] * gy[i] > 0) { n1 = m[i - w - 1]; n2 = m[i + w + 1]; }
                else { n1 = m[i - w + 1]; n2 = m[i + w - 1]; }
                if (v > n1 && v >= n2) out[i] = 1;
            }
        }
        return out;
    }

    private static byte[] hysteresis(EdgeMap em, byte[] nms, float highPct, float lowRatio) {
        int w = em.w, h = em.h, n = w * h;
        float max = 0; int cnt = 0;
        for (int i = 0; i < n; i++) if (nms[i] != 0) { cnt++; if (em.mag[i] > max) max = em.mag[i]; }
        byte[] out = new byte[n];
        if (cnt == 0) return out;
        float[] vals = new float[cnt];
        int k = 0;
        for (int i = 0; i < n; i++) if (nms[i] != 0) vals[k++] = em.mag[i];
        Arrays.sort(vals);
        float hi = Math.max(20f, vals[Math.min(cnt - 1, (int) (cnt * highPct))]);
        float lo = hi * lowRatio;
        int[] stack = new int[n];
        int sp = 0;
        for (int i = 0; i < n; i++) {
            if (nms[i] != 0 && out[i] == 0 && em.mag[i] >= hi) {
                out[i] = 1; stack[sp++] = i;
                while (sp > 0) {
                    int p = stack[--sp];
                    int px = p % w, py = p / w;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = py + dy;
                        if (yy < 0 || yy >= h) continue;
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = px + dx;
                            if (xx < 0 || xx >= w) continue;
                            int q = yy * w + xx;
                            if (out[q] == 0 && nms[q] != 0 && em.mag[q] >= lo) { out[q] = 1; stack[sp++] = q; }
                        }
                    }
                }
            }
        }
        return out;
    }

    private static void dilate(byte[] b, int w, int h) {
        byte[] c = b.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (c[y * w + x] == 0) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = y + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx;
                        if (xx >= 0 && xx < w) b[yy * w + xx] = 1;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ components

    private static void edgeComponents(byte[] edges, int w, int h, List<float[]> out) {
        componentsToQuads(edges, w, h, out, true, 0, 4);
    }

    /**
     * Connected components of set pixels; each large component contributes its hull's best quad.
     * @param eight 8- or 4-connectivity
     * @param minArea minimum pixel count
     * @param maxBorderSides reject components touching more image sides than this
     */
    private static void componentsToQuads(byte[] img, int w, int h, List<float[]> out,
                                          boolean eight, int minArea, int maxBorderSides) {
        int n = w * h;
        byte[] seen = new byte[n];
        int[] stack = new int[n];
        int[] rowMin = new int[h], rowMax = new int[h];
        Arrays.fill(rowMin, Integer.MAX_VALUE);
        Arrays.fill(rowMax, -1);
        int[] touched = new int[h];
        for (int s = 0; s < n; s++) {
            if (img[s] == 0 || seen[s] != 0) continue;
            int sp = 0, count = 0, nt = 0;
            int minX = w, maxX = -1, minY = h, maxY = -1;
            stack[sp++] = s; seen[s] = 1;
            while (sp > 0) {
                int p = stack[--sp];
                int px = p % w, py = p / w;
                count++;
                if (px < minX) minX = px; if (px > maxX) maxX = px;
                if (py < minY) minY = py; if (py > maxY) maxY = py;
                if (rowMax[py] < 0) touched[nt++] = py;
                if (px < rowMin[py]) rowMin[py] = px;
                if (px > rowMax[py]) rowMax[py] = px;
                for (int dy = -1; dy <= 1; dy++) {
                    int yy = py + dy;
                    if (yy < 0 || yy >= h) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        if (!eight && dx != 0 && dy != 0) continue;
                        int xx = px + dx;
                        if (xx < 0 || xx >= w) continue;
                        int q = yy * w + xx;
                        if (img[q] != 0 && seen[q] == 0) { seen[q] = 1; stack[sp++] = q; }
                    }
                }
            }
            boolean big = (maxX - minX) >= 0.2 * w && (maxY - minY) >= 0.2 * h && count >= minArea;
            int sides = (minX <= 1 ? 1 : 0) + (maxX >= w - 2 ? 1 : 0) + (minY <= 1 ? 1 : 0) + (maxY >= h - 2 ? 1 : 0);
            if (big && sides <= maxBorderSides) {
                int[] xs = new int[nt * 2], ys = new int[nt * 2];
                int k = 0;
                for (int t = 0; t < nt; t++) {
                    int y = touched[t];
                    xs[k] = rowMin[y]; ys[k++] = y;
                    xs[k] = rowMax[y]; ys[k++] = y;
                }
                float[] quad = hullQuad(xs, ys, k);
                if (quad != null) out.add(quad);
            }
            for (int t = 0; t < nt; t++) { int y = touched[t]; rowMin[y] = Integer.MAX_VALUE; rowMax[y] = -1; }
        }
    }

    private static void regionCandidates(EdgeMap em, List<float[]> out) {
        int w = em.w, h = em.h, n = w * h;
        int[] hist = new int[256];
        for (int i = 0; i < n; i++) hist[Math.max(0, Math.min(255, (int) em.gray[i]))]++;
        int t = otsu(hist, n);
        byte[] bin = new byte[n];
        for (int pol = 0; pol < 2; pol++) {
            for (int i = 0; i < n; i++) {
                boolean bright = em.gray[i] > t;
                bin[i] = (byte) ((pol == 0) == bright ? 1 : 0);
            }
            open(bin, w, h);
            componentsToQuads(bin, w, h, out, false, (int) (0.06 * n), 2);
        }
    }

    private static int otsu(int[] hist, int total) {
        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * (double) hist[i];
        double sumB = 0, best = -1; int wB = 0, thr = 128;
        for (int i = 0; i < 256; i++) {
            wB += hist[i];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += i * (double) hist[i];
            double mB = sumB / wB, mF = (sum - sumB) / wF;
            double v = (double) wB * wF * (mB - mF) * (mB - mF);
            if (v > best) { best = v; thr = i; }
        }
        return thr;
    }

    /** Morphological opening (3x3) to cut thin bridges between regions. */
    private static void open(byte[] b, int w, int h) {
        byte[] e = new byte[b.length];
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            int i = y * w + x;
            e[i] = (byte) (b[i] & b[i - 1] & b[i + 1] & b[i - w] & b[i + w] & b[i - w - 1] & b[i - w + 1] & b[i + w - 1] & b[i + w + 1]);
        }
        Arrays.fill(b, (byte) 0);
        for (int y = 1; y < h - 1; y++) for (int x = 1; x < w - 1; x++) {
            if (e[y * w + x] == 0) continue;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) b[(y + dy) * w + x + dx] = 1;
        }
    }

    // ------------------------------------------------------------------ Hough lines

    /**
     * Gradient-constrained Hough transform: finds strong straight lines and combines two roughly
     * parallel pairs into quads. Handles films/pages whose corners are hidden or low-contrast.
     */
    private static void houghCandidates(EdgeMap em, byte[] nms, List<float[]> out) {
        final int w = em.w, h = em.h;
        final int nT = 180;
        final double diag = Math.hypot(w, h);
        final int nR = (int) (2 * diag) + 2;
        float[] cs = new float[nT], sn = new float[nT];
        for (int t = 0; t < nT; t++) { cs[t] = (float) Math.cos(Math.PI * t / nT); sn[t] = (float) Math.sin(Math.PI * t / nT); }
        int[] acc = new int[nT * nR];
        float thr = Math.max(8f, 2.2f * em.noise);
        for (int y = 2; y < h - 2; y++) {
            for (int x = 2; x < w - 2; x++) {
                int i = y * w + x;
                if (nms[i] == 0 || em.mag[i] < thr) continue;
                double a = Math.atan2(em.gy[i], em.gx[i]);
                if (a < 0) a += Math.PI;
                int t0 = (int) Math.round(a / Math.PI * nT);
                for (int dt = -5; dt <= 5; dt++) {
                    int t = t0 + dt;
                    if (t < 0) t += nT; else if (t >= nT) t -= nT;
                    int r = (int) Math.round(x * cs[t] + y * sn[t] + diag);
                    acc[t * nR + r]++;
                }
            }
        }
        int K = 12;
        int[] lt = new int[K], lr = new int[K], lv = new int[K];
        int nl = 0;
        int minVotes = (int) (0.12 * Math.min(w, h));
        for (int k = 0; k < K; k++) {
            int best = 0, bi = -1;
            for (int i = 0; i < acc.length; i++) if (acc[i] > best) { best = acc[i]; bi = i; }
            if (bi < 0 || best < minVotes) break;
            int t = bi / nR, r = bi % nR;
            lt[nl] = t; lr[nl] = r; lv[nl] = best; nl++;
            for (int dt = -6; dt <= 6; dt++) {
                int tt = t + dt, rr = r;
                if (tt < 0) { tt += nT; rr = nR - 1 - r; } else if (tt >= nT) { tt -= nT; rr = nR - 1 - r; }
                for (int dr = -10; dr <= 10; dr++) {
                    int q = rr + dr;
                    if (q >= 0 && q < nR) acc[tt * nR + q] = 0;
                }
            }
        }
        if (nl < 4) return;
        double[] th = new double[nl], rho = new double[nl];
        for (int i = 0; i < nl; i++) { th[i] = Math.PI * lt[i] / nT; rho[i] = lr[i] - diag; }
        // pairs of near-parallel, well separated lines
        List<int[]> pairs = new ArrayList<int[]>();
        double minSep = 0.15 * Math.min(w, h);
        for (int i = 0; i < nl; i++) for (int j = i + 1; j < nl; j++) {
            if (angDiff(th[i], th[j]) > Math.toRadians(28)) continue;
            // separation measured at image centre
            double di = w / 2.0 * Math.cos(th[i]) + h / 2.0 * Math.sin(th[i]) - rho[i];
            double dj = w / 2.0 * Math.cos(th[j]) + h / 2.0 * Math.sin(th[j]) - rho[j];
            if (angDiffSigned(th[i], th[j])) dj = -dj;
            if (Math.abs(di - dj) < minSep) continue;
            pairs.add(new int[]{i, j});
        }
        final List<float[]> quads = new ArrayList<float[]>();
        final List<Integer> votes = new ArrayList<Integer>();
        for (int a = 0; a < pairs.size(); a++) for (int b = a + 1; b < pairs.size(); b++) {
            int[] p = pairs.get(a), q = pairs.get(b);
            if (p[0] == q[0] || p[0] == q[1] || p[1] == q[0] || p[1] == q[1]) continue;
            double cross = angDiff(th[p[0]], th[q[0]]);
            if (cross < Math.toRadians(45)) continue;
            float[] c1 = lineX(th[p[0]], rho[p[0]], th[q[0]], rho[q[0]]);
            float[] c2 = lineX(th[p[0]], rho[p[0]], th[q[1]], rho[q[1]]);
            float[] c3 = lineX(th[p[1]], rho[p[1]], th[q[1]], rho[q[1]]);
            float[] c4 = lineX(th[p[1]], rho[p[1]], th[q[0]], rho[q[0]]);
            if (c1 == null || c2 == null || c3 == null || c4 == null) continue;
            float[] quad = {c1[0], c1[1], c2[0], c2[1], c3[0], c3[1], c4[0], c4[1]};
            boolean inside = true;
            float mx = w * 0.04f, my = h * 0.04f;
            for (int c = 0; c < 4; c++) {
                if (quad[2 * c] < -mx || quad[2 * c] > w + mx || quad[2 * c + 1] < -my || quad[2 * c + 1] > h + my) { inside = false; break; }
            }
            if (!inside) continue;
            Geom.clamp(quad, w - 1, h - 1);
            quads.add(quad);
            votes.add(lv[p[0]] + lv[p[1]] + lv[q[0]] + lv[q[1]]);
        }
        Integer[] order = new Integer[quads.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final List<Integer> vv = votes;
        Arrays.sort(order, new java.util.Comparator<Integer>() {
            public int compare(Integer x, Integer y) { return vv.get(y) - vv.get(x); }
        });
        for (int i = 0; i < Math.min(60, order.length); i++) out.add(quads.get(order[i]));
    }

    private static double angDiff(double a, double b) {
        double d = Math.abs(a - b) % Math.PI;
        return Math.min(d, Math.PI - d);
    }

    /** True when the two normals are closest across the 0/180 degree wrap (normals point opposite). */
    private static boolean angDiffSigned(double a, double b) {
        return Math.abs(a - b) > Math.PI / 2;
    }

    private static float[] lineX(double t1, double r1, double t2, double r2) {
        double a1 = Math.cos(t1), b1 = Math.sin(t1), a2 = Math.cos(t2), b2 = Math.sin(t2);
        double d = a1 * b2 - a2 * b1;
        if (Math.abs(d) < 1e-6) return null;
        return new float[]{(float) ((r1 * b2 - r2 * b1) / d), (float) ((a1 * r2 - a2 * r1) / d)};
    }

    // ------------------------------------------------------------------ hull -> quad

    static float[] hullQuad(int[] xs, int[] ys, int n) {
        if (n < 4) return null;
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) keys[i] = ((long) xs[i] << 32) | (ys[i] & 0xffffffffL);
        Arrays.sort(keys);
        int[] px = new int[n], py = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int x = (int) (keys[i] >> 32), y = (int) keys[i];
            if (m > 0 && px[m - 1] == x && py[m - 1] == y) continue;
            px[m] = x; py[m] = y; m++;
        }
        if (m < 4) return null;
        int[] hx = new int[2 * m], hy = new int[2 * m];
        int k = 0;
        for (int i = 0; i < m; i++) {
            while (k >= 2 && crossL(hx[k - 2], hy[k - 2], hx[k - 1], hy[k - 1], px[i], py[i]) <= 0) k--;
            hx[k] = px[i]; hy[k] = py[i]; k++;
        }
        for (int i = m - 2, lo = k + 1; i >= 0; i--) {
            while (k >= lo && crossL(hx[k - 2], hy[k - 2], hx[k - 1], hy[k - 1], px[i], py[i]) <= 0) k--;
            hx[k] = px[i]; hy[k] = py[i]; k++;
        }
        k--; // last point equals first
        if (k < 4) return null;
        // Reduce the hull with Visvalingam-Whyatt to keep the search small.
        List<float[]> pts = new ArrayList<float[]>(k);
        for (int i = 0; i < k; i++) pts.add(new float[]{hx[i], hy[i]});
        while (pts.size() > 22) {
            int sz = pts.size(), rm = 0; double minA = Double.MAX_VALUE;
            for (int i = 0; i < sz; i++) {
                float[] a = pts.get((i + sz - 1) % sz), b = pts.get(i), c = pts.get((i + 1) % sz);
                double ar = Math.abs((b[0] - a[0]) * (double) (c[1] - a[1]) - (b[1] - a[1]) * (double) (c[0] - a[0]));
                if (ar < minA) { minA = ar; rm = i; }
            }
            pts.remove(rm);
        }
        int s = pts.size();
        if (s < 4) return null;
        double bestA = -1; int bi = 0, bj = 1, bk = 2, bl = 3;
        for (int i = 0; i < s; i++) for (int j = i + 1; j < s; j++) for (int a = j + 1; a < s; a++) for (int b = a + 1; b < s; b++) {
            double ar = quadArea(pts.get(i), pts.get(j), pts.get(a), pts.get(b));
            if (ar > bestA) { bestA = ar; bi = i; bj = j; bk = a; bl = b; }
        }
        float[] A = pts.get(bi), B = pts.get(bj), C = pts.get(bk), D = pts.get(bl);
        return new float[]{A[0], A[1], B[0], B[1], C[0], C[1], D[0], D[1]};
    }

    private static long crossL(int ox, int oy, int ax, int ay, int bx, int by) {
        return (long) (ax - ox) * (by - oy) - (long) (ay - oy) * (bx - ox);
    }

    private static double quadArea(float[] a, float[] b, float[] c, float[] d) {
        double s = a[0] * (double) b[1] - b[0] * (double) a[1]
                + b[0] * (double) c[1] - c[0] * (double) b[1]
                + c[0] * (double) d[1] - d[0] * (double) c[1]
                + d[0] * (double) a[1] - a[0] * (double) d[1];
        return Math.abs(s) / 2;
    }
}
