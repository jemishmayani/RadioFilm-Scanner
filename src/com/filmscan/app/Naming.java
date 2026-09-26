package com.filmscan.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Default file names and the suggestion chips offered while saving. */
public final class Naming {
    private Naming() {}

    /** Date styles. Index 0 = no date. Only characters that are valid in file names. */
    public static final String[] DATE_PATTERNS = {"", "d MMM yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd.MM.yyyy", "MMMM d, yyyy"};

    public static String formatDate(int style, Date d) {
        if (style <= 0 || style >= DATE_PATTERNS.length) return "";
        return new SimpleDateFormat(DATE_PATTERNS[style], Locale.getDefault()).format(d);
    }

    public static String dateStyleLabel(int style) {
        return style <= 0 ? "No date" : formatDate(style, new Date());
    }

    public static String time(Date d) {
        return new SimpleDateFormat("HH.mm", Locale.getDefault()).format(d);
    }

    /** Name built from the File naming settings, e.g. "Scan 25 Sep 2026". */
    public static String defaultName(Prefs p) {
        Date now = new Date();
        StringBuilder b = new StringBuilder(p.namePrefix().trim());
        String date = formatDate(p.nameDate(), now);
        if (date.length() > 0) append(b, date);
        if (p.nameTime()) append(b, time(now));
        String s = b.toString().trim();
        return s.isEmpty() ? "Scan" : s;
    }

    private static void append(StringBuilder b, String t) {
        if (b.length() > 0) b.append(' ');
        b.append(t);
    }

    /** Date pieces shown as single chips, like "September", "25", "2026". */
    public static List<String> dateParts() {
        Date now = new Date();
        List<String> l = new ArrayList<String>();
        l.add(new SimpleDateFormat("MMMM", Locale.getDefault()).format(now));
        l.add(new SimpleDateFormat("d", Locale.getDefault()).format(now));
        l.add(new SimpleDateFormat("yyyy", Locale.getDefault()).format(now));
        return l;
    }

    /** Full date/time styles for the Date menu. */
    public static List<String> dateChoices() {
        Date now = new Date();
        List<String> l = new ArrayList<String>();
        for (int i = 1; i < DATE_PATTERNS.length; i++) l.add(formatDate(i, now));
        l.add(new SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(now));
        l.add(time(now));
        return l;
    }

    public static final String[] SCAN_TYPES = {"CT", "MRI", "X-ray", "USG", "PET-CT", "Mammogram", "Report", "Contrast"};
    public static final String[] BODY_PARTS = {"Brain", "Head", "Chest", "Abdomen", "Pelvis", "Spine", "Neck", "Knee", "Shoulder", "Hand", "Foot"};

    public static List<String> userWords(Prefs p) {
        List<String> l = new ArrayList<String>();
        for (String w : p.nameWords().split(",")) {
            String t = w.trim();
            if (t.length() > 0 && !l.contains(t)) l.add(t);
        }
        return l;
    }

    public static List<String> recent(Prefs p) {
        List<String> l = new ArrayList<String>();
        for (String w : p.recentNames().split("\n")) if (w.trim().length() > 0) l.add(w.trim());
        return l;
    }

    public static void remember(Prefs p, String name) {
        if (name == null || name.trim().isEmpty()) return;
        List<String> l = recent(p);
        l.remove(name.trim());
        l.add(0, name.trim());
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(6, l.size()); i++) { if (i > 0) b.append('\n'); b.append(l.get(i)); }
        p.recentNames(b.toString());
    }

    /** Adds a word to the end of a name with a single space between. */
    public static String appendWord(String name, String word) {
        String n = name == null ? "" : name.replaceAll("\\s+$", "");
        return n.isEmpty() ? word : n + " " + word;
    }
}
