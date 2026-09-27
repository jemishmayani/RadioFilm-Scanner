package com.filmscan.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.filmscan.core.Filters;

/** User settings. */
public final class Prefs {
    private final SharedPreferences sp;

    Prefs(Context c) { sp = c.getSharedPreferences("filmscan", Context.MODE_PRIVATE); }

    public boolean autoCapture() { return sp.getBoolean("autoCapture", false); }
    public void autoCapture(boolean v) { sp.edit().putBoolean("autoCapture", v).apply(); }

    public boolean grid() { return sp.getBoolean("grid", false); }
    public void grid(boolean v) { sp.edit().putBoolean("grid", v).apply(); }

    public boolean snap() { return sp.getBoolean("snap", true); }
    public void snap(boolean v) { sp.edit().putBoolean("snap", v).apply(); }

    public boolean sound() { return sp.getBoolean("sound", true); }
    public void sound(boolean v) { sp.edit().putBoolean("sound", v).apply(); }

    public boolean batch() { return sp.getBoolean("batch", false); }
    public void batch(boolean v) { sp.edit().putBoolean("batch", v).apply(); }

    /** 0 off, 1 auto, 2 on, 3 torch */
    public int flash() { return sp.getInt("flash", 0); }
    public void flash(int v) { sp.edit().putInt("flash", v).apply(); }

    public int lastFilter() { return sp.getInt("lastFilter", Filters.AUTO); }
    public void lastFilter(int v) { sp.edit().putInt("lastFilter", v).apply(); }

    /** JPEG quality for saved images. */
    public int quality() { return sp.getInt("quality", 95); }
    public void quality(int v) { sp.edit().putInt("quality", v).apply(); }

    /** Output size limit in megapixels, 0 = full resolution. */
    public int maxMp() { return sp.getInt("maxMp", 0); }
    public void maxMp(int v) { sp.edit().putInt("maxMp", v).apply(); }

    /** 0 = JPEG, 1 = PNG, 2 = PDF */
    public int format() { return sp.getInt("format", 0); }
    public void format(int v) { sp.edit().putInt("format", v).apply(); }

    public boolean splitGrid() { return sp.getBoolean("splitGrid", true); }
    public void splitGrid(boolean v) { sp.edit().putBoolean("splitGrid", v).apply(); }

    // ---------------------------------------------------------------- saving & naming

    /** Preferred save format: -1 ask every time, else Exporter.JPEG / PNG / PDF. */
    public int saveAs() { return sp.getInt("saveAs", -1); }
    public void saveAs(int v) { sp.edit().putInt("saveAs", v).apply(); }

    public String namePrefix() { return sp.getString("namePrefix", "Scan"); }
    public void namePrefix(String v) { sp.edit().putString("namePrefix", v).apply(); }

    /** Index into Naming.DATE_FORMATS; 0 = no date. */
    public int nameDate() { return sp.getInt("nameDate", 1); }
    public void nameDate(int v) { sp.edit().putInt("nameDate", v).apply(); }

    public boolean nameTime() { return sp.getBoolean("nameTime", false); }
    public void nameTime(boolean v) { sp.edit().putBoolean("nameTime", v).apply(); }

    public boolean nameSuggest() { return sp.getBoolean("nameSuggest", true); }
    public void nameSuggest(boolean v) { sp.edit().putBoolean("nameSuggest", v).apply(); }

    /** The user's own suggestion words, comma separated (hospital, doctor, patient...). */
    public String nameWords() { return sp.getString("nameWords", ""); }
    public void nameWords(String v) { sp.edit().putString("nameWords", v).apply(); }

    /** Recently used names, newest first, separated by newlines. */
    public String recentNames() { return sp.getString("recentNames", ""); }
    public void recentNames(String v) { sp.edit().putString("recentNames", v).apply(); }

    /** Saved scan currently being edited, or null. */
    public String editingId() { return sp.getString("editingId", null); }
    public void editingId(String v) { sp.edit().putString("editingId", v).apply(); }

    /** Accent colour index into Ui.ACCENTS. */
    public int accent() { return sp.getInt("accent", 0); }
    public void accent(int v) { sp.edit().putInt("accent", v).apply(); }

    /** Name suggestion sets and their customised items, as JSON (see Suggest). */
    public String suggestState() { return sp.getString("suggest", "{}"); }
    public void suggestState(String v) { sp.edit().putString("suggest", v).apply(); }

    public boolean nameShowDate() { return sp.getBoolean("nameShowDate", true); }
    public void nameShowDate(boolean v) { sp.edit().putBoolean("nameShowDate", v).apply(); }

    public boolean nameShowRecent() { return sp.getBoolean("nameShowRecent", true); }
    public void nameShowRecent(boolean v) { sp.edit().putBoolean("nameShowRecent", v).apply(); }

    /** Suggestion set last shown on the save sheet. */
    public String lastPack() { return sp.getString("lastPack", ""); }
    public void lastPack(String v) { sp.edit().putString("lastPack", v).apply(); }

    /** Next number for the counter smart word. */
    public int nameCounter() { return sp.getInt("nameCounter", 1); }
    public void nameCounter(int v) { sp.edit().putInt("nameCounter", v).apply(); }

    public android.content.SharedPreferences raw() { return sp; }

    /** Extra viewfinder turn in quarter turns (0 = automatic), for devices that report odd camera data. */
    public int previewFix() { return sp.getInt("previewFix", 0); }
    public void previewFix(int v) { sp.edit().putInt("previewFix", v).apply(); }

    /** App identity: 0 = RadioFilm Scanner, 1 = DocScanner (name, icon and save folders). */
    public int identity() { return sp.getInt("identity", 0); }
    public void identity(int v) { sp.edit().putInt("identity", v).apply(); }

    // PDF options (apply only to PDF export)
    public int pdfPage() { return sp.getInt("pdfPage", 0); }          // PdfPage.FIT / A4 / A3 / LETTER
    public void pdfPage(int v) { sp.edit().putInt("pdfPage", v).apply(); }
    public int pdfMargin() { return sp.getInt("pdfMargin", 0); }      // none / small / medium / large
    public void pdfMargin(int v) { sp.edit().putInt("pdfMargin", v).apply(); }
    public int pdfQuality() { return sp.getInt("pdfQuality", 1); }    // 0 original, 1 balanced, 2 smaller
    public void pdfQuality(int v) { sp.edit().putInt("pdfQuality", v).apply(); }

    /** Saved scans: 0 newest, 1 oldest, 2 name A-Z, 3 name Z-A. */
    public int savedSort() { return sp.getInt("savedSort", 0); }
    public void savedSort(int v) { sp.edit().putInt("savedSort", v).apply(); }
}
