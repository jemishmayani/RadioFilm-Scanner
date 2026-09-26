package com.filmscan.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Name suggestion sets. Each set has categories of words; every word can be switched on or off
 * and users can add their own. Configured once in Settings, shown on the save and rename sheets.
 */
public final class Suggest {
    private Suggest() {}

    public static final class Cat {
        public final String id, title;
        public final int icon;
        public final String[] builtins;
        final Set<String> defaultOn;
        public final boolean smart;

        Cat(String id, String title, int icon, String[] builtins, String[] defaultOn, boolean smart) {
            this.id = id; this.title = title; this.icon = icon; this.builtins = builtins; this.smart = smart;
            this.defaultOn = new HashSet<String>(Arrays.asList(defaultOn == null ? builtins : defaultOn));
        }
    }

    public static final class Pack {
        public final String id, title, subtitle;
        public final int icon;
        public final Cat[] cats;

        Pack(String id, String title, String subtitle, int icon, Cat... cats) {
            this.id = id; this.title = title; this.subtitle = subtitle; this.icon = icon; this.cats = cats;
        }
    }

    public static final class Item {
        public final String text;
        public boolean on;
        public final boolean custom;

        Item(String text, boolean on, boolean custom) { this.text = text; this.on = on; this.custom = custom; }
    }

    private static String[] a(String... s) { return s; }

    public static final Pack[] PACKS = {
            new Pack("radiology", "Radiology", "Modality, body part, technique, side and view", R.drawable.ic_radiology,
                    new Cat("rad.modality", "Modality", R.drawable.ic_radiology,
                            a("CT", "MRI", "X-ray", "USG", "Doppler", "PET-CT", "HRCT", "Mammogram", "CT angio", "MR angio",
                                    "MRCP", "Fluoroscopy", "IVP", "HSG", "DEXA", "Nuclear scan", "Bone scan", "OPG", "CBCT", "Echo"),
                            a("CT", "MRI", "X-ray", "USG", "Doppler", "PET-CT", "HRCT", "Mammogram"), false),
                    new Cat("rad.body", "Body part", R.drawable.ic_body,
                            a("Brain", "Head", "Orbit", "PNS", "Temporal bone", "Face", "Neck", "Chest", "Heart", "Abdomen",
                                    "Pelvis", "KUB", "Whole abdomen", "C-spine", "D-spine", "LS-spine", "Whole spine", "Shoulder",
                                    "Elbow", "Wrist", "Hand", "Hip", "Knee", "Ankle", "Foot", "Breast", "Whole body"),
                            a("Brain", "Head", "Neck", "Chest", "Abdomen", "Pelvis", "LS-spine", "C-spine", "Knee", "Shoulder"), false),
                    new Cat("rad.technique", "Technique", R.drawable.ic_contrast,
                            a("Plain", "Contrast", "Plain + contrast", "Angiography", "Diffusion", "Perfusion", "Dynamic",
                                    "3D", "Screening", "Follow-up", "Pre-op", "Post-op"),
                            a("Plain", "Contrast", "Plain + contrast", "Follow-up"), false),
                    new Cat("rad.side", "Side and view", R.drawable.ic_sides,
                            a("Left", "Right", "Bilateral", "AP", "PA", "Lateral", "Oblique", "Axial", "Coronal", "Sagittal"),
                            a("Left", "Right", "Bilateral", "AP", "Lateral"), false),
                    new Cat("rad.material", "Material", R.drawable.ic_film,
                            a("Film", "Report", "Films and report", "CD", "Old films", "Comparison"),
                            a("Film", "Report"), false)),
            new Pack("document", "Documents", "Medical and everyday paperwork", R.drawable.ic_doc,
                    new Cat("doc.type", "Document", R.drawable.ic_doc,
                            a("Report", "Prescription", "Lab report", "Discharge summary", "Bill", "Receipt", "Invoice",
                                    "Insurance", "Certificate", "Letter", "Form", "Consent", "ID card", "Referral"),
                            a("Report", "Prescription", "Lab report", "Discharge summary", "Bill", "Receipt", "Certificate", "Form"), false),
                    new Cat("doc.people", "People and places", R.drawable.ic_account, a(), a(), false),
                    new Cat("doc.details", "Details", R.drawable.ic_details,
                            a("Original", "Copy", "Signed", "Front", "Back", "Draft", "Final", "Page"),
                            a("Original", "Copy", "Signed", "Front", "Back"), false)),
            new Pack("general", "General", "Notes, study, receipts, plus smart words", R.drawable.ic_tag,
                    new Cat("gen.smart", "Smart words", R.drawable.ic_lightning,
                            a("weekday", "time", "monthyear", "pages", "counter"), null, true),
                    new Cat("gen.topic", "Topic", R.drawable.ic_book,
                            a("Notes", "Lecture", "Assignment", "Book", "Whiteboard", "Receipt", "Warranty", "Manual",
                                    "Recipe", "Ticket", "Letter", "Journal"),
                            a("Notes", "Lecture", "Assignment", "Whiteboard", "Receipt", "Ticket", "Book"), false),
                    new Cat("gen.tags", "My tags", R.drawable.ic_star, a(), a(), false))
    };

    // ------------------------------------------------------------------ smart words

    public static String smartLabel(String id) {
        if ("weekday".equals(id)) return "Weekday";
        if ("time".equals(id)) return "Time";
        if ("monthyear".equals(id)) return "Month and year";
        if ("pages".equals(id)) return "Page count";
        if ("counter".equals(id)) return "Counter";
        return id;
    }

    public static String smartValue(Prefs p, String id, int pages) {
        Date now = new Date();
        if ("weekday".equals(id)) return new SimpleDateFormat("EEEE", Locale.getDefault()).format(now);
        if ("time".equals(id)) return Naming.time(now);
        if ("monthyear".equals(id)) return new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(now);
        if ("pages".equals(id)) return pages == 1 ? "1 page" : pages + " pages";
        if ("counter".equals(id)) return counter(p);
        return id;
    }

    public static String counter(Prefs p) { return String.format(Locale.US, "%03d", p.nameCounter()); }

    /** After saving: if the counter word was used, move on to the next number. */
    public static void afterSave(Prefs p, String name) {
        if (name != null && name.contains(counter(p))) p.nameCounter(p.nameCounter() + 1);
    }

    // ------------------------------------------------------------------ state

    private static JSONObject state(Prefs p) {
        JSONObject o;
        try { o = new JSONObject(p.suggestState()); } catch (Exception e) { o = new JSONObject(); }
        if (!o.optBoolean("migrated")) {
            // carry the old comma-separated "My suggestion words" over into People and places
            try {
                o.put("migrated", true);
                JSONObject cats = o.optJSONObject("cats");
                if (cats == null) { cats = new JSONObject(); o.put("cats", cats); }
                JSONArray custom = new JSONArray();
                for (String w : Naming.userWords(p)) custom.put(new JSONObject().put("t", w).put("on", true));
                if (custom.length() > 0) cats.put("doc.people", new JSONObject().put("custom", custom));
                p.suggestState(o.toString());
            } catch (Exception ignored) { }
        }
        return o;
    }

    public static boolean packOn(Prefs p, Pack pk) {
        JSONArray a = state(p).optJSONArray("packs");
        if (a == null) return "radiology".equals(pk.id);
        for (int i = 0; i < a.length(); i++) if (pk.id.equals(a.optString(i))) return true;
        return false;
    }

    public static void setPackOn(Prefs p, Pack pk, boolean on) {
        try {
            JSONObject o = state(p);
            JSONArray out = new JSONArray();
            for (Pack x : PACKS) if (x == pk ? on : packOn(p, x)) out.put(x.id);
            o.put("packs", out);
            p.suggestState(o.toString());
        } catch (Exception ignored) { }
    }

    public static List<Pack> enabledPacks(Prefs p) {
        List<Pack> l = new ArrayList<Pack>();
        for (Pack pk : PACKS) if (packOn(p, pk)) l.add(pk);
        return l;
    }

    public static List<Item> items(Prefs p, Cat c) {
        List<Item> out = new ArrayList<Item>();
        JSONObject cats = state(p).optJSONObject("cats");
        JSONObject cs = cats == null ? null : cats.optJSONObject(c.id);
        Set<String> on = c.defaultOn;
        if (cs != null && cs.has("on")) {
            on = new HashSet<String>();
            JSONArray a = cs.optJSONArray("on");
            for (int i = 0; a != null && i < a.length(); i++) on.add(a.optString(i));
        }
        for (String b : c.builtins) out.add(new Item(b, on.contains(b), false));
        JSONArray custom = cs == null ? null : cs.optJSONArray("custom");
        for (int i = 0; custom != null && i < custom.length(); i++) {
            JSONObject it = custom.optJSONObject(i);
            if (it != null && it.optString("t").length() > 0) out.add(new Item(it.optString("t"), it.optBoolean("on", true), true));
        }
        return out;
    }

    /** Words to show on the sheet for one category (smart words resolved to today's values). */
    public static List<String> activeWords(Prefs p, Cat c, int pages) {
        List<String> l = new ArrayList<String>();
        for (Item it : items(p, c)) if (it.on) l.add(c.smart && !it.custom ? smartValue(p, it.text, pages) : it.text);
        return l;
    }

    private static void writeCat(Prefs p, Cat c, List<Item> items) {
        try {
            JSONObject o = state(p);
            JSONObject cats = o.optJSONObject("cats");
            if (cats == null) { cats = new JSONObject(); o.put("cats", cats); }
            JSONArray on = new JSONArray(), custom = new JSONArray();
            for (Item it : items) {
                if (it.custom) custom.put(new JSONObject().put("t", it.text).put("on", it.on));
                else if (it.on) on.put(it.text);
            }
            cats.put(c.id, new JSONObject().put("on", on).put("custom", custom));
            p.suggestState(o.toString());
        } catch (Exception ignored) { }
    }

    public static void setOn(Prefs p, Cat c, String text, boolean on) {
        List<Item> items = items(p, c);
        for (Item it : items) if (it.text.equals(text)) it.on = on;
        writeCat(p, c, items);
    }

    /** Adds a custom word. Returns false if it is empty or already there. */
    public static boolean add(Prefs p, Cat c, String text) {
        String t = text == null ? "" : text.trim().replaceAll("[\\\\/:*?\"<>|]", "");
        if (t.isEmpty()) return false;
        List<Item> items = items(p, c);
        for (Item it : items) if (it.text.equalsIgnoreCase(t)) { it.on = true; writeCat(p, c, items); return false; }
        items.add(new Item(t, true, true));
        writeCat(p, c, items);
        return true;
    }

    public static void remove(Prefs p, Cat c, String text) {
        List<Item> items = items(p, c);
        for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).custom && items.get(i).text.equals(text)) items.remove(i);
        writeCat(p, c, items);
    }

    public static void reset(Prefs p) {
        p.suggestState(new JSONObject().toString());
        try { p.suggestState(new JSONObject().put("migrated", true).toString()); } catch (Exception ignored) { }
    }

    public static String summary(Prefs p) {
        StringBuilder b = new StringBuilder();
        for (Pack pk : enabledPacks(p)) { if (b.length() > 0) b.append(" · "); b.append(pk.title); }
        return b.length() == 0 ? "Date and recent names only" : b.toString();
    }
}
