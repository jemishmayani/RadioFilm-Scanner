package com.filmscan.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** The save/share sheet and the background export, shared by every screen that exports. */
public final class ExportUi {
    private ExportUi() {}

    public interface Choice { void chosen(int format, boolean split, String name, boolean share); }

    public interface Named { void named(String name); }

    /** Runs on the export thread after a successful export, before the UI callback. */
    public interface After { void run(Exporter.Result r) throws Exception; }

    public interface Done { void done(Exporter.Result r); }

    private static final int[] FORMATS = {Exporter.PDF, Exporter.JPEG, Exporter.PNG};
    private static final String[] FORMAT_LABELS = {"PDF", "JPG", "PNG"};
    private static final int[] FORMAT_ICONS = {R.drawable.ic_pdf, R.drawable.ic_jpg, R.drawable.ic_png};

    /** Name to start with: the saved scan's own name when editing one, else from the naming settings. */
    public static String defaultName() {
        App app = App.get();
        SavedStore.Entry e = app.editingEntry();
        return e != null ? e.name : Naming.defaultName(app.prefs());
    }

    private static LinearLayout header(Activity a, int icon, String title, String sub, final Dialog[] holder) {
        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView badge = new ImageView(a);
        badge.setImageDrawable(Ui.icon(a, icon, Ui.ON_ACCENT));
        badge.setScaleType(ImageView.ScaleType.CENTER);
        badge.setBackground(Ui.oval(Ui.ACCENT));
        head.addView(badge, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        LinearLayout titles = new LinearLayout(a);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(Ui.dp(a, 14), 0, 0, 0);
        titles.addView(Ui.text(a, title, 19, Ui.LIGHT, true));
        if (sub != null) titles.addView(Ui.text(a, sub, 13, Ui.MUTED, false));
        head.addView(titles, Ui.weight(1));
        ImageView close = Ui.iconButton(a, R.drawable.ic_close, "Close");
        head.addView(close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (holder[0] != null) holder[0].dismiss(); }
        });
        return head;
    }

    private static Dialog show(Activity a, LinearLayout c, NameEditor ne, View last) {
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(c, new android.widget.FrameLayout.LayoutParams(-1, -2));
        Dialog d = Ui.sheet(a, scroll);
        long delay = 70;
        for (View r : ne.rows) { Ui.reveal(r, delay); delay += 40; }
        if (last != null && last.getVisibility() == View.VISIBLE) Ui.reveal(last, delay);
        return d;
    }

    // ================================================================== save / share sheet

    /** Save sheet: name with suggestions, icon-only format tiles, and Save plus Share. */
    public static void dialog(final Activity a, List<Page> pages, String name, final boolean shareOnly, final Choice choice) {
        final Prefs prefs = App.get().prefs();
        final int n = pages.size();
        boolean anyGrid = false;
        for (Page p : pages) if (p.hasGrid()) anyGrid = true;
        final boolean hasGrid = anyGrid;
        final int preferred = prefs.saveAs();
        final int[] format = {preferred >= 0 ? preferred : prefs.format()};
        final boolean[] split = {prefs.splitGrid()};
        final Dialog[] holder = new Dialog[1];
        final Runnable[] estimateHook = new Runnable[1];

        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        c.setPadding(pad, 0, pad, 0);
        c.addView(header(a, shareOnly ? R.drawable.ic_share : R.drawable.ic_save, shareOnly ? "Share" : "Save",
                n == 1 ? "1 page" : n + " pages", holder), new LinearLayout.LayoutParams(-1, -2));
        final NameEditor ne = new NameEditor(a, name, n);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(a, 16);
        c.addView(ne.view, nl);

        // format tiles: the icon says it all
        final LinearLayout tiles = new LinearLayout(a);
        final List<LinearLayout> tileViews = new ArrayList<LinearLayout>();
        for (int i = 0; i < FORMATS.length; i++) {
            final int f = FORMATS[i];
            LinearLayout t = tile(a, FORMAT_ICONS[i], null, FORMAT_LABELS[i]);
            t.setTag(f);
            tiles.addView(t, tileParams(a, i > 0));
            tileViews.add(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    format[0] = f;
                    for (LinearLayout tv : tileViews) styleTile(a, tv, (Integer) tv.getTag() == format[0], true);
                    if (estimateHook[0] != null) estimateHook[0].run();
                }
            });
        }
        final LinearLayout splitTile = tile(a, R.drawable.ic_split, "Panels", "Save each panel separately");
        splitTile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { split[0] = !split[0]; styleTile(a, splitTile, split[0], true); if (estimateHook[0] != null) estimateHook[0].run(); }
        });
        for (LinearLayout tv : tileViews) styleTile(a, tv, (Integer) tv.getTag() == format[0], false);
        styleTile(a, splitTile, split[0], false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        tlp.topMargin = Ui.dp(a, 18);
        c.addView(tiles, tlp);
        if (preferred < 0) {
            if (hasGrid) tiles.addView(splitTile, tileParams(a, true));
        } else {
            // preferred format from Settings: a compact chip, tap to change for this time only
            tiles.setVisibility(View.GONE);
            LinearLayout line = new LinearLayout(a);
            line.setGravity(Gravity.CENTER_VERTICAL);
            final TextView chosen = Ui.chip(a, "", R.drawable.ic_chevron_down);
            android.graphics.drawable.Drawable fi = Ui.icon(a, FORMAT_ICONS[indexOf(preferred)], Ui.ACCENT);
            int s = Ui.dp(a, 26);
            fi.setBounds(0, 0, s, s);
            chosen.setCompoundDrawablesRelative(fi, null, chosen.getCompoundDrawablesRelative()[2], null);
            chosen.setCompoundDrawablePadding(Ui.dp(a, 4));
            chosen.setMinHeight(Ui.dp(a, 48));
            chosen.setContentDescription("Format " + FORMAT_LABELS[indexOf(preferred)] + ", tap to change");
            line.addView(chosen);
            if (hasGrid) {
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(a, 88), Ui.dp(a, 60));
                sl.leftMargin = Ui.dp(a, 12);
                line.addView(splitTile, sl);
            }
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            ll.topMargin = Ui.dp(a, 16);
            c.addView(line, ll);
            chosen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    chosen.setVisibility(View.GONE);
                    tiles.setVisibility(View.VISIBLE);
                    Ui.reveal(tiles, 0);
                }
            });
        }

        // PDF options (shown only when PDF is chosen)
        final LinearLayout pdfRow = new LinearLayout(a);
        pdfRow.setGravity(Gravity.CENTER_VERTICAL);
        pdfRow.setPadding(Ui.dp(a, 14), Ui.dp(a, 10), Ui.dp(a, 8), Ui.dp(a, 10));
        pdfRow.setBackground(Ui.ripple(Ui.round(Ui.PANEL_HI, Ui.dp(a, 14)), true));
        ImageView pdfIcon = new ImageView(a);
        pdfIcon.setImageDrawable(Ui.icon(a, R.drawable.ic_pdf, Ui.ACCENT));
        pdfRow.addView(pdfIcon, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
        LinearLayout pdfTexts = new LinearLayout(a);
        pdfTexts.setOrientation(LinearLayout.VERTICAL);
        pdfTexts.setPadding(Ui.dp(a, 12), 0, 0, 0);
        pdfTexts.addView(Ui.text(a, "PDF options", 14, Ui.LIGHT, true));
        final TextView pdfSummary = Ui.text(a, PdfOptions.summary(prefs), 12.5f, Ui.MUTED, false);
        pdfTexts.addView(pdfSummary);
        pdfRow.addView(pdfTexts, Ui.weight(1));
        ImageView chev = new ImageView(a);
        chev.setImageDrawable(Ui.icon(a, R.drawable.ic_next, Ui.MUTED));
        pdfRow.addView(chev, new LinearLayout.LayoutParams(Ui.dp(a, 24), Ui.dp(a, 24)));
        pdfRow.setClickable(true);
        Ui.pressable(pdfRow);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(-1, -2);
        prl.topMargin = Ui.dp(a, 14);
        c.addView(pdfRow, prl);

        // estimated size and free space
        final LinearLayout est = new LinearLayout(a);
        est.setGravity(Gravity.CENTER_VERTICAL);
        final ImageView estIcon = new ImageView(a);
        estIcon.setImageDrawable(Ui.icon(a, R.drawable.ic_storage, Ui.MUTED));
        est.addView(estIcon, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
        final TextView estText = Ui.text(a, "", 12.5f, Ui.MUTED, false);
        estText.setPadding(Ui.dp(a, 8), 0, 0, 0);
        est.addView(estText, Ui.weight(1));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.topMargin = Ui.dp(a, 14);
        c.addView(est, el);
        // where the files will be saved (not shown when only sharing)
        LinearLayout where = new LinearLayout(a);
        where.setGravity(Gravity.CENTER_VERTICAL);
        ImageView whereIcon = new ImageView(a);
        whereIcon.setImageDrawable(Ui.icon(a, R.drawable.ic_saved, Ui.MUTED));
        where.addView(whereIcon, new LinearLayout.LayoutParams(Ui.dp(a, 18), Ui.dp(a, 18)));
        final TextView whereText = Ui.text(a, "", 12.5f, Ui.MUTED, false);
        whereText.setPadding(Ui.dp(a, 8), 0, 0, 0);
        where.addView(whereText, Ui.weight(1));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-1, -2);
        wl.topMargin = Ui.dp(a, 6);
        c.addView(where, wl);
        if (shareOnly) where.setVisibility(View.GONE);
        final List<Page> pageList = pages;
        final Runnable updateEstimate = new Runnable() {
            @Override
            public void run() {
                long need = Storage.estimate(a, pageList, format[0]);
                long free = Storage.freeBytes();
                int files = Storage.fileCount(pageList, format[0], hasGrid && split[0]);
                String base = "\u2248 " + Storage.human(need) + " \u00b7 " + (files == 1 ? "1 file" : files + " files");
                int color = Ui.MUTED;
                if (free >= 0 && need > free - 50L * 1024 * 1024) {
                    base = "Not enough space: needs \u2248 " + Storage.human(need) + ", " + Storage.human(free) + " free";
                    color = Ui.DANGER;
                } else if (free >= 0 && free - need < 500L * 1024 * 1024) {
                    base += " \u00b7 storage almost full (" + Storage.human(free) + " free)";
                    color = Ui.ACCENT;
                } else if (free >= 0) {
                    base += " \u00b7 " + Storage.human(free) + " free";
                }
                estText.setText(base);
                estText.setTextColor(color);
                estIcon.getDrawable().setTint(color);
                pdfRow.setVisibility(format[0] == Exporter.PDF ? View.VISIBLE : View.GONE);
                pdfSummary.setText(PdfOptions.summary(prefs));
                whereText.setText(format[0] == Exporter.PDF ? "Saves to Download/" + Exporter.folder()
                        : "Saves to Pictures/" + Exporter.folder() + " (Gallery)");
            }
        };
        updateEstimate.run();
        estimateHook[0] = updateEstimate;
        final List<Page> optionPages = pages;
        pdfRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { PdfOptions.show(a, optionPages, updateEstimate); }
        });

        // actions: Share (round) + Save (wide), or just Share
        LinearLayout actions = new LinearLayout(a);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        ImageView shareBtn = null;
        if (!shareOnly) {
            shareBtn = new ImageView(a);
            shareBtn.setImageDrawable(Ui.icon(a, R.drawable.ic_share, Ui.LIGHT));
            shareBtn.setScaleType(ImageView.ScaleType.CENTER);
            shareBtn.setBackground(Ui.ripple(Ui.oval(Ui.PANEL_HI), true));
            shareBtn.setContentDescription("Share");
            shareBtn.setClickable(true);
            Ui.pressable(shareBtn);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(Ui.dp(a, 54), Ui.dp(a, 54));
            sp.rightMargin = Ui.dp(a, 12);
            actions.addView(shareBtn, sp);
        }
        TextView go = Ui.button(a, shareOnly ? "Share" : "Save", true);
        go.setTextSize(16.5f);
        go.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(a, shareOnly ? R.drawable.ic_share : R.drawable.ic_save, Ui.ON_ACCENT), null, null, null);
        go.setCompoundDrawablePadding(Ui.dp(a, 10));
        go.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(a, 27)), true));
        actions.addView(go, new LinearLayout.LayoutParams(0, Ui.dp(a, 54), 1));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-1, -2);
        alp.topMargin = Ui.dp(a, 22);
        c.addView(actions, alp);

        final Dialog d = show(a, c, ne, tiles);
        holder[0] = d;
        View.OnClickListener finish = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean share = shareOnly || v.getTag() != null;
                String nm = ne.name();
                if (preferred < 0) prefs.format(format[0]);
                if (hasGrid) prefs.splitGrid(split[0]);
                d.dismiss();
                choice.chosen(format[0], hasGrid && split[0], nm, share);
            }
        };
        go.setOnClickListener(finish);
        if (shareBtn != null) {
            shareBtn.setTag("share");
            shareBtn.setOnClickListener(finish);
        }
    }

    /** Rename sheet with the same suggestions as saving. */
    public static void renameSheet(Activity a, String current, int pages, final Named named) {
        final Dialog[] holder = new Dialog[1];
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        c.setPadding(pad, 0, pad, 0);
        c.addView(header(a, R.drawable.ic_rename, "Rename", null, holder), new LinearLayout.LayoutParams(-1, -2));
        final NameEditor ne = new NameEditor(a, current, pages);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(a, 16);
        c.addView(ne.view, nl);
        TextView go = Ui.button(a, "Rename", true);
        go.setTextSize(16.5f);
        go.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(a, 27)), true));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, Ui.dp(a, 54));
        glp.topMargin = Ui.dp(a, 22);
        c.addView(go, glp);
        final Dialog d = show(a, c, ne, null);
        holder[0] = d;
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String nm = ne.name();
                d.dismiss();
                named.named(nm);
            }
        });
    }

    private static int indexOf(int format) {
        for (int i = 0; i < FORMATS.length; i++) if (FORMATS[i] == format) return i;
        return 0;
    }

    private static LinearLayout.LayoutParams tileParams(Activity a, boolean gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 72), 1);
        if (gap) lp.leftMargin = Ui.dp(a, 10);
        return lp;
    }

    private static LinearLayout tile(Activity a, int icon, String label, String description) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setContentDescription(description);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(Ui.icon(a, icon, Ui.LIGHT));
        int s = Ui.dp(a, label == null ? 38 : 28);
        t.addView(iv, new LinearLayout.LayoutParams(s, s));
        if (label != null) {
            TextView tv = Ui.text(a, label, 12.5f, Ui.MUTED, true);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, Ui.dp(a, 4), 0, 0);
            t.addView(tv, new LinearLayout.LayoutParams(-2, -2));
        }
        Ui.pressable(t);
        return t;
    }

    private static void styleTile(Activity a, LinearLayout t, boolean on, boolean animate) {
        t.setBackground(Ui.ripple(on ? Ui.round(Ui.accentA(0x24), Ui.dp(a, 16), Ui.ACCENT, Ui.dp(a, 2))
                : Ui.round(Ui.PANEL_HI, Ui.dp(a, 16)), true));
        ((ImageView) t.getChildAt(0)).getDrawable().setTint(on ? Ui.ACCENT : Ui.LIGHT);
        if (t.getChildCount() > 1) ((TextView) t.getChildAt(1)).setTextColor(on ? Ui.ACCENT : Ui.MUTED);
        if (animate && on) {
            View icon = t.getChildAt(0);
            icon.setScaleX(0.7f);
            icon.setScaleY(0.7f);
            icon.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(320).setInterpolator(new OvershootInterpolator(3f)).start();
        }
    }

    // ================================================================== export

    public static void run(final Activity a, List<Page> source, final int format, final boolean split, final String name,
                           final boolean forShare, final After after, final Done done) {
        final App app = App.get();
        final List<Page> pages = new ArrayList<Page>();
        for (Page p : source) pages.add(p.copy());
        // keep the screen from turning mid-export, so the result always reaches this screen
        a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        final AlertDialog dlg = CameraActivity.progressDialog(a, "Preparing full-resolution pages…");
        final TextView msg = (TextView) dlg.findViewById(android.R.id.message);
        app.export.execute(new Runnable() {
            @Override
            public void run() {
                final Exporter.Result r = Exporter.run(a.getApplicationContext(), pages, format, split, name, forShare, new Exporter.Progress() {
                    @Override
                    public void step(final int d, final int total) {
                        app.ui(new Runnable() {
                            @Override public void run() { CameraActivity.setProgress(dlg, "Saving at full resolution… " + d + " of " + total, d, total); }
                        });
                    }
                });
                if (r.error == null && after != null) {
                    try {
                        after.run(r);
                    } catch (Exception e) {
                        r.error = "The files were saved, but the scan could not be added to Saved scans: " + e.getMessage();
                    }
                }
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        try { dlg.dismiss(); } catch (Exception ignored) { }
                        a.setRequestedOrientation(BaseActivity.normalOrientation(a));
                        if (a.isFinishing()) return;
                        if (r.error != null) {
                            new AlertDialog.Builder(a).setTitle("Export problem").setMessage(r.error).setPositiveButton("OK", null).show();
                            return;
                        }
                        Naming.remember(app.prefs(), name);
                        Suggest.afterSave(app.prefs(), name);
                        if (forShare) share(a, r);
                        if (done != null) done.done(r);
                    }
                });
            }
        });
    }

    public static void share(Activity a, Exporter.Result r) {
        if (r.uris.isEmpty()) return;
        boolean allImages = true;
        for (String m : r.mimes) if (!m.startsWith("image/")) allImages = false;
        String type = r.mimes.size() == 1 ? r.mimes.get(0) : (allImages ? "image/*" : "*/*");
        Intent i;
        if (r.uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, r.uris.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<Uri>(r.uris));
        }
        i.setType(type);
        ClipData cd = ClipData.newRawUri("", r.uris.get(0));
        for (int k = 1; k < r.uris.size(); k++) cd.addItem(new ClipData.Item(r.uris.get(k)));
        i.setClipData(cd);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            a.startActivity(Intent.createChooser(i, "Share scan"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No app available to share with", Toast.LENGTH_SHORT).show();
        }
    }
}
