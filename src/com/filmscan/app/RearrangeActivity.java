package com.filmscan.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Preview: the step between Edit and Export. Shows each finished page (cropped, filtered,
 * anonymised) full screen; swipe left and right like a gallery, pinch to zoom. The tools below
 * open the page in the editor, rotate it, retake or delete it; Reorder moves pages around.
 */
public final class RearrangeActivity extends BaseActivity {
    /** Result extra: which editor tool to open on RESULT_PAGE ("crop", "filter", "adjust", "redact"). */
    public static final String RESULT_ACTION = "action";
    /** Result code: the scan was exported (or discarded) and the editor should close. */
    public static final int RESULT_EXPORTED = RESULT_FIRST_USER + 1;
    private static final int REQ_STORAGE = 41, REQ_RETAKE = 42;
    private static final String EXTRA_START = "start";

    private App app;
    private Session session;
    private Page page;
    private FrameLayout stage;
    private ZoomImageView view;
    private ProgressBar spinner;
    private TextView titleTv, counterTv;
    private LinearLayout strip;
    private HorizontalScrollView stripScroll;
    private PageSwiper swiper;
    private Runnable pendingSave;
    private int renderGen;

    /** Preview opened on a given page. */
    public static Intent intent(android.content.Context c, String pageId) {
        return new Intent(c, RearrangeActivity.class).putExtra(EXTRA_START, pageId);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        session = app.session();
        if (session.pages.isEmpty()) { finish(); return; }
        String start = b != null ? b.getString("page") : getIntent().getStringExtra(EXTRA_START);
        page = session.find(start);
        if (page == null) page = session.pages.get(0);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);

        // top: back, title, page counter, reorder, discard
        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back to editing");
        top.addView(back);
        titleTv = Ui.title(this, "Preview");
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(titleTv, Ui.weight(1));
        counterTv = Ui.text(this, "", 14, Ui.MUTED, true);
        counterTv.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 4), 0);
        top.addView(counterTv);
        ImageView reorder = Ui.iconButton(this, R.drawable.ic_rearrange, "Reorder pages");
        top.addView(reorder);
        ImageView clear = Ui.iconButton(this, R.drawable.ic_delete, "Discard all pages");
        top.addView(clear);
        Ui.setVisible(clear, !app.isEditingSaved());
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        // the page itself: swipe for the next one, pinch or double-tap to zoom
        stage = new FrameLayout(this);
        view = new ZoomImageView(this);
        stage.addView(view, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this);
        stage.addView(spinner, Ui.frame(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.CENTER));
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        // thumbnails: tap to jump
        stripScroll = new HorizontalScrollView(this);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip = new LinearLayout(this);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        stripScroll.addView(strip);
        col.addView(stripScroll, new LinearLayout.LayoutParams(-1, Ui.dp(this, 84)));

        // tools for this page
        View div = new View(this);
        div.setBackgroundColor(0x22E6F0F7);
        col.addView(div, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout tools = new LinearLayout(this);
        tools.setBackgroundColor(Ui.PANEL);
        LinearLayout tCrop = Ui.tool(this, R.drawable.ic_crop, "Crop");
        LinearLayout tFilter = Ui.tool(this, R.drawable.ic_filter, "Filters");
        LinearLayout tHide = Ui.tool(this, R.drawable.ic_hide, "Anonymise");
        LinearLayout tRot = Ui.tool(this, R.drawable.ic_rotate, "Rotate");
        LinearLayout tRetake = Ui.tool(this, R.drawable.ic_retake, "Retake");
        LinearLayout tDel = Ui.tool(this, R.drawable.ic_delete, "Delete");
        for (LinearLayout t : new LinearLayout[]{tCrop, tFilter, tHide, tRot, tRetake, tDel}) tools.addView(t, Ui.weight(1));
        col.addView(tools, new LinearLayout.LayoutParams(-1, -2));

        // export
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(Ui.PANEL);
        int bp = Ui.dp(this, 12);
        bar.setPadding(bp, Ui.dp(this, 4), bp, Ui.dp(this, 10));
        TextView export = Ui.button(this, "Export", true);
        export.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(this, R.drawable.ic_save, Ui.ON_ACCENT), null, null, null);
        export.setCompoundDrawablePadding(Ui.dp(this, 8));
        export.setGravity(Gravity.CENTER);
        bar.addView(export, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(col);

        swiper = new PageSwiper(stage, new PageSwiper.Host() {
            @Override
            public boolean canGo(int dir) {
                int i = session.indexOf(page) + dir;
                return i >= 0 && i < session.pages.size();
            }

            @Override public boolean beforeGo(int dir) { return true; }

            @Override
            public void go(int dir) {
                int i = session.indexOf(page) + dir;
                if (i < 0 || i >= session.pages.size()) return;
                page = session.pages.get(i);
                show(false);
            }
        });
        view.setSwipeListener(swiper.gesture);

        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        setBackHandler(new Runnable() { @Override public void run() { finish(); } });
        export.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { save(); } });
        reorder.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showReorder(); } });
        clear.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmDiscard(); } });
        tCrop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { edit("crop"); } });
        tFilter.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { edit("filter"); } });
        tHide.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { edit("redact"); } });
        tRot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { rotate(); } });
        tRetake.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivityForResult(CameraActivity.intentRetake(RearrangeActivity.this, page.id), REQ_RETAKE); }
        });
        tDel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmDelete(); } });

        show(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (page != null) out.putString("page", page.id);
    }

    private boolean firstResume = true;

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) { firstResume = false; return; }
        if (session.pages.isEmpty()) { finish(); return; }
        if (session.find(page.id) == null) page = session.pages.get(0);
        show(true);   // a retake elsewhere may have changed the page
    }

    // ================================================================== showing pages

    /** Shows the current page; rebuilds the thumbnails too when asked. */
    private void show(boolean rebuildStrip) {
        int i = session.indexOf(page), n = session.pages.size();
        SavedStore.Entry editing = app.editingEntry();
        titleTv.setText(editing != null ? editing.name : "Preview");
        counterTv.setText((i + 1) + " / " + n);
        if (rebuildStrip) buildStrip();
        selectThumb();
        render();
    }

    /** Best image already in memory for a page: the full preview, else any thumbnail. */
    private Bitmap placeholder(Page p) {
        String k = p.renderKey();
        Bitmap b = app.cached(k + "@pv");
        if (b == null) b = app.cached(k + "@thumb");
        if (b == null) b = app.cached(k + "@strip");
        if (b == null) b = app.cached(k + "@grid");
        return b;
    }

    private void render() {
        final int g = ++renderGen;
        final String key = page.renderKey() + "@pv";
        Bitmap c = app.cached(key);
        if (c != null) {
            spinner.setVisibility(View.GONE);
            view.setBitmap(c, false);
            prefetchAround(g);
            return;
        }
        Bitmap ph = placeholder(page);
        view.setBitmap(ph, false);
        // only show the spinner if there is nothing to look at, or it takes a while
        spinner.setVisibility(ph == null ? View.VISIBLE : View.GONE);
        final Page snap = page.copy();
        app.run(new Runnable() {
            @Override
            public void run() {
                if (g != renderGen) return;   // already swiped on
                final Bitmap b = Imaging.renderFinished(snap, 1600);
                if (b != null) app.cache(key, b);
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (g != renderGen) return;
                        spinner.setVisibility(View.GONE);
                        if (b != null) view.setBitmap(b, false);
                        prefetchAround(g);
                    }
                });
            }
        });
    }

    /** Prepares the next and previous pages, so a swipe shows them straight away. */
    private void prefetchAround(final int g) {
        int i = session.indexOf(page);
        for (int d : new int[]{1, -1}) {
            int j = i + d;
            if (j < 0 || j >= session.pages.size()) continue;
            final Page n = session.pages.get(j);
            final String key = n.renderKey() + "@pv";
            if (app.cached(key) != null) continue;
            final Page snap = n.copy();
            app.run(new Runnable() {
                @Override
                public void run() {
                    if (g != renderGen || app.cached(key) != null) return;   // moved on: skip
                    Bitmap b = Imaging.renderFinished(snap, 1600);
                    if (b != null) app.cache(key, b);
                }
            });
        }
    }

    private void buildStrip() {
        strip.removeAllViews();
        int w = Ui.dp(this, 52), h = Ui.dp(this, 68);
        for (int i = 0; i < session.pages.size(); i++) {
            final Page p = session.pages.get(i);
            FrameLayout cell = new FrameLayout(this);
            cell.setTag(p.id);
            cell.setPadding(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
            final ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackgroundColor(0xFF22384D);
            cell.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            TextView num = Ui.text(this, String.valueOf(i + 1), 10, Ui.ON_ACCENT, true);
            num.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 8)));
            num.setGravity(Gravity.CENTER);
            num.setMinWidth(Ui.dp(this, 16));
            num.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            cell.addView(num, Ui.frame(-2, Ui.dp(this, 16), Gravity.TOP | Gravity.START));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
            lp.rightMargin = Ui.dp(this, 8);
            strip.addView(cell, lp);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (p != page) jump(p); }
            });
            final String key = p.renderKey() + "@thumb";
            Bitmap c = app.cached(key);
            if (c != null) iv.setImageBitmap(c);
            else {
                Bitmap quick = app.cached(p.renderKey() + "@strip");
                if (quick != null) iv.setImageBitmap(quick);
                final Page snap = p.copy();
                app.run(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap t = Imaging.renderFinished(snap, 240);
                        if (t != null) app.cache(key, t);
                        app.ui(new Runnable() { @Override public void run() { if (t != null) iv.setImageBitmap(t); } });
                    }
                });
            }
        }
    }

    /** Jump to a page from the thumbnails: a quick crossfade rather than a hard cut. */
    private void jump(final Page p) {
        stage.animate().cancel();
        stage.animate().alpha(0f).setDuration(90).withEndAction(new Runnable() {
            @Override
            public void run() {
                page = p;
                show(false);
                stage.animate().alpha(1f).setDuration(160).start();
            }
        }).start();
    }

    private void selectThumb() {
        for (int i = 0; i < strip.getChildCount(); i++) {
            View c = strip.getChildAt(i);
            boolean on = page.id.equals(c.getTag());
            c.setBackground(on ? Ui.round(Ui.ACCENT, Ui.dp(this, 6)) : null);
            c.animate().alpha(on ? 1f : 0.6f).scaleX(on ? 1.06f : 1f).scaleY(on ? 1.06f : 1f).setDuration(160).start();
            if (on) {
                final View cell = c;
                stripScroll.post(new Runnable() {
                    @Override public void run() { stripScroll.smoothScrollTo(Math.max(0, cell.getLeft() - stripScroll.getWidth() / 2 + cell.getWidth() / 2), 0); }
                });
            }
        }
    }

    // ================================================================== page actions

    /** Back to the editor on this page with the given tool open. */
    private void edit(String tool) {
        setResult(RESULT_OK, new Intent().putExtra(EditActivity.RESULT_PAGE, page.id).putExtra(RESULT_ACTION, tool));
        finish();
    }

    private boolean rotating;

    /** Rotate: turns the picture on screen at once, fitted to the new shape; no reload, no spinner. */
    private void rotate() {
        if (rotating) return;
        final Bitmap cur = view.getBitmap();
        final String oldThumb = page.renderKey() + "@thumb";
        page.turn();
        page.version++;
        session.save();
        refreshSaved();
        final Bitmap thumb = app.cached(oldThumb);
        if (thumb != null) app.cache(page.renderKey() + "@thumb", turned(thumb));
        if (cur == null || view.getWidth() == 0) { render(); buildStrip(); selectThumb(); return; }
        rotating = true;
        float W = view.getWidth(), H = view.getHeight(), w = cur.getWidth(), h = cur.getHeight();
        float before = Math.min(W / w, H / h), after = Math.min(W / h, H / w);
        final float k = after / before;
        final Bitmap rotated = turned(cur);
        final String key = page.renderKey() + "@pv";
        if (rotated != null && Math.max(cur.getWidth(), cur.getHeight()) >= 1400) app.cache(key, rotated);   // full-size preview: reuse it
        view.animate().cancel();
        view.animate().rotation(90).scaleX(k).scaleY(k).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        view.setRotation(0);
                        view.setScaleX(1f);
                        view.setScaleY(1f);
                        if (rotated != null) view.setBitmap(rotated, false);
                        rotating = false;
                        render();   // sharpens it if only a thumbnail was showing
                        updateThumb(page);
                    }
                }).start();
    }

    /** A quarter turn clockwise of a bitmap (null if memory is short). */
    private static Bitmap turned(Bitmap b) {
        try {
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(90);
            return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Refreshes one thumbnail in the strip without rebuilding the others. */
    private void updateThumb(Page p) {
        for (int i = 0; i < strip.getChildCount(); i++) {
            View c = strip.getChildAt(i);
            if (!p.id.equals(c.getTag())) continue;
            final ImageView iv = (ImageView) ((FrameLayout) c).getChildAt(0);
            final String key = p.renderKey() + "@thumb";
            Bitmap t = app.cached(key);
            if (t != null) { iv.setImageBitmap(t); return; }
            final Page snap = p.copy();
            app.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap b = Imaging.renderFinished(snap, 240);
                    if (b != null) app.cache(key, b);
                    app.ui(new Runnable() { @Override public void run() { if (b != null) iv.setImageBitmap(b); } });
                }
            });
            return;
        }
    }

    private void confirmDelete() {
        final Page p = page;
        new AlertDialog.Builder(this)
                .setTitle("Delete page " + (session.indexOf(p) + 1) + "?")
                .setMessage("The other pages keep their order.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final int i = session.indexOf(p);
                        stage.animate().cancel();
                        stage.animate().alpha(0f).translationY(-Ui.dp(RearrangeActivity.this, 40)).scaleX(0.92f).scaleY(0.92f)
                                .setDuration(180).withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        session.remove(p);
                                        refreshSaved();
                                        if (session.pages.isEmpty()) { finish(); return; }
                                        page = session.pages.get(Math.min(i, session.pages.size() - 1));
                                        show(true);
                                        stage.setTranslationY(Ui.dp(RearrangeActivity.this, 24));
                                        stage.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setDuration(240)
                                                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                                    }
                                }).start();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDiscard() {
        new AlertDialog.Builder(this)
                .setTitle("Discard all pages?")
                .setMessage("Pages you have not saved will be lost. Saved scans are not affected.")
                .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        session.clear();
                        startActivity(CameraActivity.intent(RearrangeActivity.this));
                        setResult(RESULT_EXPORTED);
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Editing a saved scan in place: keep its cover and page count current. */
    private void refreshSaved() {
        if (!app.isEditingSaved()) return;
        SavedStore.Entry e = app.editingEntry();
        if (e != null) app.saved().refresh(e);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_RETAKE) return;
        String id = data == null ? null : data.getStringExtra(EditActivity.RESULT_PAGE);
        Page p = session.find(id);
        if (p == null) return;
        page = p;
        int n = session.indexOf(p) + 1;
        Toast.makeText(this, p.redact.isEmpty()
                ? "Page " + n + " retaken. Tap Crop to check the edges."
                : "Page " + n + " retaken. Check the edges and that the anonymised areas still cover what they should.", Toast.LENGTH_LONG).show();
        // onResume shows it
    }

    // ================================================================== reorder

    private void showReorder() {
        final ReorderGrid grid = new ReorderGrid(this);
        List<View> tiles = new ArrayList<View>();
        for (int i = 0; i < session.pages.size(); i++) tiles.add(tile(session.pages.get(i), i));
        grid.setTiles(tiles);
        grid.setListener(new ReorderGrid.Listener() {
            @Override
            public void onOrderChanged(List<View> order) {
                List<Page> pages = new ArrayList<Page>();
                for (View v : order) {
                    Page p = session.find((String) v.getTag());
                    if (p != null) pages.add(p);
                }
                session.setOrder(pages);
                refreshSaved();
                for (int i = 0; i < order.size(); i++) {
                    TextView badge = (TextView) ((FrameLayout) order.get(i)).getChildAt(1);
                    String s = String.valueOf(i + 1);
                    if (!s.contentEquals(badge.getText())) {
                        badge.setText(s);
                        badge.setScaleX(0.6f);
                        badge.setScaleY(0.6f);
                        badge.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
                    }
                }
            }
        });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView hint = Ui.text(this, "Hold a page and drag it to a new place.", 13, Ui.MUTED, false);
        hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), 0);
        box.addView(hint);
        ScrollView sv = new ScrollView(this);
        sv.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        box.addView(sv, new LinearLayout.LayoutParams(-1, (int) (getResources().getDisplayMetrics().heightPixels * 0.62f)));
        new AlertDialog.Builder(this)
                .setTitle("Reorder pages")
                .setView(box)
                .setPositiveButton("Done", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface d) { show(true); }
                })
                .show();
    }

    private View tile(Page p, int index) {
        FrameLayout card = new FrameLayout(this);
        card.setTag(p.id);
        card.setBackground(Ui.round(0xFF22384D, Ui.dp(this, 12)));
        int pad = Ui.dp(this, 6);
        card.setPadding(pad, pad, pad, pad);
        final ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = Ui.text(this, String.valueOf(index + 1), 12, Ui.ON_ACCENT, true);
        badge.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 11)));
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(Ui.dp(this, 22));
        badge.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        card.addView(badge, Ui.frame(-2, Ui.dp(this, 22), Gravity.TOP | Gravity.START));
        final String key = p.renderKey() + "@thumb";
        Bitmap c = app.cached(key);
        if (c != null) img.setImageBitmap(c);
        else {
            final Page snap = p.copy();
            app.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap t = Imaging.renderFinished(snap, 240);
                    if (t != null) app.cache(key, t);
                    app.ui(new Runnable() { @Override public void run() { if (t != null) img.setImageBitmap(t); } });
                }
            });
        }
        return card;
    }

    // ================================================================== export

    private void save() {
        if (session.pages.isEmpty()) return;
        ExportUi.dialog(this, session.pages, ExportUi.defaultName(), false, new ExportUi.Choice() {
            @Override
            public void chosen(int format, boolean split, String name, boolean share) {
                if (share) ExportUi.run(RearrangeActivity.this, session.pages, format, split, name, true, null, null);
                else saveNow(format, split, name);
            }
        });
    }

    private void saveNow(final int format, final boolean split, final String name) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingSave = new Runnable() { @Override public void run() { saveNow(format, split, name); } };
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        final List<Page> batch = new ArrayList<Page>();
        for (Page p : session.pages) batch.add(p.copy());
        final String clean = Exporter.sanitize(name);
        final SavedStore.Entry editing = app.editingEntry();
        final boolean[] stored = {false};
        ExportUi.run(this, batch, format, split, name, false, new ExportUi.After() {
            @Override
            public void run(Exporter.Result r) throws Exception {
                if (editing != null) app.saved().update(editing, clean, format, r);   // the same group, updated
                else app.saved().add(clean, format, batch, r);                         // a new group
                stored[0] = true;
            }
        }, new ExportUi.Done() {
            @Override
            public void done(Exporter.Result r) {
                if (!stored[0]) return;
                if (editing != null) {
                    app.endEdit();
                    Toast.makeText(RearrangeActivity.this, "Updated “" + clean + "” in " + r.where, Toast.LENGTH_LONG).show();
                    startActivity(new Intent(RearrangeActivity.this, SavedActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                } else {
                    session.clear();
                    startActivity(CameraActivity.intent(RearrangeActivity.this).putExtra(CameraActivity.EXTRA_SAVED, clean)
                            .putExtra(CameraActivity.EXTRA_WHERE, r.where));
                }
                setResult(RESULT_EXPORTED);
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        if (req != REQ_STORAGE) return;
        Runnable r = pendingSave;
        pendingSave = null;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED && r != null) r.run();
        else Toast.makeText(this, "Storage permission is needed to save to your gallery. You can still share.", Toast.LENGTH_LONG).show();
    }
}
