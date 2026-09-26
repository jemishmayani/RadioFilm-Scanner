package com.filmscan.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.List;

/**
 * Pinch/double-tap zoomable image with overlays for the panel grid and privacy boxes. In
 * "hide info" mode a one-finger drag draws a box instead of panning.
 */
public final class ZoomImageView extends View {
    public interface RedactListener { void onBoxDrawn(float[] box); }


    private Bitmap bmp;
    private final Matrix m = new Matrix(), inv = new Matrix();
    private float fitScale = 1;
    private final ScaleGestureDetector sgd;
    private final GestureDetector gd;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG), gridP = new Paint(Paint.ANTI_ALIAS_FLAG),
            boxP = new Paint(), boxLine = new Paint(Paint.ANTI_ALIAS_FLAG), drawP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int gridR = 1, gridC = 1;
    private List<float[]> boxes;
    private boolean redactMode;
    private float[] drawing;
    private RedactListener redactListener;
    private PageSwiper.Gesture swipeListener;
    private VelocityTracker vt;
    private float swX, swY;
    private boolean swAllowed, swActive;
    private final RectF r = new RectF();

    public ZoomImageView(Context c) {
        super(c);
        float dp = c.getResources().getDisplayMetrics().density;
        gridP.setColor(Ui.ACCENT); gridP.setStrokeWidth(1.5f * dp); gridP.setStyle(Paint.Style.STROKE);
        gridP.setPathEffect(new DashPathEffect(new float[]{8 * dp, 5 * dp}, 0));
        boxP.setColor(0xFF000000);
        boxLine.setColor(Ui.ACCENT); boxLine.setStyle(Paint.Style.STROKE); boxLine.setStrokeWidth(1.5f * dp);
        drawP.setColor(0x99000000);
        sgd = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float cur = currentScale();
                float f = d.getScaleFactor();
                float target = Math.max(fitScale, Math.min(fitScale * 10, cur * f));
                f = target / cur;
                m.postScale(f, f, d.getFocusX(), d.getFocusY());
                fix();
                invalidate();
                return true;
            }
        });
        gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) { return true; }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (redactMode) return false;
                m.postTranslate(-dx, -dy);
                fix();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (redactMode) return false;
                float cur = currentScale();
                float target = cur > fitScale * 1.2f ? fitScale : fitScale * 2.5f;
                m.postScale(target / cur, target / cur, e.getX(), e.getY());
                fix();
                invalidate();
                return true;
            }
        });
    }

    public void setBitmap(Bitmap b, boolean keepView) {
        boolean same = bmp != null && b != null && bmp.getWidth() == b.getWidth() && bmp.getHeight() == b.getHeight();
        bmp = b;
        if (!(keepView && same)) fit();
        invalidate();
    }

    public Bitmap getBitmap() { return bmp; }

    public void setSwipeListener(PageSwiper.Gesture l) { swipeListener = l; }

    /** Horizontal drag while not zoomed moves between pages; returns true while it owns the gesture. */
    private boolean handleSwipe(MotionEvent e) {
        if (swipeListener == null || redactMode) return false;
        int a = e.getActionMasked();
        if (vt != null) {
            MotionEvent raw = MotionEvent.obtain(e);
            raw.setLocation(e.getRawX(), e.getRawY());
            vt.addMovement(raw);
            raw.recycle();
        }
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                swX = e.getRawX(); swY = e.getRawY();
                swActive = false;
                swAllowed = currentScale() <= fitScale * 1.05f;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                return false;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (swActive) swipeListener.onSwipeRelease(0, 0);
                swActive = false;
                swAllowed = false;
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (!swAllowed || e.getPointerCount() > 1) return false;
                float dx = e.getRawX() - swX, dy = e.getRawY() - swY;
                int slop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (!swActive && Math.abs(dx) > slop && Math.abs(dx) > 1.3f * Math.abs(dy)) {
                    swActive = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (swActive) { swipeListener.onSwipeDrag(dx); return true; }
                return false;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean was = swActive;
                if (was) {
                    float vx = 0;
                    if (vt != null) { vt.computeCurrentVelocity(1000); vx = vt.getXVelocity(); }
                    if (a == MotionEvent.ACTION_UP) swipeListener.onSwipeRelease(e.getRawX() - swX, vx);
                    else swipeListener.onSwipeRelease(0, 0);
                }
                swActive = false;
                if (vt != null) { vt.recycle(); vt = null; }
                return was;
            }
            default:
                return swActive;
        }
    }

    public void setGrid(int rows, int cols) { gridR = rows; gridC = cols; invalidate(); }

    public void setBoxes(List<float[]> b) { boxes = b; invalidate(); }

    public void setRedactMode(boolean on, RedactListener l) {
        redactMode = on; redactListener = l; drawing = null;
        if (on) fit();
        invalidate();
    }

    private float currentScale() {
        float[] v = new float[9];
        m.getValues(v);
        return v[Matrix.MSCALE_X];
    }

    private void fit() {
        if (bmp == null || getWidth() == 0) return;
        float pad = getResources().getDisplayMetrics().density * 8;
        fitScale = Math.min((getWidth() - 2 * pad) / bmp.getWidth(), (getHeight() - 2 * pad) / bmp.getHeight());
        m.setScale(fitScale, fitScale);
        m.postTranslate((getWidth() - bmp.getWidth() * fitScale) / 2f, (getHeight() - bmp.getHeight() * fitScale) / 2f);
    }

    /** Keeps the image centred when smaller than the view, and edge-to-edge when larger. */
    private void fix() {
        if (bmp == null) return;
        r.set(0, 0, bmp.getWidth(), bmp.getHeight());
        m.mapRect(r);
        float dx = 0, dy = 0, w = getWidth(), h = getHeight();
        if (r.width() <= w) dx = (w - r.width()) / 2f - r.left;
        else if (r.left > 0) dx = -r.left;
        else if (r.right < w) dx = w - r.right;
        if (r.height() <= h) dy = (h - r.height()) / 2f - r.top;
        else if (r.top > 0) dy = -r.top;
        else if (r.bottom < h) dy = h - r.bottom;
        m.postTranslate(dx, dy);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) { fit(); }

    @Override
    protected void onDraw(Canvas c) {
        if (bmp == null) return;
        c.drawBitmap(bmp, m, paint);
        float bw = bmp.getWidth(), bh = bmp.getHeight();
        if (boxes != null) {
            for (float[] b : boxes) {
                r.set(b[0] * bw, b[1] * bh, b[2] * bw, b[3] * bh);
                m.mapRect(r);
                c.drawRect(r, boxP);
                if (redactMode) c.drawRect(r, boxLine);
            }
        }
        if (drawing != null) {
            r.set(Math.min(drawing[0], drawing[2]) * bw, Math.min(drawing[1], drawing[3]) * bh,
                    Math.max(drawing[0], drawing[2]) * bw, Math.max(drawing[1], drawing[3]) * bh);
            m.mapRect(r);
            c.drawRect(r, drawP);
            c.drawRect(r, boxLine);
        }
        if (gridR > 1 || gridC > 1) {
            r.set(0, 0, bw, bh);
            m.mapRect(r);
            for (int i = 1; i < gridC; i++) {
                float x = r.left + r.width() * i / gridC;
                c.drawLine(x, r.top, x, r.bottom, gridP);
            }
            for (int i = 1; i < gridR; i++) {
                float y = r.top + r.height() * i / gridR;
                c.drawLine(r.left, y, r.right, y, gridP);
            }
        }
    }

    private float[] toImage(float x, float y) {
        m.invert(inv);
        float[] p = {x, y};
        inv.mapPoints(p);
        return new float[]{Math.max(0, Math.min(1, p[0] / bmp.getWidth())), Math.max(0, Math.min(1, p[1] / bmp.getHeight()))};
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bmp == null) return false;
        sgd.onTouchEvent(e);
        if (redactMode) {
            if (e.getPointerCount() > 1 || sgd.isInProgress()) {
                drawing = null;
                invalidate();
                return true;
            }
            float[] p = toImage(e.getX(), e.getY());
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    drawing = new float[]{p[0], p[1], p[0], p[1]};
                    getParent().requestDisallowInterceptTouchEvent(true);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (drawing != null) { drawing[2] = p[0]; drawing[3] = p[1]; }
                    break;
                case MotionEvent.ACTION_UP:
                    if (drawing != null) {
                        float[] b = {Math.min(drawing[0], drawing[2]), Math.min(drawing[1], drawing[3]),
                                Math.max(drawing[0], drawing[2]), Math.max(drawing[1], drawing[3])};
                        drawing = null;
                        if (b[2] - b[0] > 0.01f && b[3] - b[1] > 0.005f && redactListener != null) redactListener.onBoxDrawn(b);
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    drawing = null;
                    break;
                default:
                    break;
            }
            invalidate();
            return true;
        }
        if (handleSwipe(e)) return true;
        if (!sgd.isInProgress()) gd.onTouchEvent(e);
        return true;
    }
}
