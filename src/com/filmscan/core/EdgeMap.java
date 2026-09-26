package com.filmscan.core;

/**
 * Smoothed gray image with Sobel gradients. Used for quad detection, sub-pixel refinement of
 * detected edges and for snapping manually dragged corners/edges onto nearby film or paper edges.
 */
public final class EdgeMap {
    public final int w, h;
    public final float[] gray, gx, gy, mag;
    /** High percentile of gradient magnitude, used to normalise thresholds. */
    public final float strong;

    private EdgeMap(int w, int h, float[] gray) {
        this.w = w; this.h = h; this.gray = gray;
        int n = w * h;
        gx = new float[n]; gy = new float[n]; mag = new float[n];
        float maxMag = 0;
        for (int y = 1; y < h - 1; y++) {
            int r = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = r + x;
                float a = gray[i - w - 1], b = gray[i - w], c = gray[i - w + 1];
                float d = gray[i - 1], f = gray[i + 1];
                float g = gray[i + w - 1], hh = gray[i + w], k = gray[i + w + 1];
                float sx = (c + 2 * f + k) - (a + 2 * d + g);
                float sy = (g + 2 * hh + k) - (a + 2 * b + c);
                gx[i] = sx; gy[i] = sy;
                float m = (float) Math.sqrt(sx * sx + sy * sy);
                mag[i] = m;
                if (m > maxMag) maxMag = m;
            }
        }
        strong = Math.max(24f, percentile(mag, maxMag, 0.985f));
        noise = percentile(mag, maxMag, 0.5f);
    }

    /** Median gradient magnitude (texture / sensor noise floor). */
    public float noise;

    public static EdgeMap fromArgb(int[] argb, int w, int h, int blurRadius) {
        float[] g = new float[w * h];
        for (int i = 0; i < g.length; i++) {
            int p = argb[i];
            g[i] = ((p >> 16) & 255) * 0.299f + ((p >> 8) & 255) * 0.587f + (p & 255) * 0.114f;
        }
        if (blurRadius > 0) {
            boxBlur(g, w, h, blurRadius);
            boxBlur(g, w, h, blurRadius);
        }
        return new EdgeMap(w, h, g);
    }

    static float percentile(float[] v, float max, float p) {
        if (max <= 0) return 0;
        int bins = 1024;
        int[] hist = new int[bins];
        float s = (bins - 1) / max;
        int cnt = 0;
        for (float x : v) { if (x > 0) { hist[(int) (x * s)]++; cnt++; } }
        if (cnt == 0) return 0;
        long target = (long) (cnt * p);
        long acc = 0;
        for (int i = 0; i < bins; i++) {
            acc += hist[i];
            if (acc >= target) return i / s;
        }
        return max;
    }

    /** In-place separable box blur with edge clamping. */
    public static void boxBlur(float[] a, int w, int h, int r) {
        float[] tmp = new float[Math.max(w, h)];
        float inv = 1f / (2 * r + 1);
        for (int y = 0; y < h; y++) {
            int o = y * w;
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += a[o + clampi(k, 0, w - 1)];
            for (int x = 0; x < w; x++) {
                tmp[x] = sum * inv;
                sum += a[o + clampi(x + r + 1, 0, w - 1)] - a[o + clampi(x - r, 0, w - 1)];
            }
            System.arraycopy(tmp, 0, a, o, w);
        }
        for (int x = 0; x < w; x++) {
            float sum = 0;
            for (int k = -r; k <= r; k++) sum += a[clampi(k, 0, h - 1) * w + x];
            for (int y = 0; y < h; y++) {
                tmp[y] = sum * inv;
                sum += a[clampi(y + r + 1, 0, h - 1) * w + x] - a[clampi(y - r, 0, h - 1) * w + x];
            }
            for (int y = 0; y < h; y++) a[y * w + x] = tmp[y];
        }
    }

    static int clampi(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    // ------------------------------------------------------------------ sampling

    /** |gradient . n| at (x,y) with bilinear interpolation, 0 outside. */
    public float aligned(float x, float y, float nx, float ny) {
        if (x < 1 || y < 1 || x >= w - 2 || y >= h - 2) return 0;
        int x0 = (int) x, y0 = (int) y;
        float fx = x - x0, fy = y - y0;
        int i = y0 * w + x0;
        float w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy), w01 = (1 - fx) * fy, w11 = fx * fy;
        float sx = gx[i] * w00 + gx[i + 1] * w10 + gx[i + w] * w01 + gx[i + w + 1] * w11;
        float sy = gy[i] * w00 + gy[i + 1] * w10 + gy[i + w] * w01 + gy[i + w + 1] * w11;
        return Math.abs(sx * nx + sy * ny);
    }

    /** Signed gradient along n (nearest neighbour). */
    float signedAt(int x, int y, float nx, float ny) {
        if (x < 1 || y < 1 || x >= w - 1 || y >= h - 1) return 0;
        int i = y * w + x;
        return gx[i] * nx + gy[i] * ny;
    }

    /** Mean aligned gradient along the segment a-&gt;b, sampling t in [t0,t1]. */
    public float segmentStrength(float ax, float ay, float bx, float by, float t0, float t1) {
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 4) return 0;
        float nx = -dy / len, ny = dx / len;
        int n = Math.max(4, (int) (len * (t1 - t0) / 1.5f));
        float sum = 0;
        for (int i = 0; i <= n; i++) {
            float t = t0 + (t1 - t0) * i / n;
            float px = ax + dx * t, py = ay + dy * t;
            // allow 1px slack across the line
            float v = aligned(px, py, nx, ny);
            float v1 = aligned(px + nx, py + ny, nx, ny);
            float v2 = aligned(px - nx, py - ny, nx, ny);
            sum += Math.max(v, Math.max(v1, v2) * 0.9f);
        }
        return sum / (n + 1);
    }

    /** Threshold above which a gradient counts as a real edge. */
    public float edgeThreshold() {
        return Math.max(10f, Math.max(2.2f * noise, 0.10f * strong));
    }

    /**
     * Fraction of each side lying on an edge of consistent polarity. Returns {min over sides, mean}.
     */
    public float[] support(float[] q) {
        float thr = edgeThreshold();
        float min = 1, sum = 0;
        for (int s = 0; s < 4; s++) {
            int e = (s + 1) & 3;
            float v = sideSupport(q[2 * s], q[2 * s + 1], q[2 * e], q[2 * e + 1], thr);
            min = Math.min(min, v);
            sum += v;
        }
        return new float[]{min, sum / 4};
    }

    private float sideSupport(float ax, float ay, float bx, float by, float thr) {
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 8) return 0;
        float nx = -dy / len, ny = dx / len;
        int n = Math.max(8, (int) (len / 2));
        int pos = 0, neg = 0, total = 0;
        for (int i = 0; i <= n; i++) {
            float t = 0.05f + 0.9f * i / n;
            float px = ax + dx * t, py = ay + dy * t;
            total++;
            if (px < 2 || py < 2 || px > w - 3 || py > h - 3) continue;
            float best = 0;
            for (int d = -2; d <= 2; d++) {
                float v = signedAt((int) (px + nx * d + 0.5f), (int) (py + ny * d + 0.5f), nx, ny);
                if (Math.abs(v) > Math.abs(best)) best = v;
            }
            if (best >= thr) pos++;
            else if (best <= -thr) neg++;
        }
        return total == 0 ? 0 : Math.max(pos, neg) / (float) total;
    }

    // ------------------------------------------------------------------ refinement

    /**
     * Refines each side by searching along its normal for the strongest edge and robustly fitting
     * a line through the found points. Returns a new quad or the input if refinement is unreliable.
     */
    public float[] refine(float[] q, float searchRadius) {
        double[][] lines = new double[4][];
        float thr = Math.max(12f, strong * 0.22f);
        for (int s = 0; s < 4; s++) {
            int e = (s + 1) & 3;
            lines[s] = fitSide(q[2 * s], q[2 * s + 1], q[2 * e], q[2 * e + 1], searchRadius, thr);
        }
        float[] r = new float[8];
        for (int c = 0; c < 4; c++) {
            int ps = (c + 3) & 3; // side ending at corner c
            double[] l1 = lines[ps], l2 = lines[c];
            float[] p = Geom.intersect(l1[0], l1[1], l1[2], l1[3], l2[0], l2[1], l2[2], l2[3]);
            if (p == null) return q;
            float moved = (float) Math.hypot(p[0] - q[2 * c], p[1] - q[2 * c + 1]);
            if (moved > searchRadius * 3 + 2) return q;
            r[2 * c] = p[0]; r[2 * c + 1] = p[1];
        }
        float mx = w * 0.02f + 2, my = h * 0.02f + 2;
        for (int c = 0; c < 4; c++) {
            if (r[2 * c] < -mx || r[2 * c] > w + mx || r[2 * c + 1] < -my || r[2 * c + 1] > h + my) return q;
        }
        Geom.clamp(r, w, h);
        if (!Geom.isConvex(r)) return q;
        double a0 = Geom.area(q), a1 = Geom.area(r);
        if (a1 < a0 * 0.8 || a1 > a0 * 1.25) return q;
        return r;
    }

    /** Returns line as {x1,y1,x2,y2}; the original side if no reliable edge is found. */
    private double[] fitSide(float ax, float ay, float bx, float by, float R, float thr) {
        double[] orig = {ax, ay, bx, by};
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 10) return orig;
        float nx = -dy / len, ny = dx / len;
        int m = Math.max(12, Math.min(160, (int) (len / 3)));
        float[] px = new float[m], py = new float[m];
        float[] sgn = new float[m];
        int cnt = 0;
        float step = 0.5f;
        for (int i = 0; i < m; i++) {
            float t = 0.08f + 0.84f * i / (m - 1);
            float cx = ax + dx * t, cy = ay + dy * t;
            float best = 0, bestD = 0, prev, next;
            int steps = (int) (R / step);
            for (int k = -steps; k <= steps; k++) {
                float d = k * step;
                float v = aligned(cx + nx * d, cy + ny * d, nx, ny);
                if (v > best) { best = v; bestD = d; }
            }
            if (best < thr) continue;
            // parabolic sub-pixel fit
            prev = aligned(cx + nx * (bestD - step), cy + ny * (bestD - step), nx, ny);
            next = aligned(cx + nx * (bestD + step), cy + ny * (bestD + step), nx, ny);
            float den = prev - 2 * best + next;
            float off = den < -1e-6f ? 0.5f * (prev - next) / den * step : 0;
            if (Math.abs(off) > step) off = 0;
            float d = bestD + off;
            px[cnt] = cx + nx * d; py[cnt] = cy + ny * d;
            sgn[cnt] = signedAt((int) (px[cnt] + 0.5f), (int) (py[cnt] + 0.5f), nx, ny);
            cnt++;
        }
        if (cnt < m * 0.35f) return orig;
        // keep the dominant gradient polarity (a real border has one consistent polarity)
        int pos = 0;
        for (int i = 0; i < cnt; i++) if (sgn[i] > 0) pos++;
        boolean keepPos = pos * 2 >= cnt;
        boolean[] use = new boolean[cnt];
        int used = 0;
        for (int i = 0; i < cnt; i++) { use[i] = (sgn[i] > 0) == keepPos || sgn[i] == 0; if (use[i]) used++; }
        if (used < m * 0.3f) return orig;
        double[] line = null;
        for (int iter = 0; iter < 3; iter++) {
            line = fitLine(px, py, use, cnt);
            if (line == null) return orig;
            // residuals
            double lx = line[2] - line[0], ly = line[3] - line[1];
            double ll = Math.hypot(lx, ly);
            double lnx = -ly / ll, lny = lx / ll;
            double[] res = new double[cnt];
            int k = 0;
            double[] tmp = new double[cnt];
            for (int i = 0; i < cnt; i++) {
                res[i] = Math.abs((px[i] - line[0]) * lnx + (py[i] - line[1]) * lny);
                if (use[i]) tmp[k++] = res[i];
            }
            java.util.Arrays.sort(tmp, 0, k);
            double med = tmp[k / 2];
            double lim = Math.max(0.8, med * 2.5);
            used = 0;
            for (int i = 0; i < cnt; i++) {
                boolean pol = (sgn[i] > 0) == keepPos || sgn[i] == 0;
                use[i] = pol && res[i] <= lim;
                if (use[i]) used++;
            }
            if (used < m * 0.3f) return orig;
        }
        line = fitLine(px, py, use, cnt);
        if (line == null) return orig;
        // reject if the fitted line tilts far from the original side
        double lx = line[2] - line[0], ly = line[3] - line[1];
        double cos = Math.abs(lx * dx + ly * dy) / (Math.hypot(lx, ly) * len);
        if (cos < Math.cos(Math.toRadians(6))) return orig;
        return line;
    }

    /** Total least squares line through selected points, returned as two points. */
    private static double[] fitLine(float[] px, float[] py, boolean[] use, int cnt) {
        double sx = 0, sy = 0; int n = 0;
        for (int i = 0; i < cnt; i++) if (use[i]) { sx += px[i]; sy += py[i]; n++; }
        if (n < 3) return null;
        double mx = sx / n, my = sy / n, sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < cnt; i++) if (use[i]) {
            double ddx = px[i] - mx, ddy = py[i] - my;
            sxx += ddx * ddx; syy += ddy * ddy; sxy += ddx * ddy;
        }
        double theta = 0.5 * Math.atan2(2 * sxy, sxx - syy);
        double ux = Math.cos(theta), uy = Math.sin(theta);
        return new double[]{mx - ux * 100, my - uy * 100, mx + ux * 100, my + uy * 100};
    }

    // ------------------------------------------------------------------ snapping

    /**
     * Snap corner {@code c} of quad {@code q} to a nearby position where both adjacent sides lie on
     * strong edges. Returns the new position or null if nothing convincing is nearby.
     */
    public float[] snapCorner(float[] q, int c, float radius, int stepPx) {
        int pc = (c + 3) & 3, nc = (c + 1) & 3;
        float cx = q[2 * c], cy = q[2 * c + 1];
        float pxp = q[2 * pc], pyp = q[2 * pc + 1], pxn = q[2 * nc], pyn = q[2 * nc + 1];
        float base = cornerScore(cx, cy, pxp, pyp, pxn, pyn);
        float best = -1, bx = cx, by = cy;
        int r = (int) Math.ceil(radius);
        float r2 = radius * radius;
        for (int dy = -r; dy <= r; dy += stepPx) {
            for (int dx = -r; dx <= r; dx += stepPx) {
                if (dx * dx + dy * dy > r2) continue;
                float x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x > w - 1 || y > h - 1) continue;
                float s = cornerScore(x, y, pxp, pyp, pxn, pyn);
                // tiny preference for staying close
                s *= 1f - 0.08f * (float) Math.sqrt(dx * dx + dy * dy) / (radius + 1);
                if (s > best) { best = s; bx = x; by = y; }
            }
        }
        float need = Math.max(10f, strong * 0.2f);
        if (best >= need && best >= base * 1.05f) return new float[]{bx, by};
        if (base >= need) return new float[]{cx, cy};
        return null;
    }

    private float cornerScore(float x, float y, float ax, float ay, float bx, float by) {
        // only the part of each side near the corner (other corners may be inaccurate)
        float la = (float) Math.hypot(ax - x, ay - y), lb = (float) Math.hypot(bx - x, by - y);
        float ta = la < 1 ? 0.5f : Math.min(0.5f, Math.max(0.15f, 140f / la));
        float tb = lb < 1 ? 0.5f : Math.min(0.5f, Math.max(0.15f, 140f / lb));
        float s1 = segmentStrength(x, y, ax, ay, 0.02f, ta);
        float s2 = segmentStrength(x, y, bx, by, 0.02f, tb);
        // both sides must be edges: harmonic-like combination
        return 2 * s1 * s2 / (s1 + s2 + 1e-3f);
    }

    /**
     * Snap side {@code s} (corner s to s+1) to the strongest nearby straight edge. The side may
     * shift along its normal and tilt slightly; its ends slide along the neighbouring sides.
     * Returns a new quad or null.
     */
    public float[] snapSide(float[] q, int s, float radius) {
        int e = (s + 1) & 3, ps = (s + 3) & 3, ne = (s + 2) & 3;
        float ax = q[2 * s], ay = q[2 * s + 1], bx = q[2 * e], by = q[2 * e + 1];
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 10) return null;
        float nx = -dy / len, ny = dx / len;
        float mx = (ax + bx) / 2, my = (ay + by) / 2;
        float base = segmentStrength(ax, ay, bx, by, 0.06f, 0.94f);
        float best = -1; float[] bestQ = null;
        int R = (int) Math.ceil(radius);
        for (int ai = -4; ai <= 4; ai++) {
            double ang = Math.toRadians(ai * 0.75);
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double ux = (dx * ca - dy * sa) / len, uy = (dx * sa + dy * ca) / len;
            for (int d = -R; d <= R; d++) {
                double cx = mx + nx * d, cy = my + ny * d;
                double l1x = cx - ux * len, l1y = cy - uy * len, l2x = cx + ux * len, l2y = cy + uy * len;
                float[] pa = Geom.intersect(l1x, l1y, l2x, l2y, q[2 * ps], q[2 * ps + 1], ax, ay);
                float[] pb = Geom.intersect(l1x, l1y, l2x, l2y, bx, by, q[2 * ne], q[2 * ne + 1]);
                if (pa == null || pb == null) continue;
                if (pa[0] < -2 || pa[1] < -2 || pa[0] > w + 1 || pa[1] > h + 1) continue;
                if (pb[0] < -2 || pb[1] < -2 || pb[0] > w + 1 || pb[1] > h + 1) continue;
                float sc = segmentStrength(pa[0], pa[1], pb[0], pb[1], 0.06f, 0.94f);
                sc *= 1f - 0.08f * Math.abs(d) / (radius + 1) - 0.01f * Math.abs(ai);
                if (sc > best) {
                    best = sc;
                    float[] nq = q.clone();
                    nq[2 * s] = pa[0]; nq[2 * s + 1] = pa[1]; nq[2 * e] = pb[0]; nq[2 * e + 1] = pb[1];
                    bestQ = nq;
                }
            }
        }
        float need = Math.max(10f, strong * 0.2f);
        if (bestQ == null || best < need || best < base * 1.05f) return null;
        Geom.clamp(bestQ, w, h);
        return Geom.isConvex(bestQ) ? bestQ : null;
    }
}
