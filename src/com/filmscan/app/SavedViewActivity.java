package com.filmscan.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Views the pages of one saved scan (swipe between pages), with share, edit again and delete. */
public final class SavedViewActivity extends BaseActivity {
    private static final String EXTRA_ID = "saved";

    private App app;
    private SavedStore.Entry entry;
    private List<Page> pages = new ArrayList<Page>();
    private int index;
    private int gen;
    private boolean loaded;
    private PageSwiper swiper;
    private ZoomImageView view;
    private FrameLayout stage;
    private ProgressBar spinner;
    private TextView titleTv, metaTv;
    private ImageView prevBtn, nextBtn;

    public static Intent intent(Context c, String id) {
        return new Intent(c, SavedViewActivity.class).putExtra(EXTRA_ID, id);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        entry = app.saved().find(getIntent().getStringExtra(EXTRA_ID));
        if (entry == null) { finish(); return; }
        if (b != null) { index = b.getInt("index"); loaded = true; }
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);

        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        titleTv = Ui.title(this, entry.name);
        titleTv.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(titleTv, Ui.weight(1));
        prevBtn = Ui.iconButton(this, R.drawable.ic_prev, "Previous page");
        nextBtn = Ui.iconButton(this, R.drawable.ic_next, "Next page");
        top.addView(prevBtn);
        top.addView(nextBtn);
        ImageView rename = Ui.iconButton(this, R.drawable.ic_rename, "Rename");
        top.addView(rename);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        metaTv = Ui.text(this, "", 13, Ui.MUTED, false);
        metaTv.setGravity(Gravity.CENTER);
        metaTv.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 6));
        col.addView(metaTv, new LinearLayout.LayoutParams(-1, -2));

        stage = new FrameLayout(this);
        view = new ZoomImageView(this);
        stage.addView(view, new FrameLayout.LayoutParams(-1, -1));
        spinner = new ProgressBar(this);
        stage.addView(spinner, Ui.frame(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.CENTER));
        col.addView(stage, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setBackgroundColor(Ui.PANEL);
        bottom.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2));
        LinearLayout share = Ui.tool(this, R.drawable.ic_share, "Share");
        LinearLayout edit = Ui.tool(this, R.drawable.ic_edit, "Edit again");
        LinearLayout del = Ui.tool(this, R.drawable.ic_delete, "Delete");
        bottom.addView(share, Ui.weight(1));
        bottom.addView(edit, Ui.weight(1));
        bottom.addView(del, Ui.weight(1));
        col.addView(bottom, new LinearLayout.LayoutParams(-1, -2));
        setContentView(col);

        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        prevBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { step(-1); } });
        nextBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { step(1); } });
        rename.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SavedActivity.rename(SavedViewActivity.this, entry, new Runnable() {
                    @Override public void run() { titleTv.setText(entry.name); }
                });
            }
        });
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { SavedActivity.share(SavedViewActivity.this, entry); }
        });
        edit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { SavedActivity.editAgain(SavedViewActivity.this, entry); }
        });
        LinearLayout[] tools = {share, edit, del};
        for (int i = 0; i < tools.length; i++) Ui.reveal(tools[i], 80 + 50L * i);
        del.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SavedActivity.confirmDelete(SavedViewActivity.this, entry, new Runnable() {
                    @Override public void run() { finish(); }
                });
            }
        });
        swiper = new PageSwiper(stage, new PageSwiper.Host() {
            @Override public boolean canGo(int dir) { int i = index + dir; return i >= 0 && i < pages.size(); }
            @Override public boolean beforeGo(int dir) { return true; }
            @Override public void go(int dir) { show(index + dir); }
        });
        view.setSwipeListener(swiper.gesture);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // reload: the scan may have been edited, renamed or emptied meanwhile
        if (app.saved().find(entry.id) == null) { finish(); return; }
        titleTv.setText(entry.name);
        spinner.setVisibility(View.VISIBLE);
        final SavedStore.Entry e = entry;
        app.run(new Runnable() {
            @Override
            public void run() {
                final List<Page> ps = app.saved().pages(e);
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        pages = ps;
                        if (pages.isEmpty()) {
                            spinner.setVisibility(View.GONE);
                            Toast.makeText(SavedViewActivity.this, "The photos of this scan are missing", Toast.LENGTH_LONG).show();
                            return;
                        }
                        show(loaded ? Math.min(index, pages.size() - 1) : 0);
                        loaded = true;
                    }
                });
            }
        });
    }

    private void show(int i) {
        index = i;
        int n = pages.size();
        Ui.setVisible(prevBtn, n > 1);
        Ui.setVisible(nextBtn, n > 1);
        prevBtn.setAlpha(i > 0 ? 1f : 0.3f);
        nextBtn.setAlpha(i < n - 1 ? 1f : 0.3f);
        metaTv.setText((n > 1 ? "Page " + (i + 1) + " of " + n + " · " : "") + SavedActivity.meta(entry).replaceFirst("^\\d+ pages? · ", "")
                + (entry.where != null && entry.where.length() > 0 ? "\nFiles in " + entry.where : ""));
        final Page p = pages.get(i);
        view.setGrid(1, 1);
        final String key = p.renderKey() + "@saved";
        Bitmap c = app.cached(key);
        if (c != null) { view.setBitmap(c, false); spinner.setVisibility(View.GONE); return; }
        view.setBitmap(null, false);
        spinner.setVisibility(View.VISIBLE);
        final int g = ++gen;
        app.run(new Runnable() {
            @Override
            public void run() {
                final Bitmap b = Imaging.renderFinished(p, Imaging.EDIT_SIDE);
                app.cache(key, b);
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        if (g != gen || isFinishing()) return;
                        spinner.setVisibility(View.GONE);
                        if (b != null) view.setBitmap(b, false);
                    }
                });
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("index", index);
    }

    private void step(int d) {
        if (pages.size() < 2) return;
        swiper.step(d);
    }
}
