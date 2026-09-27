package com.filmscan.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.filmscan.core.PdfPage;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF Options: page size, margins and file size, with a live preview of how each page will look.
 * Settings are remembered and apply only to PDF export; scans themselves are never changed.
 */
public final class PdfOptions {
    private PdfOptions() {}

    /** One-line summary for the save sheet, e.g. "A4 · Small margins · Balanced". */
    public static String summary(Prefs p) {
        String m = p.pdfMargin() == 0 ? "No margins" : PdfPage.MARGIN_NAMES[p.pdfMargin()] + " margins";
        return PdfPage.SIZE_NAMES[p.pdfPage()] + " \u00b7 " + m + " \u00b7 " + PdfQuality.NAMES[p.pdfQuality()];
    }

    public static void show(final Activity a, final List<Page> pages, final Runnable onChanged) {
        final App app = App.get();
        final Prefs prefs = app.prefs();
        final Dialog[] holder = new Dialog[1];
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(Ui.dp(a, 20), 0, Ui.dp(a, 20), 0);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.text(a, "PDF options", 19, Ui.LIGHT, true), Ui.weight(1));
        ImageView close = Ui.iconButton(a, R.drawable.ic_close, "Close");
        head.addView(close);
        c.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // live preview of one page, with page switching when there are several
        final PreviewView preview = new PreviewView(a);
        c.addView(preview, new LinearLayout.LayoutParams(-1, Ui.dp(a, 250)));
        LinearLayout nav = new LinearLayout(a);
        nav.setGravity(Gravity.CENTER);
        final ImageView prev = Ui.iconButton(a, R.drawable.ic_prev, "Previous page");
        final ImageView next = Ui.iconButton(a, R.drawable.ic_next, "Next page");
        final TextView pageNo = Ui.text(a, "", 13, Ui.MUTED, false);
        pageNo.setGravity(Gravity.CENTER);
        nav.addView(prev);
        nav.addView(pageNo, new LinearLayout.LayoutParams(Ui.dp(a, 80), -2));
        nav.addView(next);
        if (pages.size() > 1) c.addView(nav, new LinearLayout.LayoutParams(-1, -2));
        final int[] index = {0};
        final Runnable showPage = new Runnable() {
            @Override
            public void run() {
                final Page p = pages.get(index[0]).copy();
                pageNo.setText((index[0] + 1) + " / " + pages.size());
                prev.setAlpha(index[0] > 0 ? 1f : 0.3f);
                next.setAlpha(index[0] < pages.size() - 1 ? 1f : 0.3f);
                final String key = p.renderKey() + "@grid";
                Bitmap cached = app.cached(key);
                if (cached != null) { preview.setPicture(cached); return; }
                preview.setPicture(null);
                app.run(new Runnable() {
                    @Override
                    public void run() {
                        final Bitmap t = Imaging.renderFinished(p, 480);
                        app.cache(key, t);
                        app.ui(new Runnable() { @Override public void run() { if (t != null) preview.setPicture(t); } });
                    }
                });
            }
        };
        prev.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (index[0] > 0) { index[0]--; showPage.run(); } }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (index[0] < pages.size() - 1) { index[0]++; showPage.run(); } }
        });

        final TextView estimate = Ui.text(a, "", 12.5f, Ui.MUTED, false);
        final TextView qualityNote = Ui.text(a, "", 12.5f, Ui.MUTED, false);
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                preview.setOptions(prefs.pdfPage(), prefs.pdfMargin());
                qualityNote.setText(PdfQuality.NOTES[prefs.pdfQuality()]);
                long bytes = Storage.estimate(a, pages, Exporter.PDF);
                estimate.setText("Estimated PDF size \u2248 " + Storage.human(bytes) + (pages.size() > 1 ? " for " + pages.size() + " pages" : ""));
                if (onChanged != null) onChanged.run();
            }
        };

        section(a, c, "Page size");
        c.addView(choices(a, PdfPage.SIZE_NAMES, prefs.pdfPage(), new Pick() {
            @Override public void pick(int i) { prefs.pdfPage(i); refresh.run(); }
        }));
        section(a, c, "Margins");
        c.addView(choices(a, PdfPage.MARGIN_NAMES, prefs.pdfMargin(), new Pick() {
            @Override public void pick(int i) { prefs.pdfMargin(i); refresh.run(); }
        }));
        section(a, c, "File size");
        c.addView(choices(a, PdfQuality.NAMES, prefs.pdfQuality(), new Pick() {
            @Override public void pick(int i) { prefs.pdfQuality(i); refresh.run(); }
        }));
        qualityNote.setPadding(0, Ui.dp(a, 6), 0, 0);
        c.addView(qualityNote);
        LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(-1, -2);
        el.topMargin = Ui.dp(a, 12);
        c.addView(estimate, el);
        TextView note = Ui.text(a, "These settings only change the PDF. Your scans stay exactly as they are.", 12, Ui.MUTED, false);
        note.setPadding(0, Ui.dp(a, 4), 0, 0);
        c.addView(note);

        TextView done = Ui.button(a, "Done", true);
        done.setBackground(Ui.ripple(Ui.round(Ui.ACCENT, Ui.dp(a, 26)), true));
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Ui.dp(a, 52));
        dl.topMargin = Ui.dp(a, 18);
        c.addView(done, dl);

        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.addView(c, new android.widget.FrameLayout.LayoutParams(-1, -2));
        holder[0] = Ui.sheet(a, sv);
        refresh.run();
        showPage.run();
        View.OnClickListener dismiss = new View.OnClickListener() {
            @Override public void onClick(View v) { holder[0].dismiss(); }
        };
        done.setOnClickListener(dismiss);
        close.setOnClickListener(dismiss);
    }

    private interface Pick { void pick(int i); }

    private static void section(Context a, LinearLayout c, String title) {
        TextView t = Ui.text(a, title, 13, Ui.ACCENT, true);
        t.setPadding(0, Ui.dp(a, 14), 0, Ui.dp(a, 6));
        c.addView(t);
    }

    /** A row of single-choice chips. */
    private static View choices(final Context a, String[] labels, int selected, final Pick pick) {
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(a);
        hs.addView(row);
        final List<TextView> chips = new ArrayList<TextView>();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            final TextView t = Ui.chip(a, labels[i], 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(a, 8);
            row.addView(t, lp);
            chips.add(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int k = 0; k < chips.size(); k++) style(a, chips.get(k), k == idx);
                    pick.pick(idx);
                }
            });
        }
        for (int k = 0; k < chips.size(); k++) style(a, chips.get(k), k == selected);
        return hs;
    }

    private static void style(Context a, TextView t, boolean on) {
        t.setTextColor(on ? Ui.ON_ACCENT : Ui.LIGHT);
        t.setBackground(Ui.ripple(on ? Ui.round(Ui.ACCENT, Ui.dp(a, 18)) : Ui.round(Ui.PANEL_HI, Ui.dp(a, 18), 0x2EE6F0F7, Ui.dp(a, 1)), true));
    }

    /** Draws a page as it will appear in the PDF: page shape, margins and the picture's placement. */
    static final class PreviewView extends View {
        private Bitmap picture;
        private int size, margin;
        private final Paint page = new Paint(Paint.ANTI_ALIAS_FLAG), shadow = new Paint(Paint.ANTI_ALIAS_FLAG),
                guide = new Paint(Paint.ANTI_ALIAS_FLAG), img = new Paint(Paint.FILTER_BITMAP_FLAG), empty = new Paint();
        private final RectF r = new RectF();

        PreviewView(Context c) {
            super(c);
            page.setColor(0xFFFFFFFF);
            shadow.setColor(0x55000000);
            guide.setStyle(Paint.Style.STROKE);
            guide.setStrokeWidth(Ui.dp(c, 1));
            guide.setColor(0x6690A4B8);
            float d = Ui.dp(c, 4);
            guide.setPathEffect(new DashPathEffect(new float[]{d, d}, 0));
            empty.setColor(0xFFD7DEE5);
        }

        void setPicture(Bitmap b) { picture = b; invalidate(); }

        void setOptions(int size, int margin) { this.size = size; this.margin = margin; invalidate(); }

        @Override
        protected void onDraw(Canvas c) {
            int iw = picture != null ? picture.getWidth() : 3, ih = picture != null ? picture.getHeight() : 4;
            double[] lay = PdfPage.layout(size, margin, iw, ih);
            float pad = Ui.dp(getContext(), 12);
            float s = (float) Math.min((getWidth() - 2 * pad) / lay[0], (getHeight() - 2 * pad) / lay[1]);
            float pw = (float) lay[0] * s, ph = (float) lay[1] * s;
            float left = (getWidth() - pw) / 2f, top = (getHeight() - ph) / 2f;
            float sh = Ui.dp(getContext(), 3);
            c.drawRect(left + sh, top + sh, left + pw + sh, top + ph + sh, shadow);
            c.drawRect(left, top, left + pw, top + ph, page);
            float m = (float) PdfPage.MARGINS[margin] * s;
            if (m > 0) c.drawRect(left + m, top + m, left + pw - m, top + ph - m, guide);
            // PDF places the picture from its lower-left corner
            float x = left + (float) lay[2] * s, y = top + ph - (float) (lay[3] + lay[5]) * s;
            r.set(x, y, x + (float) lay[4] * s, y + (float) lay[5] * s);
            if (picture != null) c.drawBitmap(picture, null, r, img);
            else c.drawRect(r, empty);
        }
    }
}
