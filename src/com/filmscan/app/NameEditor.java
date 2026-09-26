package com.filmscan.app;

import android.app.Activity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * File name field with tap-to-add suggestion chips: date, the enabled suggestion sets (with tabs
 * when more than one is on) and recent names. Used by the save sheet and the rename sheet.
 */
public final class NameEditor {
    public final LinearLayout view;
    public final EditText field;
    /** Rows that get a staggered entrance once the sheet is visible. */
    public final List<View> rows = new ArrayList<View>();

    private final Activity a;
    private final Prefs prefs;
    private final int pages;
    private LinearLayout packArea;
    private final List<TextView> tabs = new ArrayList<TextView>();

    public NameEditor(Activity a, String initial, int pages) {
        this.a = a;
        this.prefs = App.get().prefs();
        this.pages = pages;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);

        LinearLayout box = new LinearLayout(a);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackground(Ui.round(Ui.BG, Ui.dp(a, 14), Ui.accentA(0x55), Ui.dp(a, 1.5f)));
        box.setPadding(Ui.dp(a, 14), 0, Ui.dp(a, 2), 0);
        field = new EditText(a);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setTextColor(Ui.LIGHT);
        field.setTextSize(17);
        field.setHint("File name");
        field.setHintTextColor(Ui.MUTED);
        field.setText(initial);
        field.setSelectAllOnFocus(true);
        field.setPadding(0, Ui.dp(a, 12), 0, Ui.dp(a, 12));
        box.addView(field, Ui.weight(1));
        final ImageView clear = Ui.iconButton(a, R.drawable.ic_close_circle, "Clear name");
        clear.setImageDrawable(Ui.icon(a, R.drawable.ic_close_circle, Ui.MUTED));
        box.addView(clear, new LinearLayout.LayoutParams(Ui.dp(a, 44), Ui.dp(a, 44)));
        view.addView(box, new LinearLayout.LayoutParams(-1, -2));
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { field.setText(""); field.requestFocus(); }
        });
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int co, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int be, int co) { }
            @Override public void afterTextChanged(Editable s) { clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.INVISIBLE); }
        });

        if (!prefs.nameSuggest()) return;
        if (prefs.nameShowDate()) {
            LinearLayout dateRow = chipRow(R.drawable.ic_calendar);
            fillDateRow(dateRow, false);
            addRow(dateRow);
        }
        List<Suggest.Pack> packs = Suggest.enabledPacks(prefs);
        if (packs.size() > 1) {
            // one tab per enabled set keeps the sheet short
            LinearLayout tabRow = new LinearLayout(a);
            Suggest.Pack current = packs.get(0);
            for (Suggest.Pack pk : packs) if (pk.id.equals(prefs.lastPack())) current = pk;
            for (final Suggest.Pack pk : packs) {
                TextView t = Ui.chip(a, pk.title, 0);
                android.graphics.drawable.Drawable d = Ui.icon(a, pk.icon, Ui.MUTED);
                int s = Ui.dp(a, 18);
                d.setBounds(0, 0, s, s);
                t.setCompoundDrawablesRelative(d, null, null, null);
                t.setCompoundDrawablePadding(Ui.dp(a, 6));
                t.setTag(pk);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.rightMargin = Ui.dp(a, 8);
                tabRow.addView(t, lp);
                tabs.add(t);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { selectPack(pk, true); }
                });
            }
            HorizontalScrollView hs = new HorizontalScrollView(a);
            hs.setHorizontalScrollBarEnabled(false);
            hs.addView(tabRow);
            addRow(hs);
            packArea = new LinearLayout(a);
            packArea.setOrientation(LinearLayout.VERTICAL);
            view.addView(packArea, new LinearLayout.LayoutParams(-1, -2));
            selectPack(current, false);
        } else if (packs.size() == 1) {
            packArea = new LinearLayout(a);
            packArea.setOrientation(LinearLayout.VERTICAL);
            view.addView(packArea, new LinearLayout.LayoutParams(-1, -2));
            fillPack(packs.get(0), false);
        }
        if (prefs.nameShowRecent()) {
            List<String> recent = Naming.recent(prefs);
            if (!recent.isEmpty()) addRow(wordsRow(R.drawable.ic_history, recent, true));
        }
    }

    public String name() {
        String n = field.getText().toString().trim();
        return n.isEmpty() ? Naming.defaultName(prefs) : n;
    }

    private void addRow(View row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, rows.isEmpty() ? 14 : 8);
        view.addView(row, lp);
        rows.add(row);
    }

    private void selectPack(Suggest.Pack pk, boolean animate) {
        prefs.lastPack(pk.id);
        for (TextView t : tabs) {
            boolean on = t.getTag() == pk;
            t.setBackground(Ui.ripple(on ? Ui.round(Ui.accentA(0x30), Ui.dp(a, 18), Ui.ACCENT, Ui.dp(a, 1.5f))
                    : Ui.round(Ui.PANEL_HI, Ui.dp(a, 18), 0x2EE6F0F7, Ui.dp(a, 1)), true));
            t.setTextColor(on ? Ui.ACCENT : Ui.LIGHT);
            t.getCompoundDrawablesRelative()[0].setTint(on ? Ui.ACCENT : Ui.MUTED);
        }
        fillPack(pk, animate);
    }

    private void fillPack(Suggest.Pack pk, boolean animate) {
        packArea.removeAllViews();
        int shown = 0;
        for (Suggest.Cat c : pk.cats) {
            List<String> words = Suggest.activeWords(prefs, c, pages);
            if (words.isEmpty()) continue;
            LinearLayout row = wordsRow(c.icon, words, false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(a, 8);
            packArea.addView(row, lp);
            if (animate) Ui.reveal(row, 35L * shown);
            else rows.add(row);
            shown++;
        }
    }

    /** A leading icon followed by a horizontally scrolling line of chips. */
    private LinearLayout chipRow(int icon) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(a);
        iv.setImageDrawable(Ui.icon(a, icon, Ui.MUTED));
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(a, 22), Ui.dp(a, 22)));
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chips = new LinearLayout(a);
        chips.setPadding(Ui.dp(a, 10), 0, 0, 0);
        hs.addView(chips);
        row.addView(hs, Ui.weight(1));
        return row;
    }

    private static LinearLayout chipsOf(LinearLayout row) {
        return (LinearLayout) ((HorizontalScrollView) row.getChildAt(1)).getChildAt(0);
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(a, 8);
        return lp;
    }

    private LinearLayout wordsRow(int icon, List<String> words, boolean replace) {
        LinearLayout row = chipRow(icon);
        LinearLayout chips = chipsOf(row);
        for (String w : words) chips.addView(wordChip(w, replace), chipParams());
        return row;
    }

    /** Tapping adds the word to the end of the name; recent names replace it. */
    private TextView wordChip(final String w, final boolean replace) {
        TextView t = Ui.chip(a, w, 0);
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String s = replace ? w : Naming.appendWord(field.getText().toString(), w);
                field.setText(s);
                field.setSelection(s.length());
            }
        });
        return t;
    }

    /** "Date ▾" swaps between day/month/year pieces and complete date styles. */
    private void fillDateRow(final LinearLayout row, final boolean expanded) {
        final LinearLayout chips = chipsOf(row);
        chips.removeAllViews();
        TextView toggle = Ui.chip(a, "Date", R.drawable.ic_chevron_down);
        toggle.setTextColor(Ui.ACCENT);
        toggle.getCompoundDrawablesRelative()[2].setTint(Ui.ACCENT);
        chips.addView(toggle, chipParams());
        List<String> words = expanded ? Naming.dateChoices() : Naming.dateParts();
        for (String w : words) chips.addView(wordChip(w, false), chipParams());
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fillDateRow(row, !expanded);
                for (int i = 1; i < chips.getChildCount(); i++) Ui.reveal(chips.getChildAt(i), i * 25L);
                ((HorizontalScrollView) row.getChildAt(1)).smoothScrollTo(0, 0);
            }
        });
    }
}
