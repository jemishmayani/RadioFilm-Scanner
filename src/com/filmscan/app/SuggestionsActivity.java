package com.filmscan.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.widget.Toast;
import java.util.List;

/**
 * Configure name suggestions once: which sets appear when saving and renaming, which words each
 * category offers, and your own words for anything missing.
 */
public final class SuggestionsActivity extends BaseActivity {
    private Prefs prefs;
    private LinearLayout list;

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
        top.addView(Ui.title(this, "Name suggestions"), Ui.weight(1));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 32));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        build();
    }

    private void build() {
        list.removeAllViews();
        int delay = 0;

        TextView intro = Ui.text(this, "Choose what appears as tap-to-add words when you save or rename. Tap a word to show or hide it. Long-press your own words to remove them.", 13.5f, Ui.MUTED, false);
        intro.setPadding(Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 4));
        list.addView(intro);

        section("On the save and rename sheets", 0);
        switchRow(R.drawable.ic_star, "Show suggestions", null, prefs.nameSuggest(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.nameSuggest(v); }
        });
        switchRow(R.drawable.ic_calendar, "Date words", "September, 25, 2026 and full dates", prefs.nameShowDate(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.nameShowDate(v); }
        });
        switchRow(R.drawable.ic_history, "Recent names", null, prefs.nameShowRecent(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton c, boolean v) { prefs.nameShowRecent(v); }
        });
        for (final Suggest.Pack pk : Suggest.PACKS) {
            switchRow(pk.icon, pk.title, pk.subtitle, Suggest.packOn(prefs, pk), new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton c, boolean v) {
                    Suggest.setPackOn(prefs, pk, v);
                    View sec = list.findViewWithTag(pk.id);
                    if (sec != null) sec.animate().alpha(v ? 1f : 0.45f).setStartDelay(0).setDuration(200).start();
                }
            });
        }

        for (Suggest.Pack pk : Suggest.PACKS) {
            LinearLayout sec = new LinearLayout(this);
            sec.setOrientation(LinearLayout.VERTICAL);
            sec.setTag(pk.id);
            sec.setAlpha(Suggest.packOn(prefs, pk) ? 1f : 0.45f);
            TextView h = Ui.text(this, pk.title, 13, Ui.ACCENT, true);
            h.setPadding(Ui.dp(this, 20), Ui.dp(this, 26), Ui.dp(this, 20), Ui.dp(this, 2));
            sec.addView(h);
            for (Suggest.Cat c : pk.cats) sec.addView(category(c));
            list.addView(sec);
            Ui.reveal(sec, 60L * ++delay);
        }

        TextView reset = Ui.button(this, "Reset suggestions to defaults", false);
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-2, -2);
        rl.gravity = Gravity.CENTER_HORIZONTAL;
        rl.topMargin = Ui.dp(this, 28);
        list.addView(reset, rl);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(SuggestionsActivity.this)
                        .setTitle("Reset suggestions?")
                        .setMessage("Your own words are removed and every set goes back to its default words.")
                        .setPositiveButton("Reset", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { Suggest.reset(prefs); build(); }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private void section(String title, long delay) {
        TextView t = Ui.text(this, title, 13, Ui.ACCENT, true);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 4));
        list.addView(t);
    }

    private void switchRow(int icon, String title, String sub, boolean on, CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 58));
        r.setPadding(Ui.dp(this, 18), Ui.dp(this, 6), Ui.dp(this, 16), Ui.dp(this, 6));
        r.setBackground(Ui.ripple(null, true));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.icon(this, icon, Ui.MUTED));
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 8), 0);
        texts.addView(Ui.text(this, title, 15.5f, Ui.LIGHT, false));
        if (sub != null) texts.addView(Ui.text(this, sub, 12.5f, Ui.MUTED, false));
        r.addView(texts, Ui.weight(1));
        final Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setOnCheckedChangeListener(l);
        r.addView(sw);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
        list.addView(r, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Category header with an Add button, then its words as toggle chips. */
    private View category(final Suggest.Cat c) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 4));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(Ui.icon(this, c.icon, Ui.MUTED));
        head.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)));
        TextView t = Ui.text(this, c.title, 15, Ui.LIGHT, true);
        t.setPadding(Ui.dp(this, 12), 0, 0, 0);
        head.addView(t, Ui.weight(1));
        if (!c.smart) {
            TextView add = Ui.chip(this, "Add", 0);
            android.graphics.drawable.Drawable d = Ui.icon(this, R.drawable.ic_plus, Ui.ACCENT);
            int s = Ui.dp(this, 18);
            d.setBounds(0, 0, s, s);
            add.setCompoundDrawablesRelative(d, null, null, null);
            add.setCompoundDrawablePadding(Ui.dp(this, 4));
            add.setTextColor(Ui.ACCENT);
            head.addView(add);
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { addWord(c); }
            });
        }
        box.addView(head);
        ChipFlow flow = new ChipFlow(this);
        flow.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 4));
        fillFlow(flow, c);
        box.addView(flow, new LinearLayout.LayoutParams(-1, -2));
        box.setTag(flow);
        flow.setTag(c);
        return box;
    }

    private void fillFlow(final ChipFlow flow, final Suggest.Cat c) {
        flow.removeAllViews();
        List<Suggest.Item> items = Suggest.items(prefs, c);
        if (items.isEmpty()) {
            TextView empty = Ui.text(this, c.id.equals("doc.people") ? "Add hospital, doctor or patient names" : "Add your own words", 13, Ui.MUTED, false);
            flow.addView(empty);
            return;
        }
        for (final Suggest.Item it : items) {
            String label = c.smart && !it.custom
                    ? Suggest.smartLabel(it.text) + " · " + Suggest.smartValue(prefs, it.text, 3) : it.text;
            final TextView chip = Ui.chip(this, label, 0);
            style(chip, it.on, it.custom);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    it.on = !it.on;
                    Suggest.setOn(prefs, c, it.text, it.on);
                    style(chip, it.on, it.custom);
                }
            });
            if (it.custom) {
                chip.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        new AlertDialog.Builder(SuggestionsActivity.this)
                                .setTitle("Remove \u201c" + it.text + "\u201d?")
                                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int w) {
                                        Suggest.remove(prefs, c, it.text);
                                        chip.animate().alpha(0f).scaleX(0.6f).scaleY(0.6f).setStartDelay(0).setDuration(160)
                                                .withEndAction(new Runnable() { @Override public void run() { fillFlow(flow, c); } }).start();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        return true;
                    }
                });
            }
            flow.addView(chip);
        }
    }

    private void style(TextView chip, boolean on, boolean custom) {
        chip.setBackground(Ui.ripple(on ? Ui.round(Ui.accentA(0x30), Ui.dp(this, 18), Ui.ACCENT, Ui.dp(this, 1.5f))
                : Ui.round(0, Ui.dp(this, 18), 0x33E6F0F7, Ui.dp(this, 1)), true));
        chip.setTextColor(on ? Ui.LIGHT : Ui.MUTED);
        if (on) {
            android.graphics.drawable.Drawable d = Ui.icon(this, R.drawable.ic_check, Ui.ACCENT);
            int s = Ui.dp(this, 16);
            d.setBounds(0, 0, s, s);
            chip.setCompoundDrawablesRelative(d, null, null, null);
            chip.setCompoundDrawablePadding(Ui.dp(this, 5));
        } else {
            chip.setCompoundDrawablesRelative(null, null, null, null);
        }
        chip.setTypeface(null, custom ? android.graphics.Typeface.ITALIC : android.graphics.Typeface.NORMAL);
    }

    private void addWord(final Suggest.Cat c) {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        et.setHint(c.id.equals("rad.modality") ? "e.g. Myelogram" : c.id.equals("rad.body") ? "e.g. TMJ"
                : c.id.equals("rad.technique") ? "e.g. Delayed phase" : c.id.equals("doc.people") ? "e.g. City Hospital" : "New word");
        et.setTextColor(Ui.LIGHT);
        et.setHintTextColor(Ui.MUTED);
        FrameLayout f = new FrameLayout(this);
        int p = Ui.dp(this, 22);
        f.setPadding(p, Ui.dp(this, 8), p, 0);
        f.addView(et);
        final AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Add to " + c.title)
                .setView(f)
                .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int w) {
                        String t = et.getText().toString();
                        if (!Suggest.add(prefs, c, t) && t.trim().length() > 0) {
                            Toast.makeText(SuggestionsActivity.this, "\u201c" + t.trim() + "\u201d is already in the list and now switched on", Toast.LENGTH_SHORT).show();
                        }
                        View flow = list.findViewWithTag(c);
                        if (flow instanceof ChipFlow) {
                            fillFlow((ChipFlow) flow, c);
                            ChipFlow cf = (ChipFlow) flow;
                            if (cf.getChildCount() > 0) Ui.reveal(cf.getChildAt(cf.getChildCount() - 1), 0);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        et.requestFocus();
    }
}
