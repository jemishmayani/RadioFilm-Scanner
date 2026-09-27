package com.filmscan.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.filmscan.core.Collage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Layout: put several pages of a saved scan on one image (a contact sheet). Step 1 selects pages
 * (tap order = layout order); step 2 customises the grid with a live preview and exports JPEG/PNG.
 * Pages are only read; the saved scan is never changed.
 */
public final class LayoutActivity extends BaseActivity {
    private static final String EXTRA_ID = "saved";

    public static Intent intent(Context c, String id) {
        return new Intent(c, LayoutActivity.class).putExtra(EXTRA_ID, id);
    }

    // ---------------------------------------------------------------- settings (kept while the app runs)
    static final String[] GRID_NAMES = {"Auto", "1\u00d72", "2\u00d71", "2\u00d72", "2\u00d73", "3\u00d72", "3\u00d73", "Custom"};
    static final int[][] GRIDS = {null, {1, 2}, {2, 1}, {2, 2}, {2, 3}, {3, 2}, {3, 3}, null};
    static final String[] CANVAS_NAMES = {"Portrait", "Landscape", "Square"};
    static final String[] BORDER_NAMES = {"None", "Thin", "Thick"};
    static final String[] SIZE_NAMES = {"Standard (2400 px)", "High (3600 px)", "Maximum (4800 px)"};
    static final int[] SIZES = {2400, 3600, 4800};
    static final int[] BACKGROUNDS = {0xFFFFFFFF, 0xFFE6E9EC, 0xFF000000, 0xFF0D1B2A};
    static final String[] BACKGROUND_NAMES = {"White", "Light grey", "Black", "Navy"};

    private static int gridChoice = 0, customRows = 2, customCols = 2, canvasChoice = 0, border = 0, background = 0;
    private static int spacing = 30, margin = 30, sizeChoice = 0, format = Exporter.JPEG;
    private static boolean fill = false, labels = false;

    private App app;
    private SavedStore.Entry entry;
    private List<Page> pages = new ArrayList<Page>();
    private final List<Integer> selected = new ArrayList<Integer>();   // indices into pages, in layout order
    private final List<Bitmap> thumbs = new ArrayList<Bitmap>();
    private FrameLayout root;
    private View selectStage, customStage;
    private GridView grid;
    private SelectAdapter selectAdapter;
    private TextView selectCount, nextBtn, sheetNo;
    private CollageView preview;
    private LinearLayout customRow, sheetNav;
    private int sheet, pendingSwap = -1;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        entry = app.saved().find(getIntent().getStringExtra(EXTRA_ID));
        if (entry == null) { finish(); return; }
        pages = app.saved().pages(entry);
        if (pages.isEmpty()) { Toast.makeText(this, "The photos of this scan are missing", Toast.LENGTH_LONG).show(); finish(); return; }
        for (int i = 0; i < pages.size(); i++) thumbs.add(null);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        selectStage = buildSelect();
        root.addView(selectStage, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        setBackHandler(new Runnable() { @Override public void run() { goBack(); } });
        loadThumbs();
    }

    private void goBack() {
        if (customStage != null && customStage.getVisibility() == View.VISIBLE) {
            customStage.setVisibility(View.GONE);
            selectStage.setVisibility(View.VISIBLE);
            Ui.reveal(selectStage, 0);
        } else {
            finish();
        }
    }

    private void loadThumbs() {
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            final Page p = pages.get(i).copy();
            final String key = p.renderKey() + "@grid";
            Bitmap c = app.cached(key);
            if (c != null) { thumbs.set(i, c); continue; }
            app.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap t = Imaging.renderFinished(p, 480);
                    app.cache(key, t);
                    app.ui(new Runnable() {
                        @Override
                        public void run() {
                            thumbs.set(idx, t);
                            if (selectAdapter != null) selectAdapter.notifyDataSetChanged();
                            if (preview != null) preview.invalidate();
                        }
                    });
                }
            });
        }
    }

    // ================================================================== step 1: select pages

    private View buildSelect() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        top.addView(Ui.title(this, "Select pages"), Ui.weight(1));
        nextBtn = Ui.button(this, "Next", true);
        nextBtn.setMinHeight(Ui.dp(this, 40));
        nextBtn.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(this, 20)), true));
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
        nl.rightMargin = Ui.dp(this, 8);
        top.addView(nextBtn, nl);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), Ui.dp(this, 4));
        selectCount = Ui.text(this, "", 13, Ui.MUTED, false);
        bar.addView(selectCount, Ui.weight(1));
        TextView all = action("Select all"), none = action("Clear");
        bar.addView(all);
        bar.addView(none);
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        grid = new GridView(this);
        grid.setNumColumns(3);
        int sp = Ui.dp(this, 10);
        grid.setHorizontalSpacing(sp);
        grid.setVerticalSpacing(sp);
        grid.setPadding(sp, sp, sp, sp);
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        selectAdapter = new SelectAdapter();
        grid.setAdapter(selectAdapter);
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goBack(); } });
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < pages.size(); i++) if (!selected.contains(i)) selected.add(i);
                updateSelect();
            }
        });
        none.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { selected.clear(); updateSelect(); } });
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Integer k = pos;
                if (selected.contains(k)) selected.remove(k); else selected.add(k);
                updateSelect();
            }
        });
        nextBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openCustomise(); } });
        updateSelect();
        return col;
    }

    private TextView action(String s) {
        TextView t = Ui.text(this, s, 13.5f, Ui.ACCENT, true);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        t.setBackground(Ui.ripple(null, false));
        return t;
    }

    private void updateSelect() {
        int n = selected.size();
        selectCount.setText(n == 0 ? "Tap pages in the order you want them" : n + " of " + pages.size() + " selected \u00b7 numbers show the order");
        nextBtn.setEnabled(n > 0);
        nextBtn.setAlpha(n > 0 ? 1f : 0.4f);
        selectAdapter.notifyDataSetChanged();
    }

    private final class SelectAdapter extends BaseAdapter {
        @Override public int getCount() { return pages.size(); }

        @Override public Object getItem(int i) { return pages.get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            FrameLayout f;
            if (convert == null) {
                f = new FrameLayout(LayoutActivity.this);
                int p = Ui.dp(LayoutActivity.this, 6);
                f.setPadding(p, p, p, p);
                ImageView iv = new ImageView(LayoutActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                f.addView(iv, new FrameLayout.LayoutParams(-1, Ui.dp(LayoutActivity.this, 130)));
                TextView badge = Ui.text(LayoutActivity.this, "", 13, Ui.ON_ACCENT, true);
                badge.setGravity(Gravity.CENTER);
                badge.setBackground(Ui.oval(Ui.ACCENT));
                f.addView(badge, Ui.frame(Ui.dp(LayoutActivity.this, 28), Ui.dp(LayoutActivity.this, 28), Gravity.TOP | Gravity.END));
                convert = f;
            }
            f = (FrameLayout) convert;
            ImageView iv = (ImageView) f.getChildAt(0);
            TextView badge = (TextView) f.getChildAt(1);
            iv.setImageBitmap(thumbs.get(pos));
            int order = selected.indexOf(pos);
            boolean on = order >= 0;
            f.setBackground(on ? Ui.round(Ui.accentA(0x2A), Ui.dp(LayoutActivity.this, 12), Ui.ACCENT, Ui.dp(LayoutActivity.this, 2.5f))
                    : Ui.round(0xFF22384D, Ui.dp(LayoutActivity.this, 12)));
            badge.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
            badge.setText(String.valueOf(order + 1));
            iv.setAlpha(on || selected.isEmpty() ? 1f : 0.6f);
            return convert;
        }
    }

    // ================================================================== step 2: customise

    private void openCustomise() {
        if (selected.isEmpty()) return;
        sheet = 0;
        pendingSwap = -1;
        if (customStage == null) {
            customStage = buildCustomise();
            root.addView(customStage, new FrameLayout.LayoutParams(-1, -1));
        }
        selectStage.setVisibility(View.GONE);
        customStage.setVisibility(View.VISIBLE);
        Ui.reveal(customStage, 0);
        update();
    }

    private int[] gridSize() {
        if (gridChoice == 0) return Collage.autoGrid(selected.size(), canvasChoice == 1);
        if (gridChoice == GRIDS.length - 1) return new int[]{customRows, customCols};
        return GRIDS[gridChoice];
    }

    private int perSheet() { int[] g = gridSize(); return g[0] * g[1]; }

    private int sheets() { return Math.max(1, (selected.size() + perSheet() - 1) / perSheet()); }

    /** Canvas size in pixels for a long side. */
    private static int[] canvasSize(int longSide, int canvas) {
        int shortSide = Math.round(longSide / 1.41421356f);
        if (canvas == 2) return new int[]{longSide, longSide};
        return canvas == 1 ? new int[]{longSide, shortSide} : new int[]{shortSide, longSide};
    }

    private View buildCustomise() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back to page selection");
        top.addView(back);
        top.addView(Ui.title(this, "Layout"), Ui.weight(1));
        ImageView share = Ui.iconButton(this, R.drawable.ic_share, "Share layout");
        top.addView(share);
        TextView export = Ui.button(this, "Export", true);
        export.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(this, R.drawable.ic_save, Ui.ON_ACCENT), null, null, null);
        export.setCompoundDrawablePadding(Ui.dp(this, 6));
        export.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 18), 0);
        export.setMinHeight(Ui.dp(this, 40));
        export.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(this, 20)), true));
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
        el.rightMargin = Ui.dp(this, 8);
        top.addView(export, el);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        preview = new CollageView(this);
        col.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1.1f));
        sheetNav = new LinearLayout(this);
        sheetNav.setGravity(Gravity.CENTER_VERTICAL);
        sheetNav.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        TextView hint = Ui.text(this, "Tap two pages to swap them", 12.5f, Ui.MUTED, false);
        sheetNav.addView(hint, Ui.weight(1));
        final ImageView prev = Ui.iconButton(this, R.drawable.ic_prev, "Previous image");
        final ImageView next = Ui.iconButton(this, R.drawable.ic_next, "Next image");
        sheetNo = Ui.text(this, "", 13, Ui.MUTED, false);
        sheetNo.setGravity(Gravity.CENTER);
        sheetNav.addView(prev);
        sheetNav.addView(sheetNo, new LinearLayout.LayoutParams(Ui.dp(this, 56), -2));
        sheetNav.addView(next);
        col.addView(sheetNav, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.PANEL);
        LinearLayout o = new LinearLayout(this);
        o.setOrientation(LinearLayout.VERTICAL);
        o.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 20));
        sv.addView(o, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        section(o, "Grid");
        o.addView(choices(GRID_NAMES, gridChoice, new Pick() { @Override public void pick(int i) { gridChoice = i; sheet = 0; update(); } }));
        customRow = new LinearLayout(this);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setPadding(0, Ui.dp(this, 8), 0, 0);
        customRow.addView(stepper("Rows", true));
        View gap = new View(this);
        customRow.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 20), 1));
        customRow.addView(stepper("Columns", false));
        o.addView(customRow);
        section(o, "Orientation");
        o.addView(choices(CANVAS_NAMES, canvasChoice, new Pick() { @Override public void pick(int i) { canvasChoice = i; update(); } }));
        section(o, "Pages in cells");
        o.addView(choices(new String[]{"Whole page", "Fill cell (crop edges)"}, fill ? 1 : 0, new Pick() { @Override public void pick(int i) { fill = i == 1; update(); } }));
        section(o, "Spacing");
        o.addView(slider(spacing, new Pick() { @Override public void pick(int v) { spacing = v; update(); } }));
        section(o, "Margins");
        o.addView(slider(margin, new Pick() { @Override public void pick(int v) { margin = v; update(); } }));
        section(o, "Border");
        o.addView(choices(BORDER_NAMES, border, new Pick() { @Override public void pick(int i) { border = i; update(); } }));
        section(o, "Background");
        o.addView(choices(BACKGROUND_NAMES, background, new Pick() { @Override public void pick(int i) { background = i; update(); } }));
        LinearLayout lr = new LinearLayout(this);
        lr.setGravity(Gravity.CENTER_VERTICAL);
        lr.setPadding(0, Ui.dp(this, 14), 0, 0);
        lr.addView(Ui.text(this, "Page numbers", 14, Ui.LIGHT, false), Ui.weight(1));
        final Switch sw = new Switch(this);
        sw.setChecked(labels);
        lr.addView(sw);
        o.addView(lr);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean v) { labels = v; update(); }
        });
        section(o, "Page order");
        LinearLayout orderRow = new LinearLayout(this);
        TextView reverse = Ui.chip(this, "Reverse", 0), reset = Ui.chip(this, "As selected", 0);
        LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(-2, -2);
        ol.rightMargin = Ui.dp(this, 8);
        orderRow.addView(reverse, ol);
        orderRow.addView(reset);
        o.addView(orderRow);
        final List<Integer> asSelected = new ArrayList<Integer>(selected);
        reverse.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Collections.reverse(selected); pendingSwap = -1; update(); }
        });
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<Integer> keep = new ArrayList<Integer>(asSelected);
                keep.retainAll(selected);
                selected.clear();
                selected.addAll(keep);
                pendingSwap = -1;
                update();
            }
        });
        section(o, "Save as");
        o.addView(choices(new String[]{"JPEG", "PNG (lossless)"}, format == Exporter.PNG ? 1 : 0, new Pick() {
            @Override public void pick(int i) { format = i == 1 ? Exporter.PNG : Exporter.JPEG; }
        }));
        section(o, "Image size");
        o.addView(choices(SIZE_NAMES, sizeChoice, new Pick() { @Override public void pick(int i) { sizeChoice = i; } }));
        TextView note = Ui.text(this, "Creates a new image. The saved scan and its pages are not changed.", 12, Ui.MUTED, false);
        note.setPadding(0, Ui.dp(this, 14), 0, 0);
        o.addView(note);

        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goBack(); } });
        prev.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (sheet > 0) { sheet--; pendingSwap = -1; update(); } } });
        next.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (sheet < sheets() - 1) { sheet++; pendingSwap = -1; update(); } } });
        export.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { export(false); } });
        share.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { export(true); } });
        return col;
    }

    private interface Pick { void pick(int i); }

    private void section(LinearLayout o, String title) {
        TextView t = Ui.text(this, title, 13, Ui.ACCENT, true);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 6));
        o.addView(t);
    }

    private View choices(String[] labelsArr, int sel, final Pick pick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        hs.addView(row);
        final List<TextView> chips = new ArrayList<TextView>();
        for (int i = 0; i < labelsArr.length; i++) {
            final int idx = i;
            TextView t = Ui.chip(this, labelsArr[i], 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 8);
            row.addView(t, lp);
            chips.add(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int k = 0; k < chips.size(); k++) styleChip(chips.get(k), k == idx);
                    pick.pick(idx);
                }
            });
        }
        for (int k = 0; k < chips.size(); k++) styleChip(chips.get(k), k == sel);
        return hs;
    }

    private void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? Ui.ON_ACCENT : Ui.LIGHT);
        t.setBackground(Ui.ripple(on ? Ui.round(Ui.ACCENT, Ui.dp(this, 18)) : Ui.round(Ui.PANEL_HI, Ui.dp(this, 18), 0x2EE6F0F7, Ui.dp(this, 1)), true));
    }

    private View slider(int value, final Pick pick) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(value);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) { if (fromUser) pick.pick(v); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        return sb;
    }

    private View stepper(String label, final boolean rows) {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.addView(Ui.text(this, label, 13.5f, Ui.MUTED, false));
        TextView minus = Ui.chip(this, "\u2212", 0), plus = Ui.chip(this, "+", 0);
        final TextView val = Ui.text(this, String.valueOf(rows ? customRows : customCols), 15, Ui.LIGHT, true);
        val.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.leftMargin = Ui.dp(this, 8);
        l.addView(minus, ml);
        l.addView(val, new LinearLayout.LayoutParams(Ui.dp(this, 32), -2));
        l.addView(plus);
        View.OnClickListener change = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int d = v.getTag() != null ? 1 : -1;
                if (rows) customRows = Math.max(1, Math.min(6, customRows + d));
                else customCols = Math.max(1, Math.min(6, customCols + d));
                val.setText(String.valueOf(rows ? customRows : customCols));
                sheet = 0;
                update();
            }
        };
        plus.setTag("plus");
        minus.setOnClickListener(change);
        plus.setOnClickListener(change);
        return l;
    }

    private void update() {
        if (preview == null) return;
        Ui.setVisible(customRow, gridChoice == GRIDS.length - 1);
        int n = sheets();
        if (sheet >= n) sheet = n - 1;
        sheetNo.setText((sheet + 1) + " / " + n);
        for (int i = 1; i < sheetNav.getChildCount(); i++) sheetNav.getChildAt(i).setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        preview.invalidate();
    }

    /** Pages of one sheet as indices into {@code pages}; -1 for empty cells. */
    private int[] sheetPages(int s) {
        int per = perSheet();
        int[] out = new int[per];
        for (int i = 0; i < per; i++) {
            int k = s * per + i;
            out[i] = k < selected.size() ? selected.get(k) : -1;
        }
        return out;
    }

    // ================================================================== drawing (preview and export share it)

    /**
     * Draws one sheet onto a canvas of w x h: background, cells, pages (whole or filling the cell),
     * borders and page numbers. {@code images[i]} is the picture for cell i (null = still loading).
     */
    static void drawSheet(Canvas c, int w, int h, int rows, int cols, Bitmap[] images, int firstNumber, int highlight) {
        float shortSide = Math.min(w, h);
        float m = margin / 100f * 0.10f * shortSide, g = spacing / 100f * 0.08f * shortSide;
        int bg = BACKGROUNDS[background];
        boolean dark = background >= 2;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(bg);
        c.drawRect(0, 0, w, h, p);
        float[][] cells = Collage.cells(w, h, rows, cols, m, g);
        Paint img = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        Paint bd = new Paint(Paint.ANTI_ALIAS_FLAG);
        bd.setStyle(Paint.Style.STROKE);
        bd.setColor(dark ? 0xFFCCD3DA : 0xFF333A42);
        bd.setStrokeWidth(border == 2 ? 0.008f * shortSide : 0.003f * shortSide);
        Paint lbBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbBg.setColor(0xB3000000);
        Paint lbText = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbText.setColor(0xFFFFFFFF);
        lbText.setTextSize(0.028f * shortSide);
        lbText.setFakeBoldText(true);
        for (int i = 0; i < cells.length && i < images.length; i++) {
            float[] cell = cells[i];
            Bitmap b = images[i];
            RectF dst;
            if (b != null) {
                float[] pl = Collage.place(cell, b.getWidth(), b.getHeight(), fill);
                android.graphics.Rect src = new android.graphics.Rect(Math.round(pl[0]), Math.round(pl[1]), Math.round(pl[2]), Math.round(pl[3]));
                dst = new RectF(pl[4], pl[5], pl[6], pl[7]);
                c.drawBitmap(b, src, dst, img);
            } else {
                dst = null;
            }
            if (dst != null) {
                if (border > 0) c.drawRect(dst, bd);
                if (labels) {
                    String t = String.valueOf(firstNumber + i);   // the cell's place in the page order
                    float pad = 0.012f * shortSide, tw = lbText.measureText(t);
                    Paint.FontMetrics fm = lbText.getFontMetrics();
                    float bh = fm.descent - fm.ascent + pad;
                    RectF r = new RectF(dst.left + pad, dst.top + pad, dst.left + pad + tw + 2 * pad, dst.top + pad + bh);
                    c.drawRoundRect(r, bh / 2, bh / 2, lbBg);
                    c.drawText(t, r.left + pad, r.top + pad / 2 - fm.ascent, lbText);
                }
            }
            if (i == highlight) {
                Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
                hl.setStyle(Paint.Style.STROKE);
                hl.setStrokeWidth(0.012f * shortSide);
                hl.setColor(Ui.ACCENT);
                c.drawRect(cell[0], cell[1], cell[2], cell[3], hl);
            }
        }
    }

    /** Live preview of the current sheet; tap two cells to swap their pages. */
    private final class CollageView extends View {
        private final RectF area = new RectF();

        CollageView(Context c) { super(c); }

        private int[] previewCanvas() { return canvasSize(1000, canvasChoice); }

        @Override
        protected void onDraw(Canvas c) {
            int[] cs = previewCanvas();
            float pad = Ui.dp(getContext(), 14);
            float s = Math.min((getWidth() - 2 * pad) / cs[0], (getHeight() - 2 * pad) / cs[1]);
            float ox = (getWidth() - cs[0] * s) / 2f, oy = (getHeight() - cs[1] * s) / 2f;
            area.set(ox, oy, ox + cs[0] * s, oy + cs[1] * s);
            Paint sh = new Paint();
            sh.setColor(0x55000000);
            float d = Ui.dp(getContext(), 3);
            c.drawRect(area.left + d, area.top + d, area.right + d, area.bottom + d, sh);
            int[] g = gridSize();
            int[] idx = sheetPages(sheet);
            Bitmap[] imgs = new Bitmap[idx.length];
            for (int i = 0; i < idx.length; i++) imgs[i] = idx[i] >= 0 ? thumbs.get(idx[i]) : null;
            c.save();
            c.translate(ox, oy);
            c.scale(s, s);
            drawSheet(c, cs[0], cs[1], g[0], g[1], imgs, sheet * perSheet() + 1, pendingSwap);
            // loading placeholders for pages whose thumbnail isn't ready
            float shortSide = Math.min(cs[0], cs[1]);
            float[][] cells = Collage.cells(cs[0], cs[1], g[0], g[1], margin / 100f * 0.10f * shortSide, spacing / 100f * 0.08f * shortSide);
            Paint ph = new Paint();
            ph.setColor(background >= 2 ? 0xFF22384D : 0xFFD7DEE5);
            for (int i = 0; i < idx.length && i < cells.length; i++) {
                if (idx[i] >= 0 && imgs[i] == null) c.drawRect(cells[i][0], cells[i][1], cells[i][2], cells[i][3], ph);
            }
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
            if (e.getActionMasked() != MotionEvent.ACTION_UP) return true;
            int[] cs = previewCanvas();
            if (!area.contains(e.getX(), e.getY())) return true;
            float s = area.width() / cs[0];
            float x = (e.getX() - area.left) / s, y = (e.getY() - area.top) / s;
            int[] g = gridSize();
            float shortSide = Math.min(cs[0], cs[1]);
            float[][] cells = Collage.cells(cs[0], cs[1], g[0], g[1], margin / 100f * 0.10f * shortSide, spacing / 100f * 0.08f * shortSide);
            int per = perSheet();
            for (int i = 0; i < cells.length; i++) {
                float[] c = cells[i];
                if (x >= c[0] && x <= c[2] && y >= c[1] && y <= c[3]) {
                    int k = sheet * per + i;
                    if (k >= selected.size()) return true;
                    if (pendingSwap < 0) {
                        pendingSwap = i;                                   // first tap: pick a page
                    } else {
                        int a = sheet * per + pendingSwap;                 // second tap: swap the two
                        if (a != k) Collections.swap(selected, a, k);
                        pendingSwap = -1;
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                    }
                    invalidate();
                    return true;
                }
            }
            return true;
        }
    }

    // ================================================================== export

    private void export(final boolean forShare) {
        final int[] g = gridSize();
        final int per = perSheet(), n = sheets();
        final int[] cs = canvasSize(SIZES[sizeChoice], canvasChoice);
        final List<Integer> order = new ArrayList<Integer>(selected);
        final int fmt = format;
        final int quality = App.get().prefs().quality();
        final String base = entry.name + " layout";
        final AlertDialog dlg = CameraActivity.progressDialog(this, "Creating layout…");
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        app.export.execute(new Runnable() {
            @Override
            public void run() {
                final List<Uri> uris = new ArrayList<Uri>();
                String error = null;
                try {
                    float shortSide = Math.min(cs[0], cs[1]);
                    float[][] cells = Collage.cells(cs[0], cs[1], g[0], g[1], margin / 100f * 0.10f * shortSide, spacing / 100f * 0.08f * shortSide);
                    int done = 0, total = order.size();
                    for (int s = 0; s < n; s++) {
                        Bitmap out = Bitmap.createBitmap(cs[0], cs[1], Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(out);
                        Bitmap[] imgs = new Bitmap[per];
                        for (int i = 0; i < per; i++) {
                            int k = s * per + i;
                            if (k >= order.size()) break;
                            Page p = pages.get(order.get(k)).copy();
                            float cw = cells[i][2] - cells[i][0], ch = cells[i][3] - cells[i][1];
                            long need = (long) (Math.max(cw, ch) * Math.max(cw, ch) * 1.2f);
                            Bitmap b = Imaging.renderFull(p, Math.max(250_000L, need));   // full-quality page at the size it needs
                            if (b != null) {
                                Imaging.applyFilter(b, p);
                                Imaging.drawRedactions(b, p);
                            }
                            imgs[i] = b;
                            final int d = ++done, tt = total;
                            app.ui(new Runnable() {
                                @Override public void run() { CameraActivity.setProgress(dlg, "Creating layout… page " + d + " of " + tt, d, tt); }
                            });
                        }
                        drawSheet(canvas, cs[0], cs[1], g[0], g[1], imgs, s * per + 1, -1);
                        for (Bitmap b : imgs) if (b != null) b.recycle();
                        String name = n == 1 ? base : base + " " + (s + 1);
                        uris.add(forShare ? Exporter.shareImage(LayoutActivity.this, out, name, fmt, quality, s == 0)
                                : Exporter.saveImage(LayoutActivity.this, out, name, fmt, quality));
                        out.recycle();
                    }
                } catch (OutOfMemoryError e) {
                    error = "Not enough memory for this image size. Choose a smaller Image size and try again.";
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final String err = error;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        try { dlg.dismiss(); } catch (Exception ignored) { }
                        setRequestedOrientation(BaseActivity.normalOrientation(LayoutActivity.this));
                        if (isFinishing()) return;
                        if (err != null) {
                            new AlertDialog.Builder(LayoutActivity.this).setTitle("Layout problem").setMessage(err).setPositiveButton("OK", null).show();
                            return;
                        }
                        Exporter.Result r = new Exporter.Result();
                        for (Uri u : uris) { r.uris.add(u); r.mimes.add(fmt == Exporter.PNG ? "image/png" : "image/jpeg"); }
                        r.files = uris.size();
                        if (forShare) { ExportUi.share(LayoutActivity.this, r); return; }
                        saved(r);
                    }
                });
            }
        });
    }

    private void saved(final Exporter.Result r) {
        String what = r.files == 1 ? "Layout saved" : r.files + " layout images saved";
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(what)
                .setMessage("Saved to Pictures/" + Exporter.folder() + " (Gallery). The saved scan is unchanged.")
                .setPositiveButton("Done", null);
        boolean shareable = true;
        for (Uri u : r.uris) if (!"content".equals(u.getScheme())) shareable = false;
        if (shareable) {
            b.setNeutralButton("Share", new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int w) { ExportUi.share(LayoutActivity.this, r); }
            });
        }
        b.show();
    }
}
