package com.filmscan.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Reorder pages by dragging. Opened from the editor only when needed; tap a page to jump to it. */
public final class RearrangeActivity extends BaseActivity {
    private App app;
    private Session session;
    private ReorderGrid grid;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        app = App.get();
        session = app.session();
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG);

        LinearLayout top = Ui.topBar(this);
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, "Back");
        top.addView(back);
        top.addView(Ui.title(this, "Rearrange"), Ui.weight(1));
        ImageView clear = Ui.iconButton(this, R.drawable.ic_delete, "Discard all pages");
        top.addView(clear);
        Ui.setVisible(clear, !app.isEditingSaved());
        TextView done = Ui.button(this, "Done", true);
        done.setMinHeight(Ui.dp(this, 40));
        done.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(this, 20)), true));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
        dlp.leftMargin = Ui.dp(this, 4);
        dlp.rightMargin = Ui.dp(this, 8);
        top.addView(done, dlp);
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout hint = new LinearLayout(this);
        hint.setGravity(Gravity.CENTER_VERTICAL);
        hint.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 4));
        ImageView hi = new ImageView(this);
        hi.setImageDrawable(Ui.icon(this, R.drawable.ic_drag, Ui.MUTED));
        hint.addView(hi, new LinearLayout.LayoutParams(Ui.dp(this, 18), Ui.dp(this, 18)));
        TextView ht = Ui.text(this, "Hold and drag to move a page. Tap to open it.", 13, Ui.MUTED, false);
        ht.setPadding(Ui.dp(this, 8), 0, 0, 0);
        hint.addView(ht);
        col.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        grid = new ReorderGrid(this);
        sv.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);

        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(RearrangeActivity.this)
                        .setTitle("Discard all pages?")
                        .setMessage("Pages you have not saved will be lost. Saved scans are not affected.")
                        .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int w) {
                                session.clear();
                                startActivity(CameraActivity.intent(RearrangeActivity.this));
                                finish();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        grid.setListener(new ReorderGrid.Listener() {
            @Override
            public void onOrderChanged(List<View> order) {
                List<Page> pages = new ArrayList<Page>();
                for (View v : order) {
                    Page p = session.find((String) v.getTag());
                    if (p != null) pages.add(p);
                }
                session.setOrder(pages);
                for (int i = 0; i < order.size(); i++) {
                    TextView badge = (TextView) ((FrameLayout) order.get(i)).getChildAt(1);
                    String s = String.valueOf(i + 1);
                    if (!s.contentEquals(badge.getText())) {
                        badge.setText(s);
                        badge.setScaleX(0.6f);
                        badge.setScaleY(0.6f);
                        badge.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start();
                    }
                }
            }
        });

        List<View> tiles = new ArrayList<View>();
        for (int i = 0; i < session.pages.size(); i++) tiles.add(tile(session.pages.get(i), i));
        grid.setTiles(tiles);
        for (int i = 0; i < tiles.size(); i++) {
            View t = tiles.get(i);
            t.setAlpha(0f);
            t.setScaleX(0.92f);
            t.setScaleY(0.92f);
            t.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(40L * Math.min(i, 10)).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(2f)).withEndAction(Ui.resetDelay(t)).start();
        }
    }

    private View tile(final Page page, int index) {
        FrameLayout card = new FrameLayout(this);
        card.setTag(page.id);
        card.setBackground(Ui.ripple(Ui.round(0xFF22384D, Ui.dp(this, 14)), true));
        int p = Ui.dp(this, 8);
        card.setPadding(p, p, p, p);
        card.setClickable(true);
        final ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = Ui.text(this, String.valueOf(index + 1), 12, Ui.ON_ACCENT, true);
        badge.setBackground(Ui.round(Ui.ACCENT, Ui.dp(this, 11)));
        badge.setGravity(Gravity.CENTER);
        badge.setMinWidth(Ui.dp(this, 22));
        badge.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        card.addView(badge, Ui.frame(-2, Ui.dp(this, 22), Gravity.TOP | Gravity.START));
        ImageView dragIcon = new ImageView(this);
        dragIcon.setImageDrawable(Ui.icon(this, R.drawable.ic_drag, 0xB3E6F0F7));
        dragIcon.setScaleType(ImageView.ScaleType.CENTER);
        dragIcon.setBackground(Ui.oval(0x99081420));
        card.addView(dragIcon, Ui.frame(Ui.dp(this, 30), Ui.dp(this, 30), Gravity.BOTTOM | Gravity.END));
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_OK, new Intent().putExtra(EditActivity.RESULT_PAGE, page.id));
                finish();
            }
        });
        final String key = page.renderKey() + "@grid";
        Bitmap c = app.cached(key);
        if (c != null) img.setImageBitmap(c);
        else {
            final Page snap = page.copy();
            app.run(new Runnable() {
                @Override
                public void run() {
                    final Bitmap t = Imaging.renderFinished(snap, 480);
                    app.cache(key, t);
                    app.ui(new Runnable() {
                        @Override
                        public void run() {
                            if (t == null) return;
                            img.setAlpha(0f);
                            img.setImageBitmap(t);
                            img.animate().alpha(1f).setStartDelay(0).setDuration(220).start();
                        }
                    });
                }
            });
        }
        return card;
    }
}
