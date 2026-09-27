package com.filmscan.app;

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;
import com.filmscan.core.Filters;
import com.filmscan.core.Geom;
import java.util.List;
import java.util.Locale;

/** Free space, and a size estimate for saving pages, so large batches don't fail half-way. */
public final class Storage {
    private Storage() {}

    public static long freeBytes() {
        try {
            return new StatFs(Environment.getExternalStorageDirectory().getPath()).getAvailableBytes();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Typical compressed bytes per pixel for scans (JPEG depends on quality; gray pages compress better). */
    static double bytesPerPixel(int format, int quality, boolean gray) {
        if (format == Exporter.PNG) return gray ? 1.3 : 2.4;
        if (quality >= 100) return gray ? 0.75 : 1.2;
        if (quality >= 95) return gray ? 0.4 : 0.6;
        if (quality >= 85) return gray ? 0.22 : 0.33;
        return gray ? 0.18 : 0.28;
    }

    /** Estimated total size of saving these pages in this format. */
    public static long estimate(Context c, List<Page> pages, int format) {
        Prefs prefs = App.get().prefs();
        long maxPx = Imaging.maxOutputPixels(c, prefs.maxMp());
        int quality = format == Exporter.PNG ? 100 : prefs.quality();
        if (format == Exporter.PDF) {
            quality = PdfQuality.jpegQuality(prefs.pdfQuality(), prefs);
            int cap = PdfQuality.maxSide(prefs.pdfQuality());
            if (cap > 0) maxPx = Math.min(maxPx, (long) cap * cap);
        }
        double total = 0;
        for (Page p : pages) {
            int dw = p.dispW(), dh = p.dispH();
            if (dw <= 0 || dh <= 0) continue;
            double[] sz = Geom.outputSize(Imaging.quadPx(p, dw, dh), dw, dh);
            double px = Math.min(maxPx, sz[0] * sz[1]);
            if (format == Exporter.PDF) {
                int cap = PdfQuality.maxSide(prefs.pdfQuality());
                double longSide = Math.max(sz[0], sz[1]);
                if (cap > 0 && longSide > cap) px = Math.min(px, sz[0] * sz[1] * (cap / longSide) * (cap / longSide));
            }
            total += px * bytesPerPixel(format, quality, Filters.isGray(p.filter)) + (format == Exporter.PDF ? 4096 : 0);
        }
        return (long) total;
    }

    public static int fileCount(List<Page> pages, int format, boolean split) {
        if (format == Exporter.PDF) return 1;
        int n = 0;
        for (Page p : pages) n += split ? p.gridR * p.gridC : 1;
        return n;
    }

    public static String human(long bytes) {
        if (bytes < 0) return "?";
        if (bytes < 1024L * 1024) return Math.max(1, bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.0f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.1f GB", bytes / 1073741824.0);
    }
}
