package com.filmscan.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

/** Draws the live film outline, framing grid and tap-to-focus ring over the camera preview. */
public final class EdgeOverlayView extends View {
    private final View target;
    private float[] quad;           // normalized, smoothed
    private boolean grid;
    private float steady;           // 0..1 auto-capture progress
    private float focusX, focusY;
    private long focusAt;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG),
            mark = new Paint(Paint.ANTI_ALIAS_FLAG), gridP = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public EdgeOverlayView(Context c, View target) {
        super(c);
        this.target = target;
        fill.setColor(Ui.ACCENT); fill.setAlpha(46);
        line.setColor(Ui.ACCENT); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(Ui.dp(c, 2f));
        mark.setColor(Ui.ACCENT); mark.setStyle(Paint.Style.STROKE); mark.setStrokeWidth(Ui.dp(c, 4.5f));
        mark.setStrokeCap(Paint.Cap.ROUND); mark.setStrokeJoin(Paint.Join.ROUND);
        gridP.setColor(0x55FFFFFF); gridP.setStrokeWidth(Ui.dp(c, 1));
        ring.setColor(Ui.LIGHT); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(Ui.dp(c, 1.5f));
    }

    public void setQuad(float[] q) { quad = q; invalidate(); }

    public float[] getQuad() { return quad; }

    public void setGrid(boolean g) { grid = g; invalidate(); }

    public void setSteady(float s) { steady = s; invalidate(); }

    public void showFocus(float x, float y) {
        focusX = x; focusY = y; focusAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        float l = target.getLeft(), t = target.getTop(), w = target.getWidth(), h = target.getHeight();
        if (w <= 0) return;
        if (grid) {
            for (int i = 1; i < 3; i++) {
                c.drawLine(l + w * i / 3f, t, l + w * i / 3f, t + h, gridP);
                c.drawLine(l, t + h * i / 3f, l + w, t + h * i / 3f, gridP);
            }
        }
        if (quad != null) {
            path.reset();
            for (int i = 0; i < 4; i++) {
                float x = l + quad[2 * i] * w, y = t + quad[2 * i + 1] * h;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            c.drawPath(path, fill);
            c.drawPath(path, line);
            // grease-pencil corner marks; they close in as the shot steadies for auto-capture
            float frac = 0.16f + 0.34f * steady;
            for (int i = 0; i < 4; i++) {
                float x = l + quad[2 * i] * w, y = t + quad[2 * i + 1] * h;
                int p = (i + 3) & 3, n = (i + 1) & 3;
                float px = l + quad[2 * p] * w, py = t + quad[2 * p + 1] * h;
                float nx = l + quad[2 * n] * w, ny = t + quad[2 * n + 1] * h;
                path.reset();
                path.moveTo(x + (px - x) * frac, y + (py - y) * frac);
                path.lineTo(x, y);
                path.lineTo(x + (nx - x) * frac, y + (ny - y) * frac);
                c.drawPath(path, mark);
            }
        }
        long dt = SystemClock.uptimeMillis() - focusAt;
        if (focusAt > 0 && dt < 900) {
            float k = dt / 900f;
            float r = Ui.dp(getContext(), 38 - 10 * Math.min(1f, k * 3));
            ring.setAlpha((int) (255 * (1 - k * k)));
            c.drawCircle(focusX, focusY, r, ring);
            postInvalidateOnAnimation();
        }
    }
}
