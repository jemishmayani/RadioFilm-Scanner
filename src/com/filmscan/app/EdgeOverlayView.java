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
    private final Paint pillBg = new Paint(Paint.ANTI_ALIAS_FLAG), pillText = new Paint(Paint.ANTI_ALIAS_FLAG),
            ringBg = new Paint(Paint.ANTI_ALIAS_FLAG), ringFg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF arc = new android.graphics.RectF();
    private float shownSteady;

    public EdgeOverlayView(Context c, View target) {
        super(c);
        this.target = target;
        fill.setColor(Ui.ACCENT); fill.setAlpha(46);
        line.setColor(Ui.ACCENT); line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(Ui.dp(c, 2f));
        mark.setColor(Ui.ACCENT); mark.setStyle(Paint.Style.STROKE); mark.setStrokeWidth(Ui.dp(c, 4.5f));
        mark.setStrokeCap(Paint.Cap.ROUND); mark.setStrokeJoin(Paint.Join.ROUND);
        gridP.setColor(0x55FFFFFF); gridP.setStrokeWidth(Ui.dp(c, 1));
        ring.setColor(Ui.LIGHT); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(Ui.dp(c, 1.5f));
        pillBg.setColor(0xCC081420);
        pillText.setColor(Ui.LIGHT);
        pillText.setTextSize(Ui.dp(c, 16));
        pillText.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        pillText.setLetterSpacing(0.12f);
        ringBg.setStyle(Paint.Style.STROKE); ringBg.setStrokeWidth(Ui.dp(c, 3)); ringBg.setColor(0x44E6F0F7);
        ringFg.setStyle(Paint.Style.STROKE); ringFg.setStrokeWidth(Ui.dp(c, 3)); ringFg.setColor(Ui.ACCENT);
        ringFg.setStrokeCap(Paint.Cap.ROUND);
    }

    /**
     * New detection result. The outline glides towards it frame by frame (and fades in or out),
     * instead of jumping each time the detector reports.
     */
    public void setQuad(float[] q) {
        goal = q == null ? null : q.clone();
        if (goal != null && quad == null) quad = goal.clone();   // first sighting: appear in place, fading in
        lastFrame = 0;
        postInvalidateOnAnimation();
    }

    public float[] getQuad() { return goal; }

    private float[] goal;            // latest detection
    private float shownAlpha;        // 0..1 fade of the outline
    private long lastFrame;

    /** Moves the drawn outline towards the latest detection; returns true while still moving. */
    private boolean animateQuad() {
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 16f : Math.min(64f, now - lastFrame);
        lastFrame = now;
        float kPos = 1f - (float) Math.exp(-dt / 85f);    // ~85 ms time constant: smooth but responsive
        float kFade = 1f - (float) Math.exp(-dt / 120f);
        boolean moving = false;
        if (goal != null && quad != null) {
            for (int i = 0; i < 8; i++) {
                float d = goal[i] - quad[i];
                quad[i] += d * kPos;
                if (Math.abs(d) > 0.0006f) moving = true;
            }
            shownAlpha += (1f - shownAlpha) * kFade;
            if (shownAlpha < 0.995f) moving = true;
        } else if (quad != null) {
            shownAlpha += (0f - shownAlpha) * kFade;       // lost the page: fade out where it was
            if (shownAlpha < 0.02f) { quad = null; shownAlpha = 0f; } else moving = true;
        }
        return moving;
    }

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
        boolean moving = animateQuad();
        if (quad != null) {
            path.reset();
            for (int i = 0; i < 4; i++) {
                float x = l + quad[2 * i] * w, y = t + quad[2 * i + 1] * h;
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            fill.setAlpha((int) (46 * shownAlpha));
            line.setAlpha((int) (255 * shownAlpha));
            mark.setAlpha((int) (255 * shownAlpha));
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
        if (moving) postInvalidateOnAnimation();
        else lastFrame = 0;
        drawHoldSteady(c, l, t, w, h);
        long dt = SystemClock.uptimeMillis() - focusAt;
        if (focusAt > 0 && dt < 900) {
            float k = dt / 900f;
            float r = Ui.dp(getContext(), 38 - 10 * Math.min(1f, k * 3));
            ring.setAlpha((int) (255 * (1 - k * k)));
            c.drawCircle(focusX, focusY, r, ring);
            postInvalidateOnAnimation();
        }
    }

    /** "HOLD STEADY" pill with a ring that fills as the shot steadies, just before auto-capture. */
    private void drawHoldSteady(Canvas c, float l, float t, float w, float h) {
        // ease towards the target so the ring grows smoothly between detection updates
        shownSteady += (steady - shownSteady) * 0.35f;
        if (Math.abs(steady - shownSteady) > 0.005f) postInvalidateOnAnimation();
        if (shownSteady < 0.03f || quad == null) return;
        float dp = getResources().getDisplayMetrics().density;
        String label = "HOLD STEADY";
        float tw = pillText.measureText(label);
        float ringR = 10 * dp, padH = 16 * dp, gap = 10 * dp;
        float pw = padH + ringR * 2 + gap + tw + padH, ph = 44 * dp;
        float cx = l + w / 2f, cy = t + h / 2f;
        int alpha = (int) (255 * Math.min(1f, shownSteady * 4f));
        pillBg.setAlpha((int) (0xCC * alpha / 255f));
        pillText.setAlpha(alpha);
        ringBg.setAlpha((int) (0x44 * alpha / 255f));
        ringFg.setAlpha(alpha);
        c.drawRoundRect(cx - pw / 2, cy - ph / 2, cx + pw / 2, cy + ph / 2, ph / 2, ph / 2, pillBg);
        float rx = cx - pw / 2 + padH + ringR;
        arc.set(rx - ringR, cy - ringR, rx + ringR, cy + ringR);
        c.drawOval(arc, ringBg);
        c.drawArc(arc, -90, 360 * Math.min(1f, shownSteady), false, ringFg);
        Paint.FontMetrics fm = pillText.getFontMetrics();
        c.drawText(label, rx + ringR + gap, cy - (fm.ascent + fm.descent) / 2f, pillText);
    }
}
