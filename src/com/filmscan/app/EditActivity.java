package com.filmscan.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import com.filmscan.core.EdgeMap;
import com.filmscan.core.Filters;
import com.filmscan.core.Geom;
import com.filmscan.core.IntImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Edits one page at a time; swipe left/right to move between pages. Opens in crop mode.
 * Modes: crop (drag the film outline), filters, adjust, hide info. Rotate and split act directly.
 */
public final class EditActivity extends BaseActivity {
    private static final String EXTRA_ID = "page";
    public static final String RESULT_PAGE = "pageId";
    private static final int REQ_ADD = 31, REQ_ARRANGE = 32, REQ_STORAGE = 33;
    private static final int MODE_CROP = 0, MODE_FILTER = 1, MODE_ADJUST = 2, MODE_REDACT = 3, MODE_MONITOR = 4;

    private App app;
    private Session session;
    private Page page;
    private LinearLayout strip;
    private android.widget.HorizontalScrollView stripScroll;
    private ImageView arrangeBtn;
    private String stripSignature = "";
    private Runnable pendingSave;
    private int mode = -1;

    private FrameLayout stage;
    private CropView cropView;
    private ZoomImageView zoomView;
    private ProgressBar spinner;
    private TextView titleTv, hintTv, filterName, applyFilterAll, applyAdjustAll;
    private LinearLayout cropPanel, filterPanel, adjustPanel, redactPanel;
    private LinearLayout toolCrop, toolFilter, toolAdjust, toolGrid, toolHide, toolSnap;
    private final List<FrameLayout> chipFrames = new ArrayList<FrameLayout>();
    private final List<ImageView> chipImages = new ArrayList<ImageView>();
    private SeekBar sbBright, sbContrast, sbSharp;
    private TextView vBright, vContrast, vSharp;

    private Bitmap upright;       // EXIF-corrected photo shown in crop mode
    private Bitmap base;          // perspective corrected, unfiltered
    private int loadGen, thumbGen;
    private boolean baseStale, procBusy, procPending, switching, detectBusy;
    private String cropHint = "";
    // undo / redo history per page (kept while the editor is open)
    private final java.util.Map<String, java.util.ArrayDeque<Page>> undoStacks = new java.util.HashMap<String, java.util.ArrayDeque<Page>>();
    private final java.util.Map<String, java.util.ArrayDeque<Page>> redoStacks = new java.util.HashMap<String, java.util.ArrayDeque<Page>>();
    private ImageView undoBtn, redoBtn;
    private Bitmap filtered;          // current page with its filter, before anonymising
    private int redactGen;
    private static int redactType = com.filmscan.core.Redact.BOX;
    private final List<LinearLayout> typeChips = new ArrayList<LinearLayout>();
    // Monitor Mode: its own processing layer, independent of the Filters tool
    private LinearLayout monitorPanel;
    private TextView monitorPill, monitorValue;
    private SeekBar sbMonitor;
    private final List<TextView> monitorChips = new ArrayList<TextView>();
    private PageSwiper swiper;
    private float[] beforeFull;

    public static Intent intent(Context c, String id) {
        return new Intent(c, EditActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        session = app.session();
        page = session.find(getIntent().getStringExtra(EXTRA_ID));
        if (page == null) { finish(); return; }
        int startMode = MODE_CROP;
        if (b != null) {
            // coming back after the screen turned: same page, same tool
            Page kept = session.find(b.getString("page"));
            if (kept != null) page = kept;
            startMode = b.getInt("mode", MODE_CROP);
        }
        buildUi();
        setMode(startMode);
        bindPage();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (page != null) out.putString("page", page.id);
        out.putInt("mode", mode < 0 ? MODE_CROP : mode);
    }

    @Override
    protected void onPause() {
        if (mode == MODE_CROP) commitCrop(false);
        app.main.removeCallbacks(refreshStrip);
        super.onPause();
    }

    // ================================================================== UI

    private void buildUi() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);

        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        titleTv = Ui.title(this, "");
        top.addView(titleTv, Ui.weight(1));
        arrangeBtn = Ui.iconButton(this, R.drawable.ic_rearrange, "Rearrange pages");
        ImageView del = Ui.iconButton(this, R.drawable.ic_delete, "Delete page");
        top.addView(arrangeBtn);
        top.addView(del);
        TextView done = Ui.button(this, "Save", true);
        done.setCompoundDrawablesRelativeWithIntrinsicBounds(Ui.icon(this, R.drawable.ic_save, Ui.ON_ACCENT), null, null, null);
        done.setCompoundDrawablePadding(Ui.dp(this, 6));
        done.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 18), 0);
        done.setMinHeight(Ui.dp(this, 40));
        done.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(this, 20)), true));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
        dlp.leftMargin = Ui.dp(this, 4);
        dlp.rightMargin = Ui.dp(this, 8);
        top.addView(done, dlp);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout hintRow = new LinearLayout(this);
        hintRow.setGravity(Gravity.CENTER_VERTICAL);
        hintRow.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 6), 0);
        hintTv = Ui.text(this, "", 13, Ui.MUTED, false);
        hintTv.setMaxLines(2);
        monitorPill = Ui.chip(this, "Monitor", 0);
        android.graphics.drawable.Drawable md = Ui.icon(this, R.drawable.ic_monitor, Ui.LIGHT);
        int mds = Ui.dp(this, 18);
        md.setBounds(0, 0, mds, mds);
        monitorPill.setCompoundDrawablesRelative(md, null, null, null);
        monitorPill.setCompoundDrawablePadding(Ui.dp(this, 6));
        monitorPill.setContentDescription("Monitor Mode: reduce moiré in photos of a screen");
        LinearLayout.LayoutParams mpl = new LinearLayout.LayoutParams(-2, -2);
        mpl.rightMargin = Ui.dp(this, 10);
        hintRow.addView(monitorPill, mpl);
        monitorPill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(mode == MODE_MONITOR ? MODE_FILTER : MODE_MONITOR); }
        });
        hintRow.addView(hintTv, Ui.weight(1));
        undoBtn = Ui.iconButton(this, R.drawable.ic_undo, "Undo");
        redoBtn = Ui.iconButton(this, R.drawable.ic_redo, "Redo");
        hintRow.addView(undoBtn, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 40)));
        hintRow.addView(redoBtn, new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 40)));
        col.addView(hintRow, new LinearLayout.LayoutParams(-1, -2));
        undoBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { undo(); } });
        redoBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { redo(); } });

        stage = new FrameLayout(this);
        cropView = new CropView(this);
        cropView.setSnapEnabled(app.prefs().snap());
        stage.addView(cropView, new FrameLayout.LayoutParams(-1, -1));
        zoomView = new ZoomImageView(this);
        stage.addView(zoomView, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this);
        stage.addView(spinner, Ui.frame(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.CENTER));
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        // thumbnails of every page: tap to jump, "+" to add more
        stripScroll = new android.widget.HorizontalScrollView(this);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        strip = new LinearLayout(this);
        strip.setGravity(Gravity.CENTER_VERTICAL);
        strip.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        stripScroll.addView(strip);
        col.addView(stripScroll, new LinearLayout.LayoutParams(-1, Ui.dp(this, 84)));

        FrameLayout panelHost = new FrameLayout(this);
        panelHost.setBackgroundColor(Ui.PANEL);
        cropPanel = buildCropPanel();
        filterPanel = buildFilterPanel();
        adjustPanel = buildAdjustPanel();
        redactPanel = buildRedactPanel();
        panelHost.addView(cropPanel);
        panelHost.addView(filterPanel);
        panelHost.addView(adjustPanel);
        panelHost.addView(redactPanel);
        monitorPanel = buildMonitorPanel();
        panelHost.addView(monitorPanel);
        col.addView(panelHost, new LinearLayout.LayoutParams(-1, -2));

        View div = new View(this);
        div.setBackgroundColor(0x22E6F0F7);
        col.addView(div, new LinearLayout.LayoutParams(-1, 1));
        LinearLayout tools = new LinearLayout(this);
        tools.setBackgroundColor(Ui.PANEL);
        toolCrop = Ui.tool(this, R.drawable.ic_crop, "Crop");
        toolFilter = Ui.tool(this, R.drawable.ic_filter, "Filters");
        toolAdjust = Ui.tool(this, R.drawable.ic_tune, "Adjust");
        LinearLayout tRot = Ui.tool(this, R.drawable.ic_rotate, "Rotate");
        toolGrid = Ui.tool(this, R.drawable.ic_split, "Split");
        toolHide = Ui.tool(this, R.drawable.ic_hide, "Anonymise");
        for (LinearLayout t : new LinearLayout[]{toolCrop, toolFilter, toolAdjust, tRot, toolGrid, toolHide}) tools.addView(t, Ui.weight(1));
        col.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        setContentView(col);

        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { goBack(); } });
        setBackHandler(new Runnable() { @Override public void run() { goBack(); } });
        done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { save(); } });
        arrangeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == MODE_CROP && !commitCrop(true)) return;
                startActivityForResult(new Intent(EditActivity.this, RearrangeActivity.class), REQ_ARRANGE);
            }
        });
        del.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmDelete(); } });
        toolCrop.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(MODE_CROP); } });
        toolFilter.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(MODE_FILTER); } });
        toolAdjust.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(MODE_ADJUST); } });
        toolHide.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(MODE_REDACT); } });
        tRot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { rotate(); } });
        toolGrid.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showGridDialog(); } });

        swiper = new PageSwiper(stage, new PageSwiper.Host() {
            @Override
            public boolean canGo(int dir) {
                int i = session.indexOf(page) + dir;
                return i >= 0 && i < session.pages.size();
            }

            @Override
            public boolean beforeGo(int dir) { return mode != MODE_CROP || commitCrop(true); }

            @Override
            public void go(int dir) {
                int i = session.indexOf(page) + dir;
                if (i < 0 || i >= session.pages.size()) return;
                page = session.pages.get(i);
                bindPage();
            }
        });
        cropView.setSwipeListener(swiper.gesture);
        cropView.setListener(new CropView.Listener() {
            @Override public void onQuadChanged(boolean valid) { }
            @Override public void onEditStart() { snapshot(); }
        });
        zoomView.setSwipeListener(swiper.gesture);
    }

    private LinearLayout buildCropPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        LinearLayout detect = Ui.tool(this, R.drawable.ic_auto, "Detect edges");
        LinearLayout full = Ui.tool(this, R.drawable.ic_full, "Whole photo");
        toolSnap = Ui.tool(this, R.drawable.ic_magnet, "Edge snap");
        LinearLayout resetCrop = Ui.tool(this, R.drawable.ic_restore, "Reset");
        p.addView(detect, Ui.weight(1));
        p.addView(full, Ui.weight(1));
        p.addView(toolSnap, Ui.weight(1));
        p.addView(resetCrop, Ui.weight(1));
        resetCrop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // back to the automatic crop and no rotation
                snapshot();
                if (page.rot != 0) rotateBy((4 - page.rot) & 3);
                redetect(false);
            }
        });
        Ui.setToolActive(toolSnap, app.prefs().snap());
        detect.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { redetect(true); } });
        full.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!cropView.hasImage()) return;
                snapshot();
                if (cropView.isFull() && beforeFull != null) cropView.setQuadNormalized(beforeFull, true);
                else { beforeFull = cropView.getQuadNormalized(); cropView.setQuadNormalized(Geom.fullQuad(1, 1), true); }
            }
        });
        toolSnap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean s = !app.prefs().snap();
                app.prefs().snap(s);
                cropView.setSnapEnabled(s);
                Ui.setToolActive(toolSnap, s);
                Toast.makeText(EditActivity.this, s ? "Edge snap on" : "Edge snap off", Toast.LENGTH_SHORT).show();
            }
        });
        return p;
    }

    private TextView applyAllButton() {
        TextView t = Ui.text(this, "Apply to all", 13.5f, Ui.ACCENT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        t.setBackground(Ui.ripple(Ui.round(0, Ui.dp(this, 16), Ui.ACCENT, Ui.dp(this, 1.5f)), true));
        t.setClickable(true);
        return t;
    }

    private LinearLayout buildFilterPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 2));
        filterName = Ui.text(this, "", 14, Ui.LIGHT, true);
        head.addView(filterName, Ui.weight(1));
        TextView resetFilter = textAction("Reset", Ui.LIGHT);
        head.addView(resetFilter);
        resetFilter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setFilter(Filters.ORIGINAL); }
        });
        applyFilterAll = applyAllButton();
        head.addView(applyFilterAll);
        p.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 10));
        for (int i = 0; i < Filters.COUNT; i++) {
            final int idx = i;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            chip.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            FrameLayout fr = new FrameLayout(this);
            int pad = Ui.dp(this, 3);
            fr.setPadding(pad, pad, pad, pad);
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setBackground(Ui.round(Ui.PANEL_HI, Ui.dp(this, 8)));
            iv.setClipToOutline(true);
            fr.addView(iv, new FrameLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62)));
            chip.addView(fr);
            TextView t = Ui.text(this, Filters.NAMES[i], 11.5f, Ui.MUTED, false);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(Ui.dp(this, 74), -2);
            tl.topMargin = Ui.dp(this, 3);
            chip.addView(t, tl);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setFilter(idx); }
            });
            row.addView(chip);
            chipFrames.add(fr);
            chipImages.add(iv);
        }
        hs.addView(row);
        p.addView(hs);
        applyFilterAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { applyFilterToAll(); }
        });
        return p;
    }

    private LinearLayout buildAdjustPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        vBright = new TextView(this); vContrast = new TextView(this); vSharp = new TextView(this);
        sbBright = slider(p, "Brightness", vBright, 200);
        sbContrast = slider(p, "Contrast", vContrast, 200);
        sbSharp = slider(p, "Sharpness", vSharp, 100);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        TextView reset = Ui.text(this, "Reset", 13.5f, Ui.LIGHT, true);
        reset.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
        reset.setBackground(Ui.ripple(null, false));
        applyAdjustAll = applyAllButton();
        row.addView(reset);
        row.addView(applyAdjustAll);
        p.addView(row);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                snapshot();
                page.bright = 0; page.contrast = 0; page.sharp = 0;
                edited();
                syncSliders();
                reprocess();
            }
        });
        applyAdjustAll.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { applyAdjustToAll(); } });
        return p;
    }

    private SeekBar slider(LinearLayout parent, String label, TextView value, int max) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, label, 13.5f, Ui.MUTED, false), new LinearLayout.LayoutParams(Ui.dp(this, 84), -2));
        final SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        row.addView(sb, Ui.weight(1));
        value.setTextColor(Ui.LIGHT);
        value.setTextSize(13.5f);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(Ui.dp(this, 40), -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(this, 44)));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                readSliders();
                reprocess();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { snapshot(); }
            @Override public void onStopTrackingTouch(SeekBar s) { edited(); }
        });
        return sb;
    }

    private static final int[] MONITOR_LEVELS = {0, com.filmscan.core.Moire.LOW, com.filmscan.core.Moire.MEDIUM, com.filmscan.core.Moire.HIGH};
    private static final String[] MONITOR_NAMES = {"Off", "Low", "Medium", "High"};

    private LinearLayout buildMonitorPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(this, "Monitor Mode", 14, Ui.LIGHT, true), Ui.weight(1));
        TextView reset = textAction("Reset", Ui.LIGHT);
        head.addView(reset);
        TextView all = applyAllButton();
        head.addView(all);
        p.addView(head);
        TextView d = Ui.text(this, "Reduces rainbow ripples (moiré) in photos of a screen. Filters and all other edits still apply on top.", 12.5f, Ui.MUTED, false);
        d.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 8));
        p.addView(d);
        LinearLayout levels = new LinearLayout(this);
        for (int i = 0; i < MONITOR_LEVELS.length; i++) {
            final int level = MONITOR_LEVELS[i];
            TextView c = Ui.chip(this, MONITOR_NAMES[i], 0);
            c.setGravity(Gravity.CENTER);
            c.setTag(level);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 40), 1);
            if (i > 0) lp.leftMargin = Ui.dp(this, 8);
            levels.addView(c, lp);
            monitorChips.add(c);
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { setMonitor(level); }
            });
        }
        p.addView(levels, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(Ui.text(this, "Intensity", 13.5f, Ui.MUTED, false), new LinearLayout.LayoutParams(Ui.dp(this, 72), -2));
        sbMonitor = new SeekBar(this);
        sbMonitor.setMax(100);
        row.addView(sbMonitor, Ui.weight(1));
        monitorValue = Ui.text(this, "", 13.5f, Ui.LIGHT, false);
        monitorValue.setGravity(Gravity.END);
        row.addView(monitorValue, new LinearLayout.LayoutParams(Ui.dp(this, 64), -2));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, Ui.dp(this, 44));
        rl.topMargin = Ui.dp(this, 6);
        p.addView(row, rl);
        sbMonitor.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (!fromUser) return;
                page.moire = v;
                updateMonitorUi();
                reprocess();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { snapshot(); }
            @Override public void onStopTrackingTouch(SeekBar s) { edited(); }
        });
        reset.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMonitor(0); } });
        all.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (Page q : session.pages) {
                    if (q == page || q.moire == page.moire) continue;
                    stack(undoStacks, q.id).push(q.copy());
                    stack(redoStacks, q.id).clear();
                    q.moire = page.moire;
                    q.version++;
                }
                session.save();
                stripSoon();
                Toast.makeText(EditActivity.this, "Monitor Mode " + com.filmscan.core.Moire.label(page.moire)
                        + " applied to all " + session.pages.size() + " pages", Toast.LENGTH_SHORT).show();
            }
        });
        return p;
    }

    private void setMonitor(int level) {
        if (page.moire == level) return;
        snapshot();
        page.moire = level;
        edited();
        updateMonitorUi();
        reprocess();
    }

    /** Pill above the photo, preset chips and slider all reflect the page's Monitor Mode level. */
    private void updateMonitorUi() {
        if (monitorPill == null || page == null) return;
        boolean on = page.moire > 0, open = mode == MODE_MONITOR;
        monitorPill.setText(on ? "Monitor \u00b7 " + com.filmscan.core.Moire.label(page.moire).replace("Custom ", "") : "Monitor");
        monitorPill.setTextColor(on || open ? Ui.ACCENT : Ui.LIGHT);
        monitorPill.getCompoundDrawablesRelative()[0].setTint(on || open ? Ui.ACCENT : Ui.LIGHT);
        monitorPill.setBackground(Ui.ripple(on || open ? Ui.round(Ui.accentA(0x26), Ui.dp(this, 18), Ui.ACCENT, Ui.dp(this, 1.5f))
                : Ui.round(Ui.PANEL_HI, Ui.dp(this, 18), 0x2EE6F0F7, Ui.dp(this, 1)), true));
        for (TextView c : monitorChips) {
            boolean sel = (Integer) c.getTag() == page.moire;
            c.setTextColor(sel ? Ui.ON_ACCENT : Ui.LIGHT);
            c.setBackground(Ui.ripple(sel ? Ui.round(Ui.ACCENT, Ui.dp(this, 18)) : Ui.round(Ui.PANEL_HI, Ui.dp(this, 18)), true));
        }
        if (sbMonitor != null && sbMonitor.getProgress() != page.moire) sbMonitor.setProgress(page.moire);
        if (monitorValue != null) monitorValue.setText(page.moire == 0 ? "Off" : page.moire + "%");
    }

    private LinearLayout buildRedactPanel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 6));
        p.addView(Ui.text(this, "Drag over names, IDs or dates. Black box is the safest choice for text.", 13, Ui.MUTED, false));
        LinearLayout types = new LinearLayout(this);
        types.setPadding(0, Ui.dp(this, 8), 0, 0);
        int[] icons = {R.drawable.ic_box, R.drawable.ic_blur, R.drawable.ic_pixelate};
        String[] names = {"Black box", "Blur", "Pixelate"};
        int[] kinds = {com.filmscan.core.Redact.BOX, com.filmscan.core.Redact.BLUR, com.filmscan.core.Redact.PIXELATE};
        for (int i = 0; i < 3; i++) {
            final int kind = kinds[i];
            LinearLayout t = Ui.tool(this, icons[i], names[i]);
            t.setTag(kind);
            types.addView(t, Ui.weight(1));
            typeChips.add(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { redactType = kind; styleTypes(); }
            });
        }
        p.addView(types, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        TextView clear = textAction("Remove all", Ui.LIGHT);
        TextView done = textAction("Finish", Ui.ACCENT);
        row.addView(clear);
        row.addView(done);
        p.addView(row);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (page.redact.isEmpty()) return;
                snapshot();
                page.redact.clear();
                edited();
                zoomView.invalidate();
                displayPreview();
            }
        });
        done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setMode(MODE_FILTER); } });
        styleTypes();
        return p;
    }

    private void styleTypes() {
        for (LinearLayout t : typeChips) {
            boolean on = (Integer) t.getTag() == redactType;
            Ui.setToolActive(t, on);
            t.setBackground(on ? Ui.round(Ui.accentA(0x22), Ui.dp(this, 12)) : Ui.ripple(null, false));
        }
    }

    private TextView textAction(String s, int color) {
        TextView t = Ui.text(this, s, 13.5f, color, true);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        t.setBackground(Ui.ripple(null, false));
        return t;
    }

    // ================================================================== modes

    private void setMode(int m) {
        if (mode == MODE_CROP && m != MODE_CROP && !commitCrop(true)) return;
        int old = mode;
        mode = m;
        boolean crop = m == MODE_CROP;
        if (old < 0) {
            cropView.setVisibility(crop ? View.VISIBLE : View.INVISIBLE);
            zoomView.setVisibility(crop ? View.INVISIBLE : View.VISIBLE);
        } else if ((old == MODE_CROP) != crop) {
            crossfade(crop ? cropView : zoomView, crop ? zoomView : cropView);
        }
        LinearLayout panel = crop ? cropPanel : m == MODE_FILTER ? filterPanel : m == MODE_ADJUST ? adjustPanel
                : m == MODE_MONITOR ? monitorPanel : redactPanel;
        Ui.setVisible(cropPanel, crop);
        Ui.setVisible(filterPanel, m == MODE_FILTER);
        Ui.setVisible(adjustPanel, m == MODE_ADJUST);
        Ui.setVisible(redactPanel, m == MODE_REDACT);
        Ui.setVisible(monitorPanel, m == MODE_MONITOR);
        updateMonitorUi();
        if (old >= 0 && old != m) Ui.reveal(panel, 0);
        Ui.setToolActive(toolCrop, crop);
        Ui.setToolActive(toolFilter, m == MODE_FILTER);
        Ui.setToolActive(toolAdjust, m == MODE_ADJUST);
        Ui.setToolActive(toolHide, m == MODE_REDACT);
        zoomView.setRedactMode(m == MODE_REDACT, m == MODE_REDACT ? new ZoomImageView.RedactListener() {
            @Override
            public void onBoxDrawn(float[] box) {
                snapshot();
                page.redact.add(new float[]{box[0], box[1], box[2], box[3], redactType});
                edited();
                displayPreview();
            }
        } : null);
        updateHint();
        if (!crop && baseStale) rebuildBase();
    }

    /** Smoothly swaps the crop view and the finished-page view. */
    private void crossfade(final View show, final View hide) {
        show.animate().cancel();
        hide.animate().cancel();
        show.setVisibility(View.VISIBLE);
        show.setAlpha(0f);
        show.animate().alpha(1f).setStartDelay(0).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        hide.animate().alpha(0f).setStartDelay(0).setDuration(160)
                .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        boolean shouldHide = (hide == cropView) == (mode != MODE_CROP);
                        if (shouldHide) hide.setVisibility(View.INVISIBLE);
                        hide.setAlpha(1f);
                    }
                }).start();
    }

    private void updateHint() {
        boolean many = session.pages.size() > 1;
        String s;
        if (mode == MODE_CROP) s = cropHint + (many ? " Swipe to change page." : "");
        else if (mode == MODE_REDACT) s = "";
        else if (mode == MODE_MONITOR) s = "Zoom in to check fine detail.";
        else s = many ? "Pinch to zoom. Swipe to change page." : "Pinch or double-tap to zoom.";
        hintTv.setText(s);
    }

    /** Stores the crop handles into the page. Returns false if the shape is invalid and the user was told. */
    private boolean commitCrop(boolean showError) {
        if (!cropView.hasImage()) return true;
        if (!cropView.isValid()) {
            if (showError) Toast.makeText(this, "The corners cross each other. Drag them into a four-sided shape.", Toast.LENGTH_LONG).show();
            return !showError;
        }
        float[] q = cropView.getQuadNormalized();
        boolean changed = page.quad == null;
        if (!changed) for (int i = 0; i < 8; i++) if (Math.abs(q[i] - page.quad[i]) > 1e-4f) { changed = true; break; }
        if (changed) {
            page.quad = q;
            page.version++;
            session.save();
            baseStale = true;
            stripSoon();
        }
        return true;
    }

    // ================================================================== page binding

    private void bindPage() {
        filtered = null;
        redactGen++;
        int idx = session.indexOf(page), n = session.pages.size();
        SavedStore.Entry editing = app.editingEntry();
        titleTv.setText(editing != null ? editing.name : n > 1 ? "Page " + (idx + 1) + " of " + n : "Scan");
        titleTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        Ui.setVisible(arrangeBtn, n > 1);
        rebuildStrip(false);
        selectThumb();
        Ui.setVisible(applyFilterAll, n > 1);
        Ui.setVisible(applyAdjustAll, n > 1);
        syncSliders();
        syncFilterUi();
        updateMonitorUi();
        zoomView.setGrid(page.gridR, page.gridC);
        zoomView.setBoxes(page.redact);
        Ui.setToolActive(toolGrid, page.hasGrid());
        upright = null;
        base = null;
        beforeFull = null;
        cropView.clear();
        zoomView.setBitmap(null, false);
        for (ImageView iv : chipImages) iv.setImageDrawable(null);
        cropHint = "";
        updateHint();
        updateUndoUi();
        load();
    }

    private void load() {
        final int g = ++loadGen;
        spinner.setVisibility(View.VISIBLE);
        final Page snap = page.copy();
        app.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap up = Imaging.oriented(snap, Imaging.PREVIEW_SIDE);
                if (up == null) {
                    app.ui(new Runnable() {
                        @Override
                        public void run() {
                            if (g != loadGen) return;
                            spinner.setVisibility(View.GONE);
                            Toast.makeText(EditActivity.this, "This image could not be opened", Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }
                float[] q = snap.quad;
                boolean found = snap.detected, fresh = false;
                if (q == null) {
                    try { q = Imaging.detect(up); } catch (Throwable t) { q = null; }
                    found = q != null;
                    fresh = true;
                }
                EdgeMap e = null;
                try { e = Imaging.edgeMap(up, 1024); } catch (Throwable t) { e = null; }
                Page forBase = snap.copy();
                forBase.quad = q;
                final Bitmap b = Imaging.renderPreview(forBase, up, Imaging.EDIT_SIDE);
                final float[] fq = q;
                final boolean ffound = found, ffresh = fresh;
                final EdgeMap fe = e;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (g != loadGen || isFinishing()) return;
                        upright = up;
                        if (ffresh && page.quad == null && fq != null) { page.quad = fq; page.detected = ffound; session.save(); }
                        cropView.setEdgeMap(fe);
                        cropView.setImage(up, page.quad, page.rot);
                        cropHint = ffound ? "Drag a corner or edge to adjust the crop." : "No clear edge found. Drag the corners onto the film.";
                        updateHint();
                        base = b;
                        baseStale = false;
                        if (mode == MODE_CROP) spinner.setVisibility(View.GONE);
                        reprocess();
                        buildThumbs(b);
                    }
                });
            }
        });
    }

    private void step(int d) {
        if (session.pages.size() < 2) return;
        swiper.step(d);
    }

    private void edited() {
        page.version++;
        session.save();
        stripSoon();
    }

    private void stripSoon() {
        app.main.removeCallbacks(refreshStrip);
        app.main.postDelayed(refreshStrip, 700);
    }

    private void syncSliders() {
        sbBright.setProgress(page.bright + 100);
        sbContrast.setProgress(page.contrast + 100);
        sbSharp.setProgress(page.sharp);
        vBright.setText(String.valueOf(page.bright));
        vContrast.setText(String.valueOf(page.contrast));
        vSharp.setText(String.valueOf(page.sharp));
    }

    private void readSliders() {
        page.bright = sbBright.getProgress() - 100;
        page.contrast = sbContrast.getProgress() - 100;
        page.sharp = sbSharp.getProgress();
        vBright.setText(String.valueOf(page.bright));
        vContrast.setText(String.valueOf(page.contrast));
        vSharp.setText(String.valueOf(page.sharp));
    }

    private void syncFilterUi() {
        filterName.setText(Filters.NAMES[page.filter]);
        for (int i = 0; i < chipFrames.size(); i++) {
            chipFrames.get(i).setBackground(i == page.filter ? Ui.round(0, Ui.dp(this, 11), Ui.ACCENT, Ui.dp(this, 2.5f)) : null);
        }
    }

    private void setFilter(int f) {
        if (f == page.filter) return;
        snapshot();
        page.filter = f;
        edited();
        syncFilterUi();
        updateMonitorUi();
        View chip = chipFrames.get(f);
        chip.setScaleX(0.88f);
        chip.setScaleY(0.88f);
        chip.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(300).setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
        reprocess();
    }

    private void applyFilterToAll() {
        for (Page p : session.pages) {
            if (p != page) { stack(undoStacks, p.id).push(p.copy()); stack(redoStacks, p.id).clear(); }
        }
        for (Page p : session.pages) {
            if (p != page && p.filter != page.filter) { p.filter = page.filter; p.version++; }
        }
        session.save();
        stripSoon();
        Toast.makeText(this, "\u201c" + Filters.NAMES[page.filter] + "\u201d applied to all " + session.pages.size() + " pages", Toast.LENGTH_SHORT).show();
    }

    private void applyAdjustToAll() {
        for (Page p : session.pages) {
            if (p != page) { stack(undoStacks, p.id).push(p.copy()); stack(redoStacks, p.id).clear(); }
        }
        for (Page p : session.pages) {
            if (p == page) continue;
            p.bright = page.bright; p.contrast = page.contrast; p.sharp = page.sharp;
            p.version++;
        }
        session.save();
        stripSoon();
        Toast.makeText(this, "Adjustments applied to all " + session.pages.size() + " pages", Toast.LENGTH_SHORT).show();
    }

    // ================================================================== rendering

    private void rebuildBase() {
        if (upright == null) return;
        final int g = loadGen;
        final Page snap = page.copy();
        final Bitmap up = upright;
        spinner.setVisibility(View.VISIBLE);
        app.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = Imaging.renderPreview(snap, up, Imaging.EDIT_SIDE);
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (g != loadGen || isFinishing()) return;
                        base = b;
                        baseStale = false;
                        reprocess();
                        buildThumbs(b);
                    }
                });
            }
        });
    }

    private void buildThumbs(final Bitmap src) {
        final int g = ++thumbGen;
        float s = 150f / Math.max(src.getWidth(), src.getHeight());
        final Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(src.getWidth() * s)), Math.max(1, Math.round(src.getHeight() * s)), true);
        app.run(new Runnable() {
            @Override
            public void run() {
                int w = small.getWidth(), h = small.getHeight();
                int[] orig = new int[w * h];
                small.getPixels(orig, 0, w, 0, 0, w, h);
                for (int i = 0; i < Filters.COUNT; i++) {
                    if (g != thumbGen) return;
                    int[] px = orig.clone();
                    Filters.apply(new IntImage(px, w, h), i, 0, 0, 0);
                    final Bitmap t = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
                    final int idx = i;
                    app.ui(new Runnable() {
                        @Override public void run() { if (g == thumbGen) chipImages.get(idx).setImageBitmap(t); }
                    });
                }
            }
        });
    }

    private void reprocess() {
        if (base == null) return;
        if (procBusy) { procPending = true; return; }
        procBusy = true;
        final Bitmap src = base;
        final Page snap = page.copy();
        final int g = loadGen;
        app.run(new Runnable() {
            @Override
            public void run() {
                Bitmap b;
                try {
                    b = src.copy(Bitmap.Config.ARGB_8888, true);
                    Imaging.applyFilter(b, snap);
                } catch (Throwable t) {
                    b = null;
                }
                final Bitmap out = b;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        procBusy = false;
                        if (out != null && g == loadGen && !isFinishing()) {
                            filtered = out;
                            displayPreview();
                        }
                        if (procPending) { procPending = false; reprocess(); }
                    }
                });
            }
        });
    }

    /** Shows the filtered page with its anonymised areas applied (done on a copy, off the UI thread). */
    private void displayPreview() {
        final Bitmap f = filtered;
        if (f == null) return;
        if (page.redact.isEmpty()) { setPreviewBitmap(f); return; }
        final int g = ++redactGen, lg = loadGen;
        final Page snap = page.copy();
        app.run(new Runnable() {
            @Override
            public void run() {
                Bitmap b;
                try {
                    b = f.copy(Bitmap.Config.ARGB_8888, true);
                    Imaging.drawRedactions(b, snap);
                } catch (Throwable t) {
                    b = null;
                }
                final Bitmap out = b;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (out != null && g == redactGen && lg == loadGen && !isFinishing()) setPreviewBitmap(out);
                    }
                });
            }
        });
    }

    private void setPreviewBitmap(Bitmap b) {
        spinner.setVisibility(View.GONE);
        boolean firstImage = zoomView.getBitmap() == null;
        zoomView.setBitmap(b, true);
        if (firstImage && mode != MODE_CROP) {
            zoomView.setAlpha(0f);
            zoomView.animate().alpha(1f).setStartDelay(0).setDuration(200).start();
        }
    }

    // ================================================================== undo / redo

    private java.util.ArrayDeque<Page> stack(java.util.Map<String, java.util.ArrayDeque<Page>> m, String id) {
        java.util.ArrayDeque<Page> d = m.get(id);
        if (d == null) { d = new java.util.ArrayDeque<Page>(); m.put(id, d); }
        return d;
    }

    /** The page as it is right now, including crop handles that are not yet committed. */
    private Page current() {
        Page c = page.copy();
        if (mode == MODE_CROP && cropView.hasImage() && cropView.isValid()) c.quad = cropView.getQuadNormalized();
        return c;
    }

    /** Records the current state before a change. */
    private void snapshot() {
        if (page == null) return;
        Page c = current();
        java.util.ArrayDeque<Page> u = stack(undoStacks, page.id);
        if (!u.isEmpty() && EditHistory.sameEdits(u.peek(), c)) return;
        u.push(c);
        while (u.size() > 60) u.removeLast();
        stack(redoStacks, page.id).clear();
        updateUndoUi();
    }

    private void undo() { step(undoStacks, redoStacks); }

    private void redo() { step(redoStacks, undoStacks); }

    private void step(java.util.Map<String, java.util.ArrayDeque<Page>> from, java.util.Map<String, java.util.ArrayDeque<Page>> to) {
        java.util.ArrayDeque<Page> src = stack(from, page.id);
        Page now = current();
        while (!src.isEmpty() && EditHistory.sameEdits(src.peek(), now)) src.pop();   // skip no-op entries
        if (src.isEmpty()) { updateUndoUi(); return; }
        stack(to, page.id).push(now);
        restore(src.pop());
    }

    /** Puts a recorded state back on screen. */
    private void restore(Page s) {
        boolean geometry = page.rot != s.rot || !EditHistory.sameQuad(current().quad, s.quad);
        EditHistory.apply(s, page);
        page.version++;
        session.save();
        stripSoon();
        syncSliders();
        syncFilterUi();
        updateMonitorUi();
        zoomView.setGrid(page.gridR, page.gridC);
        zoomView.setBoxes(page.redact);
        Ui.setToolActive(toolGrid, page.hasGrid());
        if (geometry) {
            cropView.setRotationSteps(page.rot);
            cropView.setQuadNormalized(page.quad, true);
            baseStale = true;
            if (mode != MODE_CROP) rebuildBase();
        } else {
            reprocess();
        }
        updateUndoUi();
    }

    private void updateUndoUi() {
        if (undoBtn == null || page == null) return;
        boolean u = !stack(undoStacks, page.id).isEmpty(), r = !stack(redoStacks, page.id).isEmpty();
        undoBtn.setEnabled(u);
        redoBtn.setEnabled(r);
        undoBtn.animate().alpha(u ? 1f : 0.3f).setStartDelay(0).setDuration(150).start();
        redoBtn.animate().alpha(r ? 1f : 0.3f).setStartDelay(0).setDuration(150).start();
    }

    /** Clears every edit on the current page, back to the original photo with automatic edges. */
    private void resetPage() {
        if (mode == MODE_CROP) commitCrop(false);
        snapshot();
        EditHistory.clearEdits(page);
        page.version++;
        session.save();
        stripSoon();
        bindPage();
        Toast.makeText(this, "Page reset to the original photo. Undo brings your edits back.", Toast.LENGTH_SHORT).show();
    }

    // ================================================================== actions

    private void redetect(final boolean record) {
        if (detectBusy || upright == null) return;
        detectBusy = true;
        spinner.setVisibility(View.VISIBLE);
        final Bitmap b = upright;
        final int g = loadGen;
        app.run(new Runnable() {
            @Override
            public void run() {
                float[] q;
                try { q = Imaging.detect(b); } catch (Throwable t) { q = null; }
                final float[] fq = q;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        detectBusy = false;
                        spinner.setVisibility(View.GONE);
                        if (g != loadGen) return;
                        if (fq != null) {
                            if (record) snapshot();
                            cropView.setQuadNormalized(fq, true);
                            cropHint = "Edges found. Drag a corner or edge to adjust.";
                        } else {
                            cropHint = "No clear edge found. Try a plainer background, or drag the corners.";
                        }
                        updateHint();
                    }
                });
            }
        });
    }

    private void rotate() {
        snapshot();
        rotateBy(1);
    }

    /** Turns the page clockwise by quarter turns, keeping anonymised areas and the grid on the same content. */
    private void rotateBy(int steps) {
        for (int k = 0; k < (steps & 3); k++) {
            page.rot = (page.rot + 1) & 3;
            // (x,y) -> (1-y, x)
            for (float[] r : page.redact) {
                float l = r[0], t = r[1], rr = r[2], b = r[3];
                r[0] = 1 - b; r[1] = l; r[2] = 1 - t; r[3] = rr;
            }
            int gr = page.gridR;
            page.gridR = page.gridC;
            page.gridC = gr;
        }
        zoomView.setGrid(page.gridR, page.gridC);
        edited();
        cropView.setRotationSteps(page.rot);
        baseStale = true;
        if (mode != MODE_CROP) rebuildBase();
    }

    private void showGridDialog() {
        if (mode == MODE_CROP && !commitCrop(true)) return;
        if (mode == MODE_CROP) setMode(MODE_FILTER); // the grid is drawn on the corrected page
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 22);
        l.setPadding(pad, Ui.dp(this, 8), pad, 0);
        l.addView(Ui.text(this, "CT and MRI films often hold many slices. Set the rows and columns and each panel is saved as its own image when you export.", 14, Ui.MUTED, false));
        LinearLayout pickers = new LinearLayout(this);
        pickers.setGravity(Gravity.CENTER);
        pickers.setPadding(0, Ui.dp(this, 10), 0, 0);
        final NumberPicker rows = picker(page.gridR), cols = picker(page.gridC);
        pickers.addView(labeled("Rows", rows));
        View gap = new View(this);
        pickers.addView(gap, new LinearLayout.LayoutParams(Ui.dp(this, 28), 1));
        pickers.addView(labeled("Columns", cols));
        l.addView(pickers);
        NumberPicker.OnValueChangeListener live = new NumberPicker.OnValueChangeListener() {
            @Override public void onValueChange(NumberPicker p, int o, int n) { zoomView.setGrid(rows.getValue(), cols.getValue()); }
        };
        rows.setOnValueChangedListener(live);
        cols.setOnValueChangedListener(live);
        final int oldR = page.gridR, oldC = page.gridC;
        new AlertDialog.Builder(this)
                .setTitle("Split into panels")
                .setView(l)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        snapshot();
                        page.gridR = rows.getValue(); page.gridC = cols.getValue();
                        edited();
                        zoomView.setGrid(page.gridR, page.gridC);
                        Ui.setToolActive(toolGrid, page.hasGrid());
                    }
                })
                .setNeutralButton("No split", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        snapshot();
                        page.gridR = 1; page.gridC = 1;
                        edited();
                        zoomView.setGrid(1, 1);
                        Ui.setToolActive(toolGrid, false);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) { zoomView.setGrid(oldR, oldC); }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override public void onCancel(DialogInterface d) { zoomView.setGrid(oldR, oldC); }
                })
                .show();
    }

    private NumberPicker picker(int v) {
        NumberPicker p = new NumberPicker(this);
        p.setMinValue(1);
        p.setMaxValue(8);
        p.setValue(Math.max(1, Math.min(8, v)));
        p.setWrapSelectorWheel(false);
        return p;
    }

    private LinearLayout labeled(String label, View v) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.addView(Ui.text(this, label, 13, Ui.ACCENT, true));
        l.addView(v);
        return l;
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this page?")
                .setMessage("The photo and its edits are removed from this scan.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        int idx = session.indexOf(page);
                        session.remove(page);
                        if (session.pages.isEmpty()) { leave(); return; }
                        page = session.pages.get(Math.min(idx, session.pages.size() - 1));
                        bindPage();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================================================================== filmstrip

    private String signature() {
        StringBuilder b = new StringBuilder();
        for (Page p : session.pages) b.append(p.id).append(':').append(p.version).append(',');
        return b.toString();
    }

    /** Rebuilds the thumbnails when pages were added, removed, reordered or edited. */
    private void rebuildStrip(boolean force) {
        String sig = signature();
        if (!force && sig.equals(stripSignature)) return;
        boolean first = stripSignature.isEmpty();
        stripSignature = sig;
        strip.removeAllViews();
        for (int i = 0; i < session.pages.size(); i++) strip.addView(thumb(session.pages.get(i), i), thumbParams());
        // "+" adds more pages to this scan
        FrameLayout add = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable dash = new android.graphics.drawable.GradientDrawable();
        dash.setCornerRadius(Ui.dp(this, 8));
        dash.setStroke(Ui.dp(this, 1.5f), 0x66E6F0F7, Ui.dp(this, 5), Ui.dp(this, 4));
        add.setBackground(Ui.ripple(dash, true));
        ImageView plus = new ImageView(this);
        plus.setImageDrawable(Ui.icon(this, R.drawable.ic_plus, Ui.LIGHT));
        plus.setScaleType(ImageView.ScaleType.CENTER);
        add.addView(plus, new FrameLayout.LayoutParams(-1, -1));
        add.setContentDescription("Add pages");
        add.setClickable(true);
        Ui.pressable(add);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mode == MODE_CROP && !commitCrop(true)) return;
                startActivityForResult(CameraActivity.intentAdd(EditActivity.this), REQ_ADD);
            }
        });
        strip.addView(add, thumbParams());
        if (first) for (int i = 0; i < strip.getChildCount(); i++) Ui.reveal(strip.getChildAt(i), 30L * Math.min(i, 8));
    }

    private LinearLayout.LayoutParams thumbParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 54), Ui.dp(this, 70));
        lp.rightMargin = Ui.dp(this, 8);
        return lp;
    }

    private View thumb(final Page p, int index) {
        FrameLayout f = new FrameLayout(this);
        f.setTag(p.id);
        int pad = Ui.dp(this, 3);
        f.setPadding(pad, pad, pad, pad);
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        f.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        TextView num = Ui.text(this, String.valueOf(index + 1), 10.5f, Ui.LIGHT, true);
        num.setBackground(Ui.round(0xB3081420, Ui.dp(this, 7)));
        num.setGravity(Gravity.CENTER);
        num.setMinWidth(Ui.dp(this, 16));
        num.setPadding(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        f.addView(num, Ui.frame(-2, Ui.dp(this, 15), Gravity.BOTTOM | Gravity.START));
        f.setClickable(true);
        f.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { jumpTo(p.id); }
        });
        f.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { thumbMenu(p.id); return true; }
        });
        final String key = p.renderKey() + "@strip";
        Bitmap c = app.cached(key);
        if (c != null) iv.setImageBitmap(c);
        else {
            final Page snap = p.copy();
            app.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap t = Imaging.renderFinished(snap, 200);
                    app.cache(key, t);
                    app.ui(new Runnable() { @Override public void run() { if (t != null) iv.setImageBitmap(t); } });
                }
            });
        }
        return f;
    }

    /** Highlights the current page in the strip and keeps it in view. */
    private void selectThumb() {
        View sel = null;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View v = strip.getChildAt(i);
            if (v.getTag() == null) continue;
            boolean on = v.getTag().equals(page.id);
            v.setBackground(on ? Ui.round(Ui.accentA(0x33), Ui.dp(this, 9), Ui.ACCENT, Ui.dp(this, 2))
                    : Ui.round(0xFF22384D, Ui.dp(this, 9)));
            v.animate().alpha(on ? 1f : 0.7f).scaleX(on ? 1f : 0.92f).scaleY(on ? 1f : 0.92f)
                    .setStartDelay(0).setDuration(200).start();
            if (on) sel = v;
        }
        if (sel != null) {
            final View target = sel;
            stripScroll.post(new Runnable() {
                @Override
                public void run() {
                    int x = target.getLeft() - (stripScroll.getWidth() - target.getWidth()) / 2;
                    stripScroll.smoothScrollTo(Math.max(0, x), 0);
                }
            });
        }
    }

    private void jumpTo(String id) {
        Page p = session.find(id);
        if (p == null || p == page) return;
        int dir = session.indexOf(p) > session.indexOf(page) ? 1 : -1;
        if (mode == MODE_CROP && !commitCrop(true)) return;
        final Page target = p;
        final float w = Math.max(1, stage.getWidth());
        stage.animate().translationX(-dir * w * 0.25f).alpha(0f).setStartDelay(0).setDuration(110)
                .setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        page = target;
                        bindPage();
                        stage.setTranslationX(0);
                        stage.animate().alpha(1f).setStartDelay(0).setDuration(220)
                                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
                    }
                }).start();
    }

    private void thumbMenu(final String id) {
        final Page p = session.find(id);
        if (p == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Page " + (session.indexOf(p) + 1))
                .setItems(new String[]{"Duplicate to crop another area", "Reset all edits", "Delete"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (mode == MODE_CROP) commitCrop(false);
                        if (which == 0) {
                            Page c = p.copy();
                            c.id = Session.newId();
                            c.version = 0;
                            session.pages.add(session.indexOf(p) + 1, c);
                            session.save();
                            page = c;
                            bindPage();
                            if (mode != MODE_CROP) setMode(MODE_CROP);
                        } else if (which == 1) {
                            if (p != page) { page = p; bindPage(); }
                            resetPage();
                        } else {
                            page = p;
                            confirmDelete();
                        }
                    }
                })
                .show();
    }

    /** After the current page's edits: refresh just its thumbnail. */
    private final Runnable refreshStrip = new Runnable() {
        @Override public void run() { rebuildStrip(false); selectThumb(); }
    };

    // ================================================================== save / leave

    private void save() {
        if (mode == MODE_CROP && !commitCrop(true)) return;
        if (session.pages.isEmpty()) return;
        ExportUi.dialog(this, session.pages, ExportUi.defaultName(), false, new ExportUi.Choice() {
            @Override
            public void chosen(int format, boolean split, String name, boolean share) {
                if (share) ExportUi.run(EditActivity.this, session.pages, format, split, name, true, null, null);
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
                    Toast.makeText(EditActivity.this, "Updated \u201c" + clean + "\u201d in " + r.where, Toast.LENGTH_LONG).show();
                    startActivity(new Intent(EditActivity.this, SavedActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                } else {
                    session.clear();
                    startActivity(CameraActivity.intent(EditActivity.this).putExtra(CameraActivity.EXTRA_SAVED, clean)
                            .putExtra(CameraActivity.EXTRA_WHERE, r.where));
                }
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

    @Override
    protected void onResume() {
        super.onResume();
        if (session.pages.isEmpty()) { leave(); return; }
        if (session.find(page.id) == null) { page = session.pages.get(0); bindPage(); return; }
        if (!signature().equals(stripSignature)) {
            // pages were added or reordered elsewhere
            int idx = session.indexOf(page), n = session.pages.size();
            SavedStore.Entry editing = app.editingEntry();
            titleTv.setText(editing != null ? editing.name : n > 1 ? "Page " + (idx + 1) + " of " + n : "Scan");
            Ui.setVisible(arrangeBtn, n > 1);
            rebuildStrip(false);
            selectThumb();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        String id = data == null ? null : data.getStringExtra(RESULT_PAGE);
        if ((req == REQ_ADD || req == REQ_ARRANGE) && id != null && session.find(id) != null) {
            // new photos open straight in crop mode; a tapped page in Rearrange opens here
            page = session.find(id);
            if (mode != MODE_CROP) setMode(MODE_CROP);
            bindPage();
        }
    }

    /** Leaves the editor: back to the camera, or back to Saved scans when editing one of them. */
    private void leave() {
        if (app.isEditingSaved()) app.endEdit();
        finish();
    }

    /** Back: keep the crop, then leave (closing a saved scan's workspace if one is open). */
    private void goBack() {
        if (mode == MODE_CROP) commitCrop(false);
        leave();
    }
}
