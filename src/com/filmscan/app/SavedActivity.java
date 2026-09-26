package com.filmscan.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Previously saved scans. */
public final class SavedActivity extends BaseActivity {
    private App app;
    private SavedStore store;
    private List<SavedStore.Entry> items = new ArrayList<SavedStore.Entry>();
    private ListView list;
    private Adapter adapter;
    private LinearLayout empty;
    private TextView titleTv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        store = app.saved();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);
        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        titleTv = Ui.title(this, "Saved scans");
        top.addView(titleTv, Ui.weight(1));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout mid = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(Ui.dp(this, 10));
        int p = Ui.dp(this, 12);
        list.setPadding(p, Ui.dp(this, 4), p, p);
        list.setClipToPadding(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0));
        adapter = new Adapter();
        list.setAdapter(adapter);
        android.view.animation.AnimationSet in = new android.view.animation.AnimationSet(true);
        in.addAnimation(new android.view.animation.AlphaAnimation(0f, 1f));
        in.addAnimation(new android.view.animation.TranslateAnimation(0, 0, Ui.dp(this, 20), 0));
        in.setDuration(280);
        in.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
        list.setLayoutAnimation(new android.view.animation.LayoutAnimationController(in, 0.1f));
        mid.addView(list, new FrameLayout.LayoutParams(-1, -1));
        empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.addView(Ui.text(this, "No saved scans yet", 19, Ui.LIGHT, true));
        TextView ed = Ui.text(this, "When you save a scan it appears here, so you can view, share or edit it again later.", 14.5f, Ui.MUTED, false);
        ed.setGravity(Gravity.CENTER);
        ed.setPadding(Ui.dp(this, 32), Ui.dp(this, 8), Ui.dp(this, 32), 0);
        empty.addView(ed);
        mid.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        col.addView(mid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);

        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                startActivity(SavedViewActivity.intent(SavedActivity.this, items.get(pos).id));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, int pos, long id) {
                entryMenu(SavedActivity.this, items.get(pos), new Runnable() { @Override public void run() { refresh(); } });
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        app.setSavedListener(new Runnable() { @Override public void run() { refresh(); } });
        refresh();
    }

    @Override
    protected void onPause() {
        app.setSavedListener(null);
        super.onPause();
    }

    private boolean firstShow = true;

    private void refresh() {
        items = store.list();
        if (firstShow && !items.isEmpty()) { firstShow = false; list.scheduleLayoutAnimation(); }
        titleTv.setText(items.isEmpty() ? "Saved scans" : "Saved scans (" + items.size() + ")");
        Ui.setVisible(empty, items.isEmpty());
        Ui.setVisible(list, !items.isEmpty());
        adapter.notifyDataSetChanged();
    }

    static String meta(SavedStore.Entry e) {
        return (e.pages == 1 ? "1 page" : e.pages + " pages") + " · " + e.format + " · "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(e.time));
    }

    // ------------------------------------------------------------------ actions shared with the viewer

    static void entryMenu(final Activity a, final SavedStore.Entry e, final Runnable changed) {
        String[] items = {"Share", "Edit", "Rename", "Delete"};
        new AlertDialog.Builder(a)
                .setTitle(e.name)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) share(a, e);
                        else if (which == 1) editAgain(a, e);
                        else if (which == 2) rename(a, e, changed);
                        else confirmDelete(a, e, changed);
                    }
                })
                .show();
    }

    static void share(final Activity a, final SavedStore.Entry e) {
        final List<Page> pages = App.get().saved().pages(e);
        if (pages.isEmpty()) { Toast.makeText(a, "The photos of this scan are missing", Toast.LENGTH_LONG).show(); return; }
        ExportUi.dialog(a, pages, e.name, true, new ExportUi.Choice() {
            @Override
            public void chosen(int format, boolean split, String name, boolean share) {
                ExportUi.run(a, pages, format, split, name, true, null, null);
            }
        });
    }

    /** Opens one saved scan in its own workspace. Nothing from other scans is mixed in. */
    static void editAgain(final Activity a, final SavedStore.Entry e) {
        App app = App.get();
        Session s = app.beginEdit(e);
        if (s.pages.isEmpty()) {
            app.endEdit();
            Toast.makeText(a, "The photos of this scan are missing", Toast.LENGTH_LONG).show();
            return;
        }
        a.startActivity(EditActivity.intent(a, s.pages.get(0).id));
    }

    /** Rename with the same suggestions as the save sheet. */
    static void rename(final Activity a, final SavedStore.Entry e, final Runnable changed) {
        ExportUi.renameSheet(a, e.name, e.pages, new ExportUi.Named() {
            @Override
            public void named(String name) {
                App.get().saved().rename(e, Exporter.sanitize(name));
                Naming.remember(App.get().prefs(), name);
                if (changed != null) changed.run();
            }
        });
    }

    static void confirmDelete(final Context c, final SavedStore.Entry e, final Runnable changed) {
        new AlertDialog.Builder(c)
                .setTitle("Delete \u201c" + e.name + "\u201d?")
                .setMessage("It is removed from Saved scans. Files already saved to your gallery or Downloads are kept.")
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        App.get().saved().delete(e);
                        if (changed != null) changed.run();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------------ list

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }

        @Override public Object getItem(int i) { return items.get(i); }

        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            Context c = SavedActivity.this;
            if (convert == null) {
                h = new Holder();
                LinearLayout row = new LinearLayout(c);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(Ui.ripple(Ui.round(Ui.PANEL, Ui.dp(c, 12)), true));
                int p = Ui.dp(c, 10);
                row.setPadding(p, p, p, p);
                FrameLayout box = new FrameLayout(c);
                box.setBackground(Ui.round(0xFF22384D, Ui.dp(c, 8)));
                int bp = Ui.dp(c, 4);
                box.setPadding(bp, bp, bp, bp);
                h.img = new ImageView(c);
                h.img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                box.addView(h.img, new FrameLayout.LayoutParams(-1, -1));
                row.addView(box, new LinearLayout.LayoutParams(Ui.dp(c, 72), Ui.dp(c, 72)));
                LinearLayout texts = new LinearLayout(c);
                texts.setOrientation(LinearLayout.VERTICAL);
                texts.setPadding(Ui.dp(c, 14), 0, Ui.dp(c, 4), 0);
                h.name = Ui.text(c, "", 15.5f, Ui.LIGHT, true);
                h.name.setSingleLine(true);
                h.name.setEllipsize(TextUtils.TruncateAt.END);
                texts.addView(h.name);
                h.meta = Ui.text(c, "", 12.5f, Ui.MUTED, false);
                h.meta.setPadding(0, Ui.dp(c, 4), 0, 0);
                texts.addView(h.meta);
                row.addView(texts, Ui.weight(1));
                h.more = new ImageView(c);
                h.more.setImageDrawable(Ui.icon(c, R.drawable.ic_share, Ui.MUTED));
                h.more.setScaleType(ImageView.ScaleType.CENTER);
                h.more.setBackground(Ui.ripple(null, false));
                h.more.setContentDescription("Share");
                h.more.setClickable(true);
                h.more.setFocusable(false);
                row.addView(h.more, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
                row.setTag(h);
                convert = row;
            } else {
                h = (Holder) convert.getTag();
            }
            final SavedStore.Entry e = items.get(pos);
            h.name.setText(e.name);
            h.meta.setText(meta(e));
            h.more.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { share(SavedActivity.this, e); }
            });
            final String key = SavedStore.thumbKey(e);
            h.key = key;
            Bitmap cached = app.cached(key);
            if (cached != null) {
                h.img.setImageBitmap(cached);
            } else {
                h.img.setImageDrawable(null);
                final Holder fh = h;
                app.run(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap t = BitmapFactory.decodeFile(e.thumb().getPath());
                        app.cache(key, t);
                        app.ui(new Runnable() {
                            @Override public void run() { if (key.equals(fh.key) && t != null) fh.img.setImageBitmap(t); }
                        });
                    }
                });
            }
            return convert;
        }
    }

    private static final class Holder {
        ImageView img, more;
        TextView name, meta;
        String key;
    }
}
