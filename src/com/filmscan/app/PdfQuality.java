package com.filmscan.app;

/** PDF file size presets: the longest side of each page image and its JPEG quality. */
public final class PdfQuality {
    private PdfQuality() {}

    public static final String[] NAMES = {"Original quality", "Balanced", "Smaller file"};
    public static final String[] NOTES = {"Full resolution", "Recommended: sharp and much smaller", "Best for messaging and email"};

    /** Longest side in pixels; 0 = no limit. */
    public static int maxSide(int preset) { return preset == 1 ? 2800 : preset == 2 ? 1800 : 0; }

    public static int jpegQuality(int preset, Prefs p) { return preset == 1 ? 85 : preset == 2 ? 70 : p.quality(); }
}
