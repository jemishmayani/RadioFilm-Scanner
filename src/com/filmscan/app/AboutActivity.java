package com.filmscan.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The project's story, privacy promise, links and ways to support it. Links open in the browser. */
public final class AboutActivity extends BaseActivity {
    static final String REPO = "https://github.com/jemishmayani/RadioFilm-Scanner";
    /** Donation page shown in Support. */
    static final String DONATE_URL = "https://buymeacoffee.com/jemishmayani";

    /**
     * The app has no internet permission (that is how scans stay private), so updates are checked in
     * the browser: the latest GitHub release opens, and the user compares its version with theirs.
     */
    static void checkForUpdates(final android.app.Activity a) {
        String ver = "";
        try { ver = a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        new android.app.AlertDialog.Builder(a)
                .setTitle("Check for updates")
                .setMessage("You have version " + ver + ".\n\n"
                        + "This app has no internet access (that's how your scans stay private), so it checks through your browser: "
                        + "the latest release opens on GitHub.\n\n"
                        + "If its version number is higher than yours, download the APK there and install it over this app. "
                        + "Your saved scans and settings are kept.")
                .setPositiveButton("Open latest release", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPO + "/releases/latest")));
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(a, "No browser found. The address is: " + REPO + "/releases", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private LinearLayout list;
    private int delay;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        top.addView(Ui.title(this, "About"), Ui.weight(1));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        list.setPadding(pad, Ui.dp(this, 4), pad, Ui.dp(this, 32));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });

        // header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(Branding.icon(Branding.current()));
        head.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(Ui.dp(this, 16), 0, 0, 0);
        names.addView(Ui.text(this, Branding.name(), 21, Ui.LIGHT, true));
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        names.addView(Ui.text(this, "Version " + ver + " · open source (MIT)", 13, Ui.MUTED, false));
        head.addView(names, Ui.weight(1));
        add(head, 8);

        section("The story");
        paragraph("Microsoft Lens was the simple scanner many of us relied on. In 2026 Microsoft retired it: "
                + "it left the app stores in February and new scans stopped after March 9, with users pointed to OneDrive instead.");
        paragraph("RadioFilm Scanner (also available as DocScanner) was built by a neurosurgery resident who scans a lot of CT and MRI films and reports, "
                + "with Claude as coding partner. The aim is a fast, private, offline scanner that does one job well: "
                + "films on a lightbox and everyday documents alike.");

        section("Two names, one app");
        paragraph("RadioFilm Scanner: for CT, MRI and X-ray films on a lightbox and radiology reports, with radiology name suggestions.");
        paragraph("DocScanner: for everyday paperwork such as reports, prescriptions, bills, forms and notes, with document name suggestions.");
        paragraph("Both have every feature. Switch any time in Settings > Appearance > App name and icon; it changes the name, "
                + "the home-screen icon and the folders new files are saved in.");

        section("Privacy");
        bullet(R.drawable.ic_shield, "No account, no cloud, no ads, no tracking.");
        bullet(R.drawable.ic_shield, "No internet permission: the app cannot send anything anywhere. Links below open in your browser.");
        bullet(R.drawable.ic_shield, "Your scans stay on this phone until you save or share them.");

        section("Project");
        link(R.drawable.ic_github, "Source code on GitHub", "Read the code, download releases", REPO);
        link(R.drawable.ic_book, "What's new", "Changes in every version", REPO + "/blob/main/CHANGELOG.md");
        link(R.drawable.ic_bug, "Report a problem or suggest a feature", "Opens a new issue on GitHub", REPO + "/issues/new");
        View upd = row(R.drawable.ic_upload, "Check for updates", "Opens the latest release on GitHub");
        upd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { checkForUpdates(AboutActivity.this); }
        });

        section("Support the project");
        link(R.drawable.ic_star_on, "Star it on GitHub", "Helps other people find it", REPO);
        View share = row(R.drawable.ic_share, "Share the app", "Send the download link to a colleague");
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, Branding.name() + ": a private, offline scanner for CT/MRI films and documents. " + REPO);
                try { startActivity(Intent.createChooser(i, "Share the app")); } catch (Exception ignored) { }
            }
        });
        if (DONATE_URL != null) link(R.drawable.ic_heart, "Buy me a coffee", "Support development: buymeacoffee.com/jemishmayani", DONATE_URL);

        section("Good to know");
        paragraph(Branding.name() + " is a scanning and sharing tool, not a medical device. Make diagnostic decisions "
                + "from the original films or images.");
        paragraph("Icons: Material Design Icons by Pictogrammers. Licence: MIT.");
    }

    private void add(View v, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(this, topMarginDp);
        list.addView(v, lp);
        Ui.reveal(v, 30L * Math.min(12, delay++));
    }

    private void section(String title) {
        TextView t = Ui.text(this, title, 13, Ui.ACCENT, true);
        add(t, 24);
    }

    private void paragraph(String text) {
        TextView t = Ui.text(this, text, 14.5f, Ui.LIGHT, false);
        t.setLineSpacing(Ui.dp(this, 3), 1f);
        add(t, 8);
    }

    private void bullet(int icon, String text) {
        LinearLayout r = new LinearLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.icon(this, icon, Ui.ACCENT));
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        TextView t = Ui.text(this, text, 14, Ui.LIGHT, false);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        r.addView(t, Ui.weight(1));
        add(r, 10);
    }

    private View row(int icon, String title, String sub) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 60));
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        r.setBackground(Ui.ripple(Ui.round(Ui.PANEL, Ui.dp(this, 14)), true));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.icon(this, icon, Ui.LIGHT));
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 16), 0, 0, 0);
        texts.addView(Ui.text(this, title, 15, Ui.LIGHT, false));
        texts.addView(Ui.text(this, sub, 12.5f, Ui.MUTED, false));
        r.addView(texts, Ui.weight(1));
        r.setClickable(true);
        Ui.pressable(r);
        add(r, 8);
        return r;
    }

    private void link(int icon, String title, String sub, final String url) {
        row(icon, title, sub).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(AboutActivity.this, "No browser found. The address is: " + url, Toast.LENGTH_LONG).show();
                }
            }
        });
    }
}
