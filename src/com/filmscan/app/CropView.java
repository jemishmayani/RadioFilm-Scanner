package com.filmscan.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import com.filmscan.core.EdgeMap;
import com.filmscan.core.Geom;

/**
 * Shows the photo with an editable quad. Corners and edge midpoints can be dragged; a loupe shows
 * the pixels under the finger, and on release the handle snaps to the nearest film/paper edge.
 * The photo is shown turned by the page's rotation so it looks the way it will be saved.
 * A horizontal swipe that does not start on a handle is reported as a page change.
 */
public final class CropView extends View {
    public interface Listener {
        void onQuadChanged(boolean valid);
    }

    private Bitmap bmp;
    private final float[] q = new float[8];   // bitmap pixel coords
    private final float[] vq = new float[8];  // view coords
    private EdgeMap em;
    private float emScale = 1f;
    private boolean snapEnabled = true;
    private int rot;
    private float scale = 1;
    private final Matrix m = new Matrix(), inv = new Matrix(), lm = new Matrix();
    private final RectF imgRect = new RectF();
    private int active = -1;                  // 0..3 corner, 4..7 side
    private float grabDx, grabDy, lastX, lastY, downX, downY;
    private boolean swiping, swActive;
    private PageSwiper.Gesture swipe;
    private VelocityTracker vt;
    private float[] snapPreview;
    private long lastSnapCalc;
    private final boolean[] snapped = new boolean[4];
    private long snapFlashAt;
    private ValueAnimator anim;
    private Listener listener;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim = new Paint(), edge = new Paint(Paint.ANTI_ALIAS_FLAG),
            handleFill = new Paint(Paint.ANTI_ALIAS_FLAG), handleRing = new Paint(Paint.ANTI_ALIAS_FLAG),
            loupeRing = new Paint(Paint.ANTI_ALIAS_FLAG), cross = new Paint(Paint.ANTI_ALIAS_FLAG),
            ghost = new Paint(Paint.ANTI_ALIAS_FLAG), pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path(), clip = new Path();
    private final float dp;

    public CropView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        scrim.setColor(Ui.SCRIM);
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(2 * dp); edge.setColor(Ui.ACCENT);
        handleRing.setStyle(Paint.Style.STROKE); handleRing.setStrokeWidth(2.5f * dp); handleRing.setColor(Ui.ACCENT);
        loupeRing.setStyle(Paint.Style.STROKE); loupeRing.setStrokeWidth(3 * dp); loupeRing.setColor(Ui.LIGHT);
        cross.setStyle(Paint.Style.STROKE); cross.setStrokeWidth(1.2f * dp); cross.setColor(Ui.ACCENT);
        ghost.setStyle(Paint.Style.STROKE); ghost.setStrokeWidth(2 * dp); ghost.setColor(Ui.LIGHT);
        pill.setStrokeCap(Paint.Cap.ROUND); pill.setStrokeWidth(6 * dp); pill.setColor(Ui.ACCENT);
    }

    public void setListener(Listener l) { listener = l; }

    public void setSwipeListener(PageSwiper.Gesture g) { swipe = g; }

    public void setSnapEnabled(boolean s) { snapEnabled = s; }

    public void setEdgeMap(EdgeMap map) {
        em = map;
        if (bmp != null && em != null) emScale = em.w / (float) bmp.getWidth();
    }

    public void setImage(Bitmap b, float[] normQuad, int rotation) {
        if (anim != null) anim.cancel();
        bmp = b;
        rot = rotation & 3;
        active = -1;
        for (int i = 0; i < 4; i++) snapped[i] = false;
        if (em != null) emScale = em.w / (float) b.getWidth();
        setQuadNormalized(normQuad, false);
        layoutImage();
    }

    public void clear() {
        if (anim != null) anim.cancel();
        bmp = null;
        em = null;
        invalidate();
    }

    public void setRotationSteps(int r) {
        rot = r & 3;
        layoutImage();
    }

    public boolean hasImage() { return bmp != null; }

    public void setQuadNormalized(float[] nq, boolean animate) {
        if (bmp == null) return;
        float w = bmp.getWidth(), h = bmp.getHeight();
        float[] target = nq == null ? Geom.fullQuad(w, h) : Geom.scale(nq, w, h);
        Geom.clamp(target, w, h);
        if (animate) animateTo(target, -1);
        else { System.arraycopy(target, 0, q, 0, 8); notifyChanged(); invalidate(); }
    }

    public float[] getQuadNormalized() {
        if (bmp == null) return null;
        return Geom.scale(q, 1f / bmp.getWidth(), 1f / bmp.getHeight());
    }

    public boolean isValid() { return Geom.isConvex(q) && Geom.area(q) > 64; }

    public boolean isFull() {
        if (bmp == null) return true;
        float[] f = Geom.fullQuad(bmp.getWidth(), bmp.getHeight());
        for (int i = 0; i < 8; i++) if (Math.abs(f[i] - q[i]) > 1.5f) return false;
        return true;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { layoutImage(); }

    private void layoutImage() {
        if (bmp == null || getWidth() == 0) { invalidate(); return; }
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        float rw = (rot & 1) == 1 ? bh : bw, rh = (rot & 1) == 1 ? bw : bh;
        float pad = 26 * dp;
        scale = Math.min((getWidth() - 2 * pad) / rw, (getHeight() - 2 * pad) / rh);
        m.reset();
        m.postTranslate(-bw / 2f, -bh / 2f);
        m.postRotate(90 * rot);
        m.postScale(scale, scale);
        m.postTranslate(getWidth() / 2f, getHeight() / 2f);
        m.invert(inv);
        imgRect.set(0, 0, bw, bh);
        m.mapRect(imgRect);
        invalidate();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        c.drawBitmap(bmp, m, bmpPaint);
        m.mapPoints(vq, q);

        boolean valid = isValid();
        path.reset();
        path.moveTo(vq[0], vq[1]);
        for (int i = 1; i < 4; i++) path.lineTo(vq[2 * i], vq[2 * i + 1]);
        path.close();
        clip.reset();
        clip.setFillType(Path.FillType.EVEN_ODD);
        clip.addRect(imgRect, Path.Direction.CW);
        clip.addPath(path);
        c.drawPath(clip, scrim);
        edge.setColor(valid ? Ui.ACCENT : Ui.DANGER);
        c.drawPath(path, edge);

        pill.setColor(valid ? Ui.ACCENT : Ui.DANGER);
        for (int s = 0; s < 4; s++) {
            int e = (s + 1) & 3;
            float ax = vq[2 * s], ay = vq[2 * s + 1], bx = vq[2 * e], by = vq[2 * e + 1];
            float mx = (ax + bx) / 2, my = (ay + by) / 2, len = (float) Math.hypot(bx - ax, by - ay);
            if (len < 60 * dp) continue;
            float ux = (bx - ax) / len * 11 * dp, uy = (by - ay) / len * 11 * dp;
            c.drawLine(mx - ux, my - uy, mx + ux, my + uy, pill);
        }
        long since = SystemClock.uptimeMillis() - snapFlashAt;
        for (int i = 0; i < 4; i++) {
            float x = vq[2 * i], y = vq[2 * i + 1];
            float r = (active == i ? 15 : 12) * dp;
            handleFill.setColor(snapped[i] ? Ui.accentA(0xCC) : 0x66000000);
            c.drawCircle(x, y, r, handleFill);
            handleRing.setColor(valid ? Ui.ACCENT : Ui.DANGER);
            c.drawCircle(x, y, r, handleRing);
            if (snapped[i] && since < 450) {
                float k = since / 450f;
                ghost.setAlpha((int) (255 * (1 - k)));
                c.drawCircle(x, y, r + 14 * dp * k, ghost);
                postInvalidateOnAnimation();
            }
        }
        if (active >= 0 && active < 4 && snapPreview != null) {
            float[] p = {snapPreview[0], snapPreview[1]};
            m.mapPoints(p);
            ghost.setAlpha(230);
            c.drawCircle(p[0], p[1], 5 * dp, ghost);
        }
        if (active >= 0) drawLoupe(c);
    }

    private void drawLoupe(Canvas c) {
        float[] p = new float[2];
        if (active < 4) { p[0] = q[2 * active]; p[1] = q[2 * active + 1]; }
        else {
            int s = active - 4, e = (s + 1) & 3;
            p[0] = (q[2 * s] + q[2 * e]) / 2; p[1] = (q[2 * s + 1] + q[2 * e + 1]) / 2;
        }
        m.mapPoints(p);
        float R = 58 * dp;
        float cx = 16 * dp + R, cy = 16 * dp + R;
        if (lastX < getWidth() / 2f && lastY < cy + R + 40 * dp) cx = getWidth() - 16 * dp - R;
        lm.set(m);
        lm.postScale(3f, 3f, p[0], p[1]);
        lm.postTranslate(cx - p[0], cy - p[1]);
        c.save();
        clip.reset();
        clip.addCircle(cx, cy, R, Path.Direction.CW);
        c.clipPath(clip);
        c.drawColor(Ui.BG);
        c.drawBitmap(bmp, lm, bmpPaint);
        float[] lq = new float[8];
        lm.mapPoints(lq, q);
        path.reset();
        path.moveTo(lq[0], lq[1]);
        for (int i = 1; i < 4; i++) path.lineTo(lq[2 * i], lq[2 * i + 1]);
        path.close();
        c.drawPath(path, edge);
        c.restore();
        c.drawLine(cx - 10 * dp, cy, cx + 10 * dp, cy, cross);
        c.drawLine(cx, cy - 10 * dp, cx, cy + 10 * dp, cross);
        c.drawCircle(cx, cy, R, loupeRing);
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                m.mapPoints(vq, q);
                active = pick(x, y);
                lastX = x; lastY = y;
                if (active < 0) {
                    swiping = true;
                    swActive = false;
                    downX = e.getRawX(); downY = e.getRawY();
                    if (vt != null) vt.recycle();
                    vt = VelocityTracker.obtain();
                    trackRaw(e);
                    return true;
                }
                if (anim != null) anim.cancel();
                if (active < 4) { grabDx = vq[2 * active] - x; grabDy = vq[2 * active + 1] - y; }
                snapPreview = null;
                getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (swiping) {
                    trackRaw(e);
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!swActive && swipe != null && Math.abs(dx) > ViewConfiguration.get(getContext()).getScaledTouchSlop()
                            && Math.abs(dx) > 1.3f * Math.abs(dy)) {
                        swActive = true;
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swActive) swipe.onSwipeDrag(dx);
                    return true;
                }
                if (active < 0) return true;
                float bw = bmp.getWidth(), bh = bmp.getHeight();
                if (active < 4) {
                    float[] p = {x + grabDx, y + grabDy};
                    inv.mapPoints(p);
                    q[2 * active] = clamp(p[0], 0, bw);
                    q[2 * active + 1] = clamp(p[1], 0, bh);
                    snapped[active] = false;
                    long now = SystemClock.uptimeMillis();
                    if (snapEnabled && em != null && now - lastSnapCalc > 50) {
                        lastSnapCalc = now;
                        float[] t = em.snapCorner(toEm(q), active, snapRadius(), 3);
                        snapPreview = t == null ? null : new float[]{t[0] / emScale, t[1] / emScale};
                    }
                } else {
                    float[] d = {x - lastX, y - lastY};
                    inv.mapVectors(d);
                    moveSide(active - 4, d[0], d[1]);
                }
                lastX = x; lastY = y;
                notifyChanged();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (swiping) {
                    swiping = false;
                    trackRaw(e);
                    if (swActive && swipe != null) {
                        float vx = 0;
                        if (vt != null) { vt.computeCurrentVelocity(1000); vx = vt.getXVelocity(); }
                        if (e.getActionMasked() == MotionEvent.ACTION_UP) swipe.onSwipeRelease(e.getRawX() - downX, vx);
                        else swipe.onSwipeRelease(0, 0);
                    }
                    swActive = false;
                    if (vt != null) { vt.recycle(); vt = null; }
                    return true;
                }
                if (active < 0) return true;
                int a = active;
                active = -1;
                snapPreview = null;
                if (snapEnabled && em != null && e.getActionMasked() == MotionEvent.ACTION_UP) snap(a);
                notifyChanged();
                invalidate();
                return true;
            }
            default:
                return true;
        }
    }

    private void trackRaw(MotionEvent e) {
        if (vt == null) return;
        MotionEvent raw = MotionEvent.obtain(e);
        raw.setLocation(e.getRawX(), e.getRawY());
        vt.addMovement(raw);
        raw.recycle();
    }

    private int pick(float x, float y) {
        float best = 40 * dp; int idx = -1;
        for (int i = 0; i < 4; i++) {
            float d = (float) Math.hypot(vq[2 * i] - x, vq[2 * i + 1] - y);
            if (d < best) { best = d; idx = i; }
        }
        if (idx >= 0) return idx;
        best = 32 * dp;
        for (int s = 0; s < 4; s++) {
            int e = (s + 1) & 3;
            float mx = (vq[2 * s] + vq[2 * e]) / 2, my = (vq[2 * s + 1] + vq[2 * e + 1]) / 2;
            float d = (float) Math.hypot(mx - x, my - y);
            if (d < best) { best = d; idx = 4 + s; }
        }
        return idx;
    }

    /** Moves side s along its normal; its ends slide along the neighbouring sides. */
    private void moveSide(int s, float dx, float dy) {
        int e = (s + 1) & 3, ps = (s + 3) & 3, ne = (s + 2) & 3;
        float ax = q[2 * s], ay = q[2 * s + 1], bx = q[2 * e], by = q[2 * e + 1];
        float len = (float) Math.hypot(bx - ax, by - ay);
        if (len < 1) return;
        float nx = -(by - ay) / len, ny = (bx - ax) / len;
        float d = dx * nx + dy * ny;
        float a2x = ax + nx * d, a2y = ay + ny * d, b2x = bx + nx * d, b2y = by + ny * d;
        float[] pa = Geom.intersect(a2x, a2y, b2x, b2y, q[2 * ps], q[2 * ps + 1], ax, ay);
        float[] pb = Geom.intersect(a2x, a2y, b2x, b2y, bx, by, q[2 * ne], q[2 * ne + 1]);
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        if (pa == null || pb == null || !inside(pa, bw, bh) || !inside(pb, bw, bh)) {
            pa = new float[]{a2x, a2y}; pb = new float[]{b2x, b2y};
        }
        float[] nq = q.clone();
        nq[2 * s] = clamp(pa[0], 0, bw); nq[2 * s + 1] = clamp(pa[1], 0, bh);
        nq[2 * e] = clamp(pb[0], 0, bw); nq[2 * e + 1] = clamp(pb[1], 0, bh);
        if (Geom.isConvex(nq)) {
            System.arraycopy(nq, 0, q, 0, 8);
            snapped[s] = false; snapped[e] = false;
        }
    }

    private static boolean inside(float[] p, float w, float h) {
        return p[0] >= -w * 0.02f && p[1] >= -h * 0.02f && p[0] <= w * 1.02f && p[1] <= h * 1.02f;
    }

    private float snapRadius() { return Math.max(12f, Math.max(em.w, em.h) * 0.028f); }

    private float[] toEm(float[] a) { return Geom.scale(a, emScale, emScale); }

    private void snap(int a) {
        float[] qe = toEm(q);
        float[] target = null;
        if (a < 4) {
            float[] t = em.snapCorner(qe, a, snapRadius(), 1);
            if (t != null) {
                target = q.clone();
                target[2 * a] = t[0] / emScale; target[2 * a + 1] = t[1] / emScale;
                Geom.clamp(target, bmp.getWidth(), bmp.getHeight());
                if (!Geom.isConvex(target)) target = null;
            }
        } else {
            float[] t = em.snapSide(qe, a - 4, snapRadius() * 0.8f);
            if (t != null) target = Geom.scale(t, 1f / emScale, 1f / emScale);
        }
        if (target != null) {
            if (a < 4) snapped[a] = true;
            else { snapped[a - 4] = true; snapped[(a - 3) & 3] = true; }
            snapFlashAt = SystemClock.uptimeMillis();
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            animateTo(target, a);
        }
    }

    private void animateTo(final float[] target, int which) {
        if (anim != null) anim.cancel();
        final float[] from = q.clone();
        anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(140);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator va) {
                float t = (Float) va.getAnimatedValue();
                for (int i = 0; i < 8; i++) q[i] = from[i] + (target[i] - from[i]) * t;
                notifyChanged();
                invalidate();
            }
        });
        anim.start();
    }

    private void notifyChanged() { if (listener != null) listener.onQuadChanged(isValid()); }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
