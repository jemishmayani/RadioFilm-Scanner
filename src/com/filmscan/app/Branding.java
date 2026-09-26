package com.filmscan.app;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/**
 * The app can present itself as RadioFilm Scanner (films and radiology reports) or DocScanner
 * (everyday documents). Same app and features; the name, launcher icon, save folders and default
 * name suggestions change. The launcher entry is switched with two activity-aliases.
 */
public final class Branding {
    private Branding() {}

    public static final int RADIOFILM = 0, DOCSCANNER = 1;
    private static final String ALIAS_RADIO = "com.filmscan.app.LauncherRadioFilm";
    private static final String ALIAS_DOC = "com.filmscan.app.LauncherDocScanner";

    public static int current() { return App.get().prefs().identity() == DOCSCANNER ? DOCSCANNER : RADIOFILM; }

    public static boolean doc() { return current() == DOCSCANNER; }

    public static String name() { return name(current()); }

    public static String name(int id) { return id == DOCSCANNER ? "DocScanner" : "RadioFilm Scanner"; }

    public static int icon(int id) { return id == DOCSCANNER ? R.mipmap.ic_launcher_doc : R.mipmap.ic_launcher; }

    /** Folder name under Pictures/ and Download/. */
    public static String folder() { return name(); }

    /** Switches identity: launcher entry, then optionally the default suggestion sets. */
    public static void switchTo(Context c, int id, boolean alsoSuggestions) {
        App.get().prefs().identity(id);
        syncLauncher(c);
        if (alsoSuggestions) {
            Prefs p = App.get().prefs();
            for (Suggest.Pack pk : Suggest.PACKS) {
                boolean on = id == DOCSCANNER ? !"radiology".equals(pk.id) : "radiology".equals(pk.id);
                Suggest.setPackOn(p, pk, on);
            }
        }
    }

    /** Makes the enabled launcher entry match the saved identity (after a switch or a settings import). */
    public static void syncLauncher(Context c) {
        PackageManager pm = c.getPackageManager();
        boolean doc = doc();
        ComponentName on = new ComponentName(c.getPackageName(), doc ? ALIAS_DOC : ALIAS_RADIO);
        ComponentName off = new ComponentName(c.getPackageName(), doc ? ALIAS_RADIO : ALIAS_DOC);
        try {
            // enable the new entry first so there is never a moment without one
            if (pm.getComponentEnabledSetting(on) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(on, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            }
            if (pm.getComponentEnabledSetting(off) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(off, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
            }
        } catch (Exception ignored) { }
    }

    /** Name and icon shown in the recent-apps screen. */
    public static void applyTaskDescription(Activity a) {
        try {
            Bitmap icon = null;
            Drawable d = a.getResources().getDrawable(icon(current()), a.getTheme());
            if (d != null) {
                int s = Ui.dp(a, 48);
                icon = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, s, s);
                d.draw(new Canvas(icon));
            }
            a.setTaskDescription(new ActivityManager.TaskDescription(name(), icon, Ui.BG));
        } catch (Throwable ignored) { }
    }
}
