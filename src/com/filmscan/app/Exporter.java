package com.filmscan.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import com.filmscan.core.Filters;
import com.filmscan.core.PdfWriter;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Renders pages at full resolution and writes them to the gallery, Downloads or a share cache. */
public final class Exporter {
    private Exporter() {}

    public static final int JPEG = 0, PNG = 1, PDF = 2;
    public static final String FOLDER = "RadioFilm Scanner";

    public interface Progress { void step(int done, int total); }

    public static final class Result {
        public final List<Uri> uris = new ArrayList<Uri>();   // content uris (share) or gallery uris
        public final List<String> mimes = new ArrayList<String>();
        public int files;
        public String where = "";
        public String error;
        public int reducedPages; // pages that had to be rendered smaller than full resolution
    }

    public static Result run(Context ctx, List<Page> pages, int format, boolean split, String baseName,
                             boolean forShare, Progress progress) {
        Result res = new Result();
        Prefs prefs = App.get().prefs();
        int quality = format == PNG ? 100 : prefs.quality();
        long maxPx = Imaging.maxOutputPixels(ctx, prefs.maxMp());
        String safe = sanitize(baseName);
        File shareDir = new File(ctx.getCacheDir(), "share");
        if (forShare) {
            shareDir.mkdirs();
            File[] old = shareDir.listFiles();
            if (old != null) for (File f : old) f.delete();
        }
        int total = 0;
        for (Page p : pages) total += split ? p.gridR * p.gridC : 1;
        int done = 0;
        PdfWriter pdf = null;
        OutputStream pdfOut = null;
        Uri pdfUri = null;
        File pdfFile = null;
        try {
            if (format == PDF) {
                String name = safe + ".pdf";
                if (forShare) {
                    pdfFile = new File(shareDir, name);
                    pdfOut = new FileOutputStream(pdfFile);
                } else if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                    v.put("relative_path", Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
                    v.put("is_pending", 1);
                    pdfUri = ctx.getContentResolver().insert(Uri.parse("content://media/external/downloads"), v);
                    if (pdfUri == null) throw new IOException("Could not create the PDF in Downloads");
                    pdfOut = ctx.getContentResolver().openOutputStream(pdfUri);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER);
                    dir.mkdirs();
                    pdfFile = unique(dir, safe, ".pdf");
                    pdfOut = new FileOutputStream(pdfFile);
                }
                pdf = new PdfWriter(new BufferedOutputStream(pdfOut, 1 << 16));
            }
            for (int pi = 0; pi < pages.size(); pi++) {
                Page p = pages.get(pi).copy();
                Bitmap full = null;
                long limit = maxPx;
                for (int attempt = 0; attempt < 3 && full == null; attempt++) {
                    try {
                        full = Imaging.renderFull(p, limit);
                        if (full != null) {
                            Imaging.applyFilter(full, p);
                            Imaging.drawRedactions(full, p);
                        }
                    } catch (OutOfMemoryError e) {
                        if (full != null) full.recycle();
                        full = null;
                        limit /= 2;
                        res.reducedPages++;
                        System.gc();
                    }
                }
                if (full == null) throw new IOException("Page " + (pi + 1) + " could not be read");
                List<Bitmap> parts = new ArrayList<Bitmap>();
                int rows = split ? p.gridR : 1, cols = split ? p.gridC : 1;
                if (rows * cols == 1) parts.add(full);
                else parts.addAll(splitGrid(full, rows, cols));
                for (int k = 0; k < parts.size(); k++) {
                    Bitmap b = parts.get(k);
                    String name = safe + (pages.size() > 1 ? "_p" + (pi + 1) : "")
                            + (parts.size() > 1 ? "_" + (k / cols + 1) + "x" + (k % cols + 1) : "");
                    if (format == PDF) {
                        ByteArrayOutputStream bo = new ByteArrayOutputStream(b.getWidth() * b.getHeight() / 3);
                        b.compress(Bitmap.CompressFormat.JPEG, quality, bo);
                        pdf.addJpegPage(bo.toByteArray(), b.getWidth(), b.getHeight(), Filters.isGray(p.filter));
                    } else if (forShare) {
                        File f = new File(shareDir, name + (format == PNG ? ".png" : ".jpg"));
                        writeBitmap(b, format, quality, new FileOutputStream(f));
                        res.uris.add(ShareProvider.uriFor(ctx, f));
                        res.mimes.add(format == PNG ? "image/png" : "image/jpeg");
                        res.files++;
                    } else {
                        Uri u = saveToGallery(ctx, b, name, format, quality);
                        res.uris.add(u);
                        res.mimes.add(format == PNG ? "image/png" : "image/jpeg");
                        res.files++;
                    }
                    if (b != full) b.recycle();
                    done++;
                    if (progress != null) progress.step(done, total);
                }
                full.recycle();
            }
            if (pdf != null) {
                pdf.finish(baseName);
                pdfOut.close();
                pdfOut = null;
                res.files = 1;
                res.mimes.add("application/pdf");
                if (forShare) {
                    res.uris.add(ShareProvider.uriFor(ctx, pdfFile));
                } else if (pdfUri != null) {
                    ContentValues v = new ContentValues();
                    v.put("is_pending", 0);
                    ctx.getContentResolver().update(pdfUri, v, null, null);
                    res.uris.add(pdfUri);
                } else {
                    MediaScannerConnection.scanFile(ctx, new String[]{pdfFile.getPath()}, new String[]{"application/pdf"}, null);
                    res.uris.add(Uri.fromFile(pdfFile));
                }
                res.where = "Download/" + FOLDER;
            } else {
                res.where = "Pictures/" + FOLDER;
            }
        } catch (Throwable t) {
            Log.e("FilmScan", "export failed", t);
            res.error = t instanceof OutOfMemoryError ? "Not enough memory. Lower the output size in Settings and try again."
                    : (t.getMessage() != null ? t.getMessage() : t.toString());
            if (pdfOut != null) try { pdfOut.close(); } catch (IOException ignored) { }
            if (pdfUri != null) try { ctx.getContentResolver().delete(pdfUri, null, null); } catch (Exception ignored) { }
        }
        return res;
    }

    static List<Bitmap> splitGrid(Bitmap b, int rows, int cols) {
        List<Bitmap> out = new ArrayList<Bitmap>();
        int w = b.getWidth(), h = b.getHeight();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int x0 = Math.round(c * w / (float) cols), x1 = Math.round((c + 1) * w / (float) cols);
                int y0 = Math.round(r * h / (float) rows), y1 = Math.round((r + 1) * h / (float) rows);
                out.add(Bitmap.createBitmap(b, x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)));
            }
        }
        return out;
    }

    private static void writeBitmap(Bitmap b, int format, int quality, OutputStream os) throws IOException {
        OutputStream bo = new BufferedOutputStream(os, 1 << 16);
        try {
            boolean ok = b.compress(format == PNG ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, quality, bo);
            if (!ok) throw new IOException("Could not encode the image");
        } finally {
            bo.close();
        }
    }

    private static Uri saveToGallery(Context ctx, Bitmap b, String name, int format, int quality) throws IOException {
        String ext = format == PNG ? ".png" : ".jpg";
        String mime = format == PNG ? "image/png" : "image/jpeg";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ext);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put("relative_path", Environment.DIRECTORY_PICTURES + "/" + FOLDER);
            v.put("is_pending", 1);
            Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Could not create the image in Pictures");
            try {
                writeBitmap(b, format, quality, cr.openOutputStream(uri));
            } catch (IOException e) {
                cr.delete(uri, null, null);
                throw e;
            }
            v.clear();
            v.put("is_pending", 0);
            cr.update(uri, v, null, null);
            return uri;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Could not create Pictures/" + FOLDER);
        File f = unique(dir, name, ext);
        writeBitmap(b, format, quality, new FileOutputStream(f));
        MediaScannerConnection.scanFile(ctx, new String[]{f.getPath()}, new String[]{mime}, null);
        return Uri.fromFile(f);
    }

    private static File unique(File dir, String name, String ext) {
        File f = new File(dir, name + ext);
        int i = 2;
        while (f.exists()) f = new File(dir, name + " (" + (i++) + ")" + ext);
        return f;
    }

    public static String sanitize(String s) {
        String r = s == null ? "" : s.trim().replaceAll("[\\\\/:*?\"<>|\\n\\r\\t]", "_");
        if (r.length() > 80) r = r.substring(0, 80);
        return r.isEmpty() ? "Scan" : r;
    }
}
