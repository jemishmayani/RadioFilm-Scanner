package com.filmscan.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Shared behaviour for every screen:
 * - accent colour, and rebuilding the screen if it changed in the background;
 * - orientation: phones portrait, tablets (smallest width 600dp+) free;
 * - edge-to-edge drawing (required from Android 15/16): the app draws behind the
 *   system bars and pads its content so nothing sits under the clock or gesture bar;
 * - back handling that works with Android 16's predictive back as well as older versions.
 */
public abstract class BaseActivity extends Activity {
    private int accentAtCreate;
    private Runnable backHandler;
    private Object backCallback;
    private long lastBack;

    public static boolean isTablet(Activity a) {
        return a.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    /** The orientation this screen normally asks for (used again after a temporary lock). */
    public static int normalOrientation(Activity a) {
        return isTablet(a) ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    @Override
    protected void onCreate(Bundle b) {
        accentAtCreate = App.get().prefs().accent();
        Ui.applyAccent(accentAtCreate);
        setTheme(Ui.themeRes());
        super.onCreate(b);
        setRequestedOrientation(normalOrientation(this));
        edgeToEdge(getWindow());
        Branding.applyTaskDescription(this);
    }

    /** Same look on every Android version: transparent bars over the app's own background. */
    static void edgeToEdge(Window w) {
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Build.VERSION.SDK_INT >= 26 ? Color.TRANSPARENT : 0x99000000);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void setContentView(View v) {
        super.setContentView(v);
        padForSystemBars(v);
    }

    /** Pads a screen's root by the status bar, navigation bar and camera cut-out. */
    static void padForSystemBars(View v) {
        final int l = v.getPaddingLeft(), t = v.getPaddingTop(), r = v.getPaddingRight(), bt = v.getPaddingBottom();
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets in) {
                int[] s = systemInsets(in);
                view.setPadding(l + s[0], t + s[1], r + s[2], bt + s[3]);
                return in.consumeSystemWindowInsets();
            }
        });
        v.requestApplyInsets();
    }

    /** left, top, right, bottom of system bars plus display cut-out. */
    static int[] systemInsets(WindowInsets in) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                // WindowInsets.getInsets(Type.systemBars() | Type.displayCutout()) — Android 11+
                Class<?> type = Class.forName("android.view.WindowInsets$Type");
                int mask = (Integer) type.getMethod("systemBars").invoke(null) | (Integer) type.getMethod("displayCutout").invoke(null)
                        | (Integer) type.getMethod("ime").invoke(null);   // keyboard, so text fields stay visible
                Object ins = WindowInsets.class.getMethod("getInsets", int.class).invoke(in, mask);
                Class<?> ic = ins.getClass();
                return new int[]{ic.getField("left").getInt(ins), ic.getField("top").getInt(ins),
                        ic.getField("right").getInt(ins), ic.getField("bottom").getInt(ins)};
            } catch (Throwable ignored) {
                // fall through to the classic values
            }
        }
        return new int[]{in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom()};
    }

    // ------------------------------------------------------------------ back

    /**
     * Runs {@code r} instead of simply closing the screen when the user goes back. Uses the
     * predictive-back callback on Android 13+ (the only route on Android 16 for apps targeting
     * API 36) and onBackPressed on older versions. Pass null to restore default behaviour.
     */
    protected void setBackHandler(Runnable r) {
        backHandler = r;
        if (Build.VERSION.SDK_INT >= 33) registerBackCallback(r != null);
    }

    @Override
    public void onBackPressed() {
        if (backHandler != null) runBack();
        else super.onBackPressed();
    }

    private void runBack() {
        long now = SystemClock.uptimeMillis();
        if (now - lastBack < 250) return; // never handle one gesture twice
        lastBack = now;
        if (backHandler != null) backHandler.run();
    }

    private void registerBackCallback(boolean on) {
        try {
            Object dispatcher = Activity.class.getMethod("getOnBackInvokedDispatcher").invoke(this);
            Class<?> cb = Class.forName("android.window.OnBackInvokedCallback");
            Class<?> dc = Class.forName("android.window.OnBackInvokedDispatcher");
            if (backCallback != null) {
                dc.getMethod("unregisterOnBackInvokedCallback", cb).invoke(dispatcher, backCallback);
                backCallback = null;
            }
            if (!on) return;
            backCallback = Proxy.newProxyInstance(cb.getClassLoader(), new Class<?>[]{cb}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method m, Object[] args) {
                    String n = m.getName();
                    if ("onBackInvoked".equals(n)) { runBack(); return null; }
                    if ("hashCode".equals(n)) return System.identityHashCode(proxy);
                    if ("equals".equals(n)) return args != null && args.length == 1 && proxy == args[0];
                    if ("toString".equals(n)) return "RadioFilmBackCallback";
                    return null;
                }
            });
            dc.getMethod("registerOnBackInvokedCallback", int.class, cb).invoke(dispatcher, 0, backCallback);
        } catch (Throwable ignored) {
            // older Android: onBackPressed() above does the job
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accentAtCreate != App.get().prefs().accent()) {
            Ui.applyAccent(App.get().prefs().accent());
            recreate();
        }
    }
}
