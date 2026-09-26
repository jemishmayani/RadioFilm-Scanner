package com.filmscan.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** App settings: how scans are saved and named, camera behaviour, and device info. */
public final class SettingsActivity extends BaseActivity {
    private static final String EXTRA_CAMERA = "camera";
    private static final int REQ_EXPORT = 21, REQ_IMPORT = 22;
    private TextView suggestValue;

    private Prefs prefs;
    private LinearLayout list;
    private TextView example;
    private int rowIndex;

    public static Intent intent(Context c, String cameraInfo) {
        return new Intent(c, SettingsActivity.class).putExtra(EXTRA_CAMERA, cameraInfo);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = App.get().prefs();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        top.addView(Ui.title(this, "Settings"), Ui.weight(1));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });

        // ---------------------------------------------------------------- saving
        section("Saving");
        info(R.drawable.ic_saved, "Where files go", "Images: Pictures/" + Exporter.folder() + "\nPDFs: Download/" + Exporter.folder()
                + "\nFind them in your Gallery or Files app. Saved scans in this app keep a copy for editing.");
        final int[] saveVals = {-1, Exporter.PDF, Exporter.JPEG, Exporter.PNG};
        final String[] saveLabels = {"Ask every time", "PDF document", "JPEG images", "PNG images (lossless)"};
        choice(R.drawable.ic_save, "Save as", saveLabels, saveVals, new Getter() { public int get() { return prefs.saveAs(); } },
                new Setter() { public void set(int v) { prefs.saveAs(v); } });
        final int[] mpVals = {0, 24, 12};
        choice(R.drawable.ic_full, "Output size", new String[]{"Full resolution", "Up to 24 MP", "Up to 12 MP (smaller files)"}, mpVals,
                new Getter() { public int get() { return prefs.maxMp(); } }, new Setter() { public void set(int v) { prefs.maxMp(v); } });
        final int[] qVals = {100, 95, 85};
        choice(R.drawable.ic_tune, "JPEG quality", new String[]{"Maximum (100)", "High (95)", "Compact (85)"}, qVals,
                new Getter() { public int get() { return prefs.quality(); } }, new Setter() { public void set(int v) { prefs.quality(v); } });

        // ---------------------------------------------------------------- naming
        section("File naming");
        textRow(R.drawable.ic_rename, "Name starts with", "Scan", false, new TextGetter() { public String get() { return prefs.namePrefix(); } },
                new TextSetter() { public void set(String v) { prefs.namePrefix(v); } });
        String[] dateLabels = new String[Naming.DATE_PATTERNS.length];
        int[] dateVals = new int[Naming.DATE_PATTERNS.length];
        for (int i = 0; i < dateLabels.length; i++) { dateLabels[i] = Naming.dateStyleLabel(i); dateVals[i] = i; }
        choice(R.drawable.ic_calendar, "Date in name", dateLabels, dateVals, new Getter() { public int get() { return prefs.nameDate(); } },
                new Setter() { public void set(int v) { prefs.nameDate(v); } });
        toggle(R.drawable.ic_history, "Add time", "e.g. " + Naming.time(new java.util.Date()), prefs.nameTime(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.nameTime(v); updateExample(); }
        });
        LinearLayout sug = row(R.drawable.ic_radiology, "Name suggestions", Suggest.summary(prefs));
        suggestValue = valueOf(sug);
        ImageView chev = new ImageView(this);
        chev.setImageDrawable(Ui.icon(this, R.drawable.ic_next, Ui.MUTED));
        sug.addView(chev, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        sug.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, SuggestionsActivity.class)); }
        });
        example = Ui.text(this, "", 13, Ui.MUTED, false);
        example.setPadding(Ui.dp(this, 56), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 6));
        list.addView(example);
        updateExample();

        // ---------------------------------------------------------------- camera
        section("Camera");
        toggle(R.drawable.ic_auto, "Auto-capture", "Take the photo when the film is held steady", prefs.autoCapture(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.autoCapture(v); }
        });
        toggle(R.drawable.ic_magnet, "Edge snap", "Crop handles jump to the nearest film edge", prefs.snap(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.snap(v); }
        });
        toggle(R.drawable.ic_grid, "Framing grid", null, prefs.grid(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.grid(v); }
        });
        toggle(R.drawable.ic_volume, "Shutter sound", null, prefs.sound(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.sound(v); }
        });
        choice(R.drawable.ic_rotate, "Viewfinder rotation",
                new String[]{"Automatic (recommended)", "Turn 90\u00b0", "Turn 180\u00b0", "Turn 270\u00b0"}, new int[]{0, 1, 2, 3},
                new Getter() { public int get() { return prefs.previewFix(); } },
                new Setter() { public void set(int v) { prefs.previewFix(v); } });

        // ---------------------------------------------------------------- appearance
        section("Appearance");
        LinearLayout idRow = row(Branding.doc() ? R.drawable.ic_doc : R.drawable.ic_radiology, "App name and icon", Branding.name());
        ImageView idIcon = new ImageView(this);
        idIcon.setImageResource(Branding.icon(Branding.current()));
        idRow.addView(idIcon, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        idRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickIdentity(); }
        });
        LinearLayout accentRow = row(R.drawable.ic_palette, "Accent colour", Ui.ACCENT_NAMES[prefs.accent()]);
        View swatch = new View(this);
        swatch.setBackground(Ui.oval(Ui.ACCENT));
        accentRow.addView(swatch, new LinearLayout.LayoutParams(Ui.dp(this, 26), Ui.dp(this, 26)));
        accentRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickAccent(); }
        });

        // ---------------------------------------------------------------- backup
        section("Backup");
        LinearLayout exp = row(R.drawable.ic_upload, "Export settings", "Save all settings, suggestions and colours to a file");
        exp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, SettingsBackup.fileName());
                try { startActivityForResult(i, REQ_EXPORT); } catch (Exception e) { toast("No file app available"); }
            }
        });
        LinearLayout imp = row(R.drawable.ic_backup, "Import settings", "Load settings from a file exported earlier");
        imp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
                try { startActivityForResult(i, REQ_IMPORT); } catch (Exception e) { toast("No file app available"); }
            }
        });

        // ---------------------------------------------------------------- about
        section("About");
        LinearLayout about = row(R.drawable.ic_info, "About " + Branding.name(), "Story, privacy, source code, support");
        ImageView chevA = new ImageView(this);
        chevA.setImageDrawable(Ui.icon(this, R.drawable.ic_next, Ui.MUTED));
        about.addView(chevA, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        about.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(SettingsActivity.this, AboutActivity.class)); }
        });
        String cam = getIntent().getStringExtra(EXTRA_CAMERA);
        info(R.drawable.ic_add_photo, "Capture resolution", (cam == null ? "Camera not started" : cam)
                + ". For 50 MP+ modes, shoot with your camera app and import the photo.");
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        LinearLayout upd = row(R.drawable.ic_upload, "Check for updates", "You have version " + ver);
        upd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AboutActivity.checkForUpdates(SettingsActivity.this); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (suggestValue != null) suggestValue.setText(Suggest.summary(prefs));
    }

    private void toast(String s) { android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show(); }

    /** A row of colour circles; the screen rebuilds in the new colour right away. */
    private void pickAccent() {
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        c.addView(Ui.text(this, "Accent colour", 19, Ui.LIGHT, true));
        ChipFlow flow = new ChipFlow(this);
        flow.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 6));
        for (int i = 0; i < Ui.ACCENTS.length; i++) {
            final int idx = i;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), Ui.dp(this, 8));
            android.widget.FrameLayout dot = new android.widget.FrameLayout(this);
            dot.setBackground(Ui.oval(Ui.ACCENTS[i]));
            if (i == prefs.accent()) {
                ImageView tick = new ImageView(this);
                tick.setImageDrawable(Ui.icon(this, R.drawable.ic_check, Ui.onColor(Ui.ACCENTS[i])));
                tick.setScaleType(ImageView.ScaleType.CENTER);
                dot.addView(tick, new android.widget.FrameLayout.LayoutParams(-1, -1));
            }
            cell.addView(dot, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
            TextView name = Ui.text(this, Ui.ACCENT_NAMES[i], 12, Ui.MUTED, false);
            name.setPadding(0, Ui.dp(this, 6), 0, 0);
            cell.addView(name);
            cell.setClickable(true);
            Ui.pressable(cell);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder[0] != null) holder[0].dismiss();
                    if (idx == prefs.accent()) return;
                    prefs.accent(idx);
                    Ui.applyAccent(idx);
                    recreate();
                }
            });
            flow.addView(cell);
        }
        c.addView(flow, new LinearLayout.LayoutParams(-1, -2));
        holder[0] = Ui.sheet(this, c);
        for (int i = 0; i < flow.getChildCount(); i++) Ui.reveal(flow.getChildAt(i), 30L * i);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        if (req == REQ_EXPORT) {
            try {
                SettingsBackup.write(this, uri);
                toast("Settings exported");
            } catch (Exception e) {
                toast("Export failed: " + e.getMessage());
            }
        } else if (req == REQ_IMPORT) {
            final org.json.JSONObject root;
            try {
                root = SettingsBackup.read(this, uri);
            } catch (Exception e) {
                toast(e.getMessage() != null ? e.getMessage() : "This file could not be read");
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Import settings?")
                    .setMessage("Settings from " + root.optString("exported", "the file") + " replace your current ones, including name suggestions and accent colour.")
                    .setPositiveButton("Import", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            try {
                                SettingsBackup.apply(root);
                                toast("Settings imported");
                                recreate();
                            } catch (Exception e) {
                                toast("Import failed: " + e.getMessage());
                            }
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    /** Choose between RadioFilm Scanner and DocScanner: two cards with icon, name and what each is for. */
    private void pickIdentity() {
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        c.addView(Ui.text(this, "App name and icon", 19, Ui.LIGHT, true));
        TextView sub = Ui.text(this, "Same app and features either way. This changes the name and icon on your home screen, the folders files are saved in, and (optionally) the name suggestions.", 13, Ui.MUTED, false);
        sub.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        c.addView(sub);
        String[] uses = {
                "For CT, MRI and X-ray films on a lightbox, and radiology reports. Radiology name suggestions (modality, body part, technique). Saves to \u201cRadioFilm Scanner\u201d folders.",
                "For everyday paperwork: reports, prescriptions, bills, forms, notes and whiteboards. Document name suggestions. Saves to \u201cDocScanner\u201d folders."};
        for (int id = 0; id < 2; id++) {
            final int which = id;
            boolean current = Branding.current() == id;
            LinearLayout card = new LinearLayout(this);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            card.setBackground(Ui.ripple(current ? Ui.round(Ui.accentA(0x24), Ui.dp(this, 16), Ui.ACCENT, Ui.dp(this, 2))
                    : Ui.round(Ui.PANEL_HI, Ui.dp(this, 16)), true));
            ImageView iv = new ImageView(this);
            iv.setImageResource(Branding.icon(id));
            card.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 52)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(Ui.dp(this, 14), 0, 0, 0);
            texts.addView(Ui.text(this, Branding.name(id) + (current ? "  \u2713" : ""), 16, current ? Ui.ACCENT : Ui.LIGHT, true));
            TextView u = Ui.text(this, uses[id], 12.5f, Ui.MUTED, false);
            u.setPadding(0, Ui.dp(this, 3), 0, 0);
            texts.addView(u);
            card.addView(texts, Ui.weight(1));
            card.setClickable(true);
            Ui.pressable(card);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 10);
            c.addView(card, lp);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (holder[0] != null) holder[0].dismiss();
                    if (which != Branding.current()) confirmIdentity(which);
                }
            });
        }
        holder[0] = Ui.sheet(this, c);
    }

    private void confirmIdentity(final int id) {
        final android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText(id == Branding.DOCSCANNER ? "Also switch name suggestions to Documents and General"
                : "Also switch name suggestions to Radiology");
        cb.setChecked(true);
        cb.setTextColor(Ui.LIGHT);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        l.setPadding(p, Ui.dp(this, 8), p, 0);
        TextView msg = Ui.text(this, "Files you already saved stay where they are; new ones go to Pictures/" + Branding.name(id)
                + " and Download/" + Branding.name(id) + ".\n\nYour home-screen icon may disappear. If it does, add "
                + Branding.name(id) + " again from the app drawer; it can take a few seconds to appear.", 14, Ui.MUTED, false);
        l.addView(msg);
        l.addView(cb);
        new AlertDialog.Builder(this)
                .setTitle("Switch to " + Branding.name(id) + "?")
                .setView(l)
                .setPositiveButton("Switch", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        Branding.switchTo(SettingsActivity.this, id, cb.isChecked());
                        toast("Now " + Branding.name(id));
                        recreate();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateExample() {
        example.setText("Example: " + Naming.defaultName(prefs));
    }

    // ------------------------------------------------------------------ row builders

    private interface Getter { int get(); }

    private interface Setter { void set(int v); }

    private interface TextGetter { String get(); }

    private interface TextSetter { void set(String v); }

    private void section(String title) {
        TextView t = Ui.text(this, title, 13, Ui.ACCENT, true);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 22), Ui.dp(this, 20), Ui.dp(this, 6));
        list.addView(t);
        Ui.reveal(t, 25L * Math.min(14, rowIndex++));
    }

    private LinearLayout row(int icon, String title, String value) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 60));
        r.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        r.setBackground(Ui.ripple(null, true));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.icon(this, icon, Ui.MUTED));
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        texts.addView(Ui.text(this, title, 15.5f, Ui.LIGHT, false));
        TextView v = Ui.text(this, value == null ? "" : value, 13, Ui.MUTED, false);
        v.setPadding(0, Ui.dp(this, 2), 0, 0);
        Ui.setVisible(v, value != null && value.length() > 0);
        texts.addView(v);
        r.addView(texts, Ui.weight(1));
        list.addView(r, new LinearLayout.LayoutParams(-1, -2));
        Ui.reveal(r, 25L * Math.min(14, rowIndex++));
        return r;
    }

    private static TextView valueOf(LinearLayout row) {
        return (TextView) ((LinearLayout) row.getChildAt(1)).getChildAt(1);
    }

    private static int indexOf(int[] a, int v) {
        for (int i = 0; i < a.length; i++) if (a[i] == v) return i;
        return 0;
    }

    private void choice(int icon, final String title, final String[] labels, final int[] vals, final Getter g, final Setter s) {
        final LinearLayout r = row(icon, title, labels[indexOf(vals, g.get())]);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(title)
                        .setSingleChoiceItems(labels, indexOf(vals, g.get()), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                s.set(vals[which]);
                                TextView val = valueOf(r);
                                val.setText(labels[which]);
                                val.setVisibility(View.VISIBLE);
                                updateExample();
                                d.dismiss();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void textRow(int icon, final String title, final String hint, final boolean multi, final TextGetter g, final TextSetter s) {
        String cur = g.get();
        final LinearLayout r = row(icon, title, cur.isEmpty() ? hint : cur);
        r.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText et = new EditText(SettingsActivity.this);
                et.setSingleLine(!multi);
                et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
                et.setText(g.get());
                et.setHint(multi ? "e.g. City Hospital, Dr Rao, Brain" : hint);
                et.setTextColor(Ui.LIGHT);
                et.setHintTextColor(Ui.MUTED);
                et.setSelectAllOnFocus(true);
                LinearLayout l = new LinearLayout(SettingsActivity.this);
                l.setOrientation(LinearLayout.VERTICAL);
                int p = Ui.dp(SettingsActivity.this, 22);
                l.setPadding(p, Ui.dp(SettingsActivity.this, 8), p, 0);
                l.addView(et, new LinearLayout.LayoutParams(-1, -2));
                if (multi) {
                    TextView note = Ui.text(SettingsActivity.this, "Separate words with commas. They appear as chips when you save.", 12.5f, Ui.MUTED, false);
                    note.setPadding(0, Ui.dp(SettingsActivity.this, 6), 0, 0);
                    l.addView(note);
                }
                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle(title)
                        .setView(l)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                String t = et.getText().toString().trim();
                                if (!multi && t.isEmpty()) t = "Scan";
                                s.set(t);
                                valueOf(r).setText(t.isEmpty() ? hint : t);
                                updateExample();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void toggle(int icon, String title, String sub, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = row(icon, title, sub);
        final Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        r.addView(sw);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sw.toggle(); }
        });
    }

    private void info(int icon, String title, String value) {
        row(icon, title, value).setClickable(false);
    }
}
