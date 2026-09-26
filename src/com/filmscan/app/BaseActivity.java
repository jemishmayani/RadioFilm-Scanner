package com.filmscan.app;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

/**
 * Applies the chosen accent colour and the screen orientation rule: phones stay in portrait,
 * tablets (smallest width 600dp or more) turn freely, so they are never letterboxed.
 */
public abstract class BaseActivity extends Activity {
    private int accentAtCreate;

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
