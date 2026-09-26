package com.filmscan.app;

import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Page changes that follow the finger: the page slides with the drag, springs back if released
 * early, or flies off and the next page glides in. Edges resist so you feel the first/last page.
 */
public final class PageSwiper {
    /** Reported by views while a horizontal swipe is in progress (raw screen pixels). */
    public interface Gesture {
        void onSwipeDrag(float dx);

        void onSwipeRelease(float dx, float vx);
    }

    public interface Host {
        boolean canGo(int dir);

        /** Last chance to veto (for example an invalid crop). */
        boolean beforeGo(int dir);

        void go(int dir);
    }

    private final View stage;
    private final Host host;
    private boolean busy;

    public PageSwiper(View stage, Host host) {
        this.stage = stage;
        this.host = host;
    }

    public final Gesture gesture = new Gesture() {
        @Override public void onSwipeDrag(float dx) { drag(dx); }

        @Override public void onSwipeRelease(float dx, float vx) { release(dx, vx); }
    };

    private float width() { return Math.max(1, stage.getWidth()); }

    public void drag(float dx) {
        if (busy) return;
        boolean ok = host.canGo(dx < 0 ? 1 : -1);
        float t = ok ? dx : dx * 0.25f;
        stage.animate().cancel();
        stage.setTranslationX(t);
        stage.setAlpha(1f - Math.min(0.45f, Math.abs(t) / width() * 0.7f));
    }

    public void release(float dx, float vx) {
        if (busy) return;
        float w = width(), min = Ui.dp(stage.getContext(), 24);
        int dir = 0;
        if (dx < -w * 0.2f || (vx < -900 && dx < -min)) dir = 1;
        else if (dx > w * 0.2f || (vx > 900 && dx > min)) dir = -1;
        if (dir != 0 && host.canGo(dir) && host.beforeGo(dir)) flip(dir, vx);
        else settle();
    }

    /** For the arrow buttons. */
    public void step(int dir) {
        if (busy) return;
        if (!host.canGo(dir)) { nudge(dir); return; }
        if (!host.beforeGo(dir)) return;
        flip(dir, 0);
    }

    private void settle() {
        stage.animate().translationX(0).alpha(1f).setStartDelay(0).setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.2f)).start();
    }

    private void nudge(int dir) {
        stage.animate().translationX(-dir * Ui.dp(stage.getContext(), 22)).setStartDelay(0).setDuration(110)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(new Runnable() {
                    @Override public void run() { settle(); }
                }).start();
    }

    private void flip(final int dir, float vx) {
        busy = true;
        final float w = width();
        float remaining = w - Math.abs(stage.getTranslationX());
        long out = (long) Math.max(90, Math.min(200, remaining / Math.max(1800f, Math.abs(vx)) * 1000));
        stage.animate().translationX(-dir * w).alpha(0f).setStartDelay(0).setDuration(out)
                .setInterpolator(new AccelerateInterpolator(1.3f)).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        host.go(dir);
                        stage.setTranslationX(dir * w * 0.45f);
                        stage.setAlpha(0f);
                        stage.animate().translationX(0).alpha(1f).setStartDelay(0).setDuration(300)
                                .setInterpolator(new DecelerateInterpolator(2.4f)).withEndAction(new Runnable() {
                                    @Override public void run() { busy = false; }
                                }).start();
                    }
                }).start();
    }
}
