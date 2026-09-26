package com.filmscan.app;

import android.content.Context;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ScrollView;
import java.util.ArrayList;
import java.util.List;

/**
 * A grid of tiles that can be reordered: long-press a tile to lift it, drag it to a new place and
 * the others glide out of the way. Scrolls its parent ScrollView when dragging near an edge.
 */
public final class ReorderGrid extends ViewGroup {
    public interface Listener {
        void onOrderChanged(List<View> order);
    }

    private final List<View> order = new ArrayList<View>();
    private int cols = 2, gap, pad, tileW, tileH;
    private Listener listener;

    private View drag, downChild;
    private float downX, downY, touchX, touchY, grabX, grabY;
    private boolean dragging;
    private final int slop;
    private int scrollSpeed;

    public ReorderGrid(Context c) {
        super(c);
        gap = Ui.dp(c, 12);
        pad = Ui.dp(c, 14);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        setClipChildren(false);
    }

    public void setListener(Listener l) { listener = l; }

    public void setTiles(List<View> tiles) {
        removeAllViews();
        order.clear();
        for (View v : tiles) { addView(v); order.add(v); }
        requestLayout();
    }

    public List<View> getOrder() { return new ArrayList<View>(order); }

    @Override
    protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws);
        cols = w > Ui.dp(getContext(), 600) ? 3 : 2;
        tileW = Math.max(1, (w - 2 * pad - gap * (cols - 1)) / cols);
        tileH = (int) (tileW * 1.3f);
        int n = order.size();
        int rows = (n + cols - 1) / cols;
        int h = 2 * pad + rows * tileH + Math.max(0, rows - 1) * gap;
        int cw = MeasureSpec.makeMeasureSpec(tileW, MeasureSpec.EXACTLY), ch = MeasureSpec.makeMeasureSpec(tileH, MeasureSpec.EXACTLY);
        for (View v : order) v.measure(cw, ch);
        setMeasuredDimension(w, h);
    }

    private int slotX(int i) { return pad + (i % cols) * (tileW + gap); }

    private int slotY(int i) { return pad + (i / cols) * (tileH + gap); }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) { layoutTiles(); }

    private void layoutTiles() {
        for (int i = 0; i < order.size(); i++) {
            View v = order.get(i);
            v.layout(slotX(i), slotY(i), slotX(i) + tileW, slotY(i) + tileH);
        }
    }

    private View childAt(float x, float y) {
        for (View v : order) {
            if (x >= v.getLeft() && x < v.getRight() && y >= v.getTop() && y < v.getBottom()) return v;
        }
        return null;
    }

    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            if (downChild == null) return;
            dragging = true;
            drag = downChild;
            grabX = downX - drag.getLeft();
            grabY = downY - drag.getTop();
            touchX = downX;
            touchY = downY;
            drag.setPressed(false);
            drag.animate().scaleX(1.07f).scaleY(1.07f).translationZ(Ui.dp(getContext(), 14)).alpha(0.96f)
                    .setStartDelay(0).setDuration(180).setInterpolator(new DecelerateInterpolator()).start();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            ViewParent p = getParent();
            if (p != null) p.requestDisallowInterceptTouchEvent(true);
        }
    };

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX(); downY = e.getY();
                downChild = childAt(downX, downY);
                if (downChild != null) postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return false;
            case MotionEvent.ACTION_MOVE:
                if (dragging) { move(e.getX(), e.getY()); return true; }
                if (Math.hypot(e.getX() - downX, e.getY() - downY) > slop) removeCallbacks(longPress);
                return false;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (dragging) { drop(); return true; }
                return false;
            default:
                return dragging;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!dragging) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                move(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drop();
                return true;
            default:
                return true;
        }
    }

    private void positionDrag() {
        drag.setTranslationX(touchX - grabX - drag.getLeft());
        drag.setTranslationY(touchY - grabY - drag.getTop());
    }

    private void move(float x, float y) {
        touchX = x;
        touchY = y;
        positionDrag();
        int n = order.size();
        int col = (int) Math.max(0, Math.min(cols - 1, (x - pad) / (tileW + gap)));
        int row = (int) Math.max(0, (y - pad) / (tileH + gap));
        int target = Math.min(n - 1, row * cols + col);
        int cur = order.indexOf(drag);
        if (target != cur && target >= 0) reorder(cur, target);
        updateAutoScroll();
    }

    /** Moves the lifted tile to a new slot; everyone else glides to their new place. */
    private void reorder(int from, int to) {
        int n = order.size();
        int[] oldL = new int[n], oldT = new int[n];
        List<View> before = new ArrayList<View>(order);
        for (int i = 0; i < n; i++) { oldL[i] = before.get(i).getLeft(); oldT[i] = before.get(i).getTop(); }
        order.remove(from);
        order.add(to, drag);
        layoutTiles();
        for (int i = 0; i < n; i++) {
            View v = before.get(i);
            if (v == drag) continue;
            float dx = oldL[i] - v.getLeft() + v.getTranslationX(), dy = oldT[i] - v.getTop() + v.getTranslationY();
            if (dx == 0 && dy == 0) continue;
            v.setTranslationX(dx);
            v.setTranslationY(dy);
            v.animate().translationX(0).translationY(0).setStartDelay(0).setDuration(220).setInterpolator(new DecelerateInterpolator(1.8f)).start();
        }
        positionDrag();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void drop() {
        dragging = false;
        stopAutoScroll();
        final View v = drag;
        drag = null;
        downChild = null;
        if (v == null) return;
        v.animate().translationX(0).translationY(0).scaleX(1f).scaleY(1f).translationZ(0).alpha(1f)
                .setStartDelay(0).setDuration(280).setInterpolator(new OvershootInterpolator(1.1f)).start();
        if (listener != null) listener.onOrderChanged(getOrder());
    }

    // ------------------------------------------------------------------ auto-scroll

    private ScrollView scroller() {
        ViewParent p = getParent();
        return p instanceof ScrollView ? (ScrollView) p : null;
    }

    private void updateAutoScroll() {
        ScrollView sv = scroller();
        if (sv == null) return;
        float top = sv.getScrollY(), bottom = top + sv.getHeight();
        float edge = Ui.dp(getContext(), 72);
        int speed = 0;
        if (touchY < top + edge) speed = -(int) (Ui.dp(getContext(), 14) * (1 - (touchY - top) / edge));
        else if (touchY > bottom - edge) speed = (int) (Ui.dp(getContext(), 14) * (1 - (bottom - touchY) / edge));
        boolean running = scrollSpeed != 0;
        scrollSpeed = speed;
        if (speed != 0 && !running) postOnAnimation(scrollTick);
    }

    private final Runnable scrollTick = new Runnable() {
        @Override
        public void run() {
            ScrollView sv = scroller();
            if (!dragging || sv == null || scrollSpeed == 0) { scrollSpeed = 0; return; }
            int before = sv.getScrollY();
            sv.scrollBy(0, scrollSpeed);
            int moved = sv.getScrollY() - before;
            if (moved == 0) { scrollSpeed = 0; return; }
            move(touchX, touchY + moved);
            postOnAnimation(this);
        }
    };

    private void stopAutoScroll() {
        scrollSpeed = 0;
        removeCallbacks(scrollTick);
    }
}
