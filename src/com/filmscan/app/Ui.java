package com.filmscan.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Design tokens. The palette comes from reading films on a lightbox: blue film base, the cool
 * white of the lightbox, and the amber of a radiologist's grease pencil for everything you touch.
 */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF0D1B2A;        // film base
    public static final int PANEL = 0xFF152A3E;
    public static final int PANEL_HI = 0xFF1F3B55;
    public static final int LIGHT = 0xFFE6F0F7;     // lightbox
    public static final int MUTED = 0xFF8FA7BA;
    /** Accent palette; index 0 (grease-pencil amber) is the default. Chosen in Settings. */
    public static final int[] ACCENTS = {0xFFF5B544, 0xFFFF8A65, 0xFFF48FB1, 0xFFB39DFF, 0xFF64B5F6, 0xFF4DD0C4, 0xFF7BD88F, 0xFFD4E157};
    public static final String[] ACCENT_NAMES = {"Amber", "Coral", "Rose", "Violet", "Sky", "Teal", "Green", "Lime"};
    private static final int[] THEMES = {R.style.AppTheme_A0, R.style.AppTheme_A1, R.style.AppTheme_A2, R.style.AppTheme_A3,
            R.style.AppTheme_A4, R.style.AppTheme_A5, R.style.AppTheme_A6, R.style.AppTheme_A7};
    private static final int[] SHEETS = {R.style.SheetTheme_A0, R.style.SheetTheme_A1, R.style.SheetTheme_A2, R.style.SheetTheme_A3,
            R.style.SheetTheme_A4, R.style.SheetTheme_A5, R.style.SheetTheme_A6, R.style.SheetTheme_A7};
    private static int accentIndex;

    public static int ACCENT = ACCENTS[0];
    public static int ON_ACCENT = 0xFF1C1405;

    public static void applyAccent(int index) {
        accentIndex = index < 0 || index >= ACCENTS.length ? 0 : index;
        ACCENT = ACCENTS[accentIndex];
        ON_ACCENT = onColor(ACCENT);
    }

    /** Dark text on light accents, white on dark ones. */
    public static int onColor(int c) {
        double r = ((c >> 16) & 255) / 255.0, g = ((c >> 8) & 255) / 255.0, b = (c & 255) / 255.0;
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return lum > 0.45 ? 0xFF14120C : 0xFFFFFFFF;
    }

    /** The accent with a given alpha (0..255). */
    public static int accentA(int alpha) { return (alpha << 24) | (ACCENT & 0xFFFFFF); }

    public static int themeRes() { return THEMES[accentIndex]; }

    public static int sheetTheme() { return SHEETS[accentIndex]; }
    public static final int DANGER = 0xFFFF6B6B;
    public static final int SCRIM = 0xB3081420;

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable round(int color, float radiusPx, int stroke, int strokeW) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(strokeW, stroke);
        return d;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    public static Drawable ripple(Drawable content, boolean bounded) {
        return new RippleDrawable(ColorStateList.valueOf(0x33E6F0F7), content, bounded ? round(0xFFFFFFFF, 0) : null);
    }

    public static Drawable icon(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res, c.getTheme()).mutate();
        d.setTint(color);
        return d;
    }

    /** 48dp round icon button. */
    public static ImageView iconButton(Context c, int res, String desc) {
        ImageView b = new ImageView(c);
        b.setImageDrawable(icon(c, res, LIGHT));
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(ripple(null, false));
        b.setContentDescription(desc);
        b.setClickable(true);
        b.setFocusable(true);
        int s = dp(c, 48);
        b.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return b;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean medium) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (medium) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Pill button. Primary = amber, otherwise a quiet panel button. */
    public static TextView button(Context c, String label, boolean primary) {
        TextView t = text(c, label, 15, primary ? ON_ACCENT : LIGHT, true);
        t.setGravity(Gravity.CENTER);
        t.setAllCaps(false);
        int h = dp(c, 48);
        t.setMinHeight(h);
        t.setPadding(dp(c, 22), 0, dp(c, 22), 0);
        t.setBackground(ripple(round(primary ? ACCENT : PANEL_HI, h / 2f), true));
        t.setClickable(true);
        t.setFocusable(true);
        pressable(t);
        return t;
    }

    /** Toolbar action: icon above a short label. */
    public static LinearLayout tool(Context c, int res, String label) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(c, 4), dp(c, 8), dp(c, 4), dp(c, 6));
        l.setBackground(ripple(null, false));
        l.setClickable(true);
        l.setFocusable(true);
        l.setContentDescription(label);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon(c, res, LIGHT));
        l.addView(iv, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
        TextView t = text(c, label, 11.5f, MUTED, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(c, 4);
        l.addView(t, lp);
        pressable(l);
        return l;
    }

    public static void setToolActive(LinearLayout tool, boolean active) {
        tool.setBackground(active ? ripple(round(accentA(0x1F), dp(tool.getContext(), 12)), true) : ripple(null, false));
        ImageView iv = (ImageView) tool.getChildAt(0);
        TextView t = (TextView) tool.getChildAt(1);
        iv.getDrawable().setTint(active ? ACCENT : LIGHT);
        t.setTextColor(active ? ACCENT : MUTED);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
    }

    public static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    /** Standard top bar: [left icon] title [actions...] */
    public static LinearLayout topBar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        bar.setMinimumHeight(dp(c, 56));
        bar.setBackgroundColor(BG);
        return bar;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 18, LIGHT, true);
        t.setSingleLine(true);
        t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        return t;
    }

    public static void setVisible(View v, boolean vis) { v.setVisibility(vis ? View.VISIBLE : View.GONE); }

    /** Gentle press-down scale with a springy release. Does not consume the touch. */
    public static void pressable(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.94f).scaleY(0.94f).setStartDelay(0).setDuration(90)
                                .setInterpolator(new DecelerateInterpolator()).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(260)
                                .setInterpolator(new OvershootInterpolator(2.2f)).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** Rounded suggestion chip. */
    public static TextView chip(Context c, String label, int iconRes) {
        TextView t = text(c, label, 14, LIGHT, false);
        t.setSingleLine(true);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 14), dp(c, 7), dp(c, 14), dp(c, 7));
        t.setBackground(ripple(round(PANEL_HI, dp(c, 18), 0x2EE6F0F7, dp(c, 1)), true));
        if (iconRes != 0) {
            android.graphics.drawable.Drawable d = icon(c, iconRes, MUTED);
            int s = dp(c, 18);
            d.setBounds(0, 0, s, s);
            t.setCompoundDrawablesRelative(null, null, d, null);
            t.setCompoundDrawablePadding(dp(c, 4));
        }
        t.setClickable(true);
        pressable(t);
        return t;
    }

    /** Fades and lifts a view in; used for panels and rows appearing. */
    public static void reveal(final View v, long delay) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 12));
        v.animate().alpha(1f).translationY(0).setStartDelay(delay).setDuration(240)
                .setInterpolator(new DecelerateInterpolator(2f)).withEndAction(resetDelay(v)).start();
    }

    /** ViewPropertyAnimator keeps its start delay; clear it so later touches animate instantly. */
    public static Runnable resetDelay(final View v) {
        return new Runnable() {
            @Override public void run() { v.animate().setStartDelay(0); }
        };
    }

    /** Bottom sheet with rounded top corners, a grab handle and a slide-up animation. */
    public static Dialog sheet(Activity a, View content) {
        Dialog d = new Dialog(a, sheetTheme());
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout wrap = new LinearLayout(a);
        wrap.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(PANEL);
        float r = dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        wrap.setBackground(bg);
        wrap.setPadding(0, dp(a, 10), 0, dp(a, 18));
        View handle = new View(a);
        handle.setBackground(round(0x55E6F0F7, dp(a, 2)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(dp(a, 40), dp(a, 4));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.bottomMargin = dp(a, 8);
        wrap.addView(handle, hl);
        wrap.addView(content, new LinearLayout.LayoutParams(-1, -2));
        d.setContentView(wrap);
        d.setCanceledOnTouchOutside(true);
        Window w = d.getWindow();
        w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        d.show();
        // full width on phones; a comfortable centred card on tablets and in landscape
        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        w.setLayout(Math.min(screenW, dp(a, 640)), ViewGroup.LayoutParams.WRAP_CONTENT);
        return d;
    }
}
