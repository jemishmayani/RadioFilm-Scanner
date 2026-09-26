package com.filmscan.core;

/** Quad geometry helpers. A quad is float[8]: x0,y0 (TL), x1,y1 (TR), x2,y2 (BR), x3,y3 (BL). */
public final class Geom {
    private Geom() {}

    public static double signedArea(float[] q) {
        double a = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            a += (double) q[2 * i] * q[2 * j + 1] - (double) q[2 * j] * q[2 * i + 1];
        }
        return a / 2.0;
    }

    public static double area(float[] q) { return Math.abs(signedArea(q)); }

    /** True if the quad is convex and ordered clockwise on screen (TL, TR, BR, BL). */
    public static boolean isConvex(float[] q) {
        for (int i = 0; i < 4; i++) {
            float ax = q[2 * i], ay = q[2 * i + 1];
            int j = (i + 1) & 3, k = (i + 2) & 3;
            float bx = q[2 * j], by = q[2 * j + 1];
            float cx = q[2 * k], cy = q[2 * k + 1];
            double cr = (double) (bx - ax) * (cy - by) - (double) (by - ay) * (cx - bx);
            if (cr <= 1e-6) return false;
        }
        return true;
    }

    /** Orders any 4 points as TL, TR, BR, BL (clockwise on screen). */
    public static float[] orderCorners(float[] p) {
        double cx = (p[0] + p[2] + p[4] + p[6]) / 4.0, cy = (p[1] + p[3] + p[5] + p[7]) / 4.0;
        Integer[] idx = {0, 1, 2, 3};
        final double[] ang = new double[4];
        for (int i = 0; i < 4; i++) ang[i] = Math.atan2(p[2 * i + 1] - cy, p[2 * i] - cx);
        java.util.Arrays.sort(idx, new java.util.Comparator<Integer>() {
            public int compare(Integer a, Integer b) { return Double.compare(ang[a], ang[b]); }
        });
        int start = 0; double best = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            double s = p[2 * idx[i]] + p[2 * idx[i] + 1];
            if (s < best) { best = s; start = i; }
        }
        float[] out = new float[8];
        for (int i = 0; i < 4; i++) {
            int k = idx[(start + i) & 3];
            out[2 * i] = p[2 * k]; out[2 * i + 1] = p[2 * k + 1];
        }
        return out;
    }

    public static double dist(float[] q, int a, int b) {
        double dx = q[2 * a] - q[2 * b], dy = q[2 * a + 1] - q[2 * b + 1];
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Intersection of line (p1,p2) with line (p3,p4). Returns null if (nearly) parallel. */
    public static float[] intersect(double x1, double y1, double x2, double y2,
                                    double x3, double y3, double x4, double y4) {
        double d = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4);
        if (Math.abs(d) < 1e-9) return null;
        double a = x1 * y2 - y1 * x2, b = x3 * y4 - y3 * x4;
        return new float[]{(float) ((a * (x3 - x4) - (x1 - x2) * b) / d),
                (float) ((a * (y3 - y4) - (y1 - y2) * b) / d)};
    }

    public static float[] fullQuad(float w, float h) {
        return new float[]{0, 0, w, 0, w, h, 0, h};
    }

    public static float[] scale(float[] q, float sx, float sy) {
        float[] r = new float[8];
        for (int i = 0; i < 4; i++) { r[2 * i] = q[2 * i] * sx; r[2 * i + 1] = q[2 * i + 1] * sy; }
        return r;
    }

    public static void clamp(float[] q, float w, float h) {
        for (int i = 0; i < 4; i++) {
            q[2 * i] = Math.max(0, Math.min(w, q[2 * i]));
            q[2 * i + 1] = Math.max(0, Math.min(h, q[2 * i + 1]));
        }
    }

    /**
     * Output size for a perspective-corrected quad. Estimates the true aspect ratio of the
     * photographed rectangle from the camera geometry (Zhang &amp; He, whiteboard scanning) and
     * falls back to side lengths when the estimate is unreliable. (cx, cy) is the image centre.
     */
    public static double[] outputSize(float[] q, double imgW, double imgH) {
        double top = dist(q, 0, 1), bottom = dist(q, 3, 2), left = dist(q, 0, 3), right = dist(q, 1, 2);
        double w0 = Math.max(top, bottom), h0 = Math.max(left, right);
        if (h0 < 1 || w0 < 1) return new double[]{Math.max(1, w0), Math.max(1, h0)};
        double sideRatio = ((top + bottom) / 2.0) / ((left + right) / 2.0);
        double r = aspectRatio(q, imgW / 2.0, imgH / 2.0, Math.hypot(imgW, imgH));
        if (!(r > 0) || r > sideRatio * 1.5 || r < sideRatio / 1.5) return new double[]{w0, h0};
        if (w0 / h0 > r) return new double[]{w0, w0 / r};
        return new double[]{h0 * r, h0};
    }

    /** Width/height ratio of the real rectangle, or NaN if it cannot be estimated reliably. */
    public static double aspectRatio(float[] q, double cx, double cy, double diag) {
        double[] m1 = {q[0] - cx, q[1] - cy, 1};
        double[] m2 = {q[2] - cx, q[3] - cy, 1};
        double[] m4 = {q[4] - cx, q[5] - cy, 1};
        double[] m3 = {q[6] - cx, q[7] - cy, 1};
        double d2 = dot(cross(m2, m4), m3), d3 = dot(cross(m3, m4), m2);
        if (Math.abs(d2) < 1e-9 || Math.abs(d3) < 1e-9) return Double.NaN;
        double k2 = dot(cross(m1, m4), m3) / d2;
        double k3 = dot(cross(m1, m4), m2) / d3;
        double[] n2 = {k2 * m2[0] - m1[0], k2 * m2[1] - m1[1], k2 * m2[2] - m1[2]};
        double[] n3 = {k3 * m3[0] - m1[0], k3 * m3[1] - m1[1], k3 * m3[2] - m1[2]};
        double a2 = n2[0] * n2[0] + n2[1] * n2[1], a3 = n3[0] * n3[0] + n3[1] * n3[1];
        if (a3 < 1e-9) return Double.NaN;
        // Nearly affine view: no usable perspective cue, ratio follows directly.
        if (Math.abs(k2 - 1) < 0.01 && Math.abs(k3 - 1) < 0.01) return Math.sqrt(a2 / a3);
        double den = n2[2] * n3[2];
        if (Math.abs(den) < 1e-12) return Math.sqrt(a2 / a3);
        double f2 = -(n2[0] * n3[0] + n2[1] * n3[1]) / den;
        if (!(f2 > 0)) return Double.NaN;
        double f = Math.sqrt(f2);
        if (f < 0.3 * diag || f > 5 * diag) return Double.NaN;
        return Math.sqrt((a2 + f2 * n2[2] * n2[2]) / (a3 + f2 * n3[2] * n3[2]));
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
}
