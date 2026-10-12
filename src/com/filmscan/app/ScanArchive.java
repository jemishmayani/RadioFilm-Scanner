package com.filmscan.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backs up every saved scan (original photos plus all edits) to one .zip file, and restores it,
 * for example when moving between the GitHub and Google Play versions or to a new phone.
 *
 * Layout: backup.json (list of scans), then scans/&lt;id&gt;/pages.json, its photos and thumb.jpg.
 * pages.json stores bare file names so a backup can be restored into any install.
 */
final class ScanArchive {
    static final int FORMAT = 1;
    private static final String TAG = "RadioFilm";

    private ScanArchive() { }

    // ================================================================== UI entry points

    static void askExport(Activity a, int req) {
        List<SavedStore.Entry> all = App.get().saved().list();
        if (all.isEmpty()) {
            Toast.makeText(a, "There are no saved scans to export yet.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        i.putExtra(Intent.EXTRA_TITLE, Branding.name() + " scans " + day + ".zip");
        try { a.startActivityForResult(i, req); } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No file app available", Toast.LENGTH_LONG).show();
        }
    }

    static void askImport(Activity a, int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try { a.startActivityForResult(i, req); } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No file app available", Toast.LENGTH_LONG).show();
        }
    }

    /** Short line for menus: how many scans and roughly how big a backup will be. */
    static String summary() {
        SavedStore s = App.get().saved();
        List<SavedStore.Entry> all = s.list();
        if (all.isEmpty()) return "No saved scans yet";
        long bytes = 0;
        for (SavedStore.Entry e : all) {
            File[] fs = e.dir.listFiles();
            if (fs != null) for (File f : fs) bytes += f.length();
        }
        return all.size() + (all.size() == 1 ? " scan" : " scans") + ", about " + Storage.human(bytes);
    }

    static void runExport(final Activity a, final Uri uri) {
        final App app = App.get();
        final AlertDialog dlg = CameraActivity.progressDialog(a, "Preparing backup…");
        app.export.execute(new Runnable() {
            @Override
            public void run() {
                String err = null;
                int n = 0;
                try {
                    OutputStream os = a.getContentResolver().openOutputStream(uri, "w");
                    if (os == null) throw new IOException("Could not open the file");
                    try {
                        n = write(app.saved(), os, new Progress() {
                            @Override
                            public void step(final int done, final int total) {
                                app.ui(new Runnable() {
                                    @Override public void run() { CameraActivity.setProgress(dlg, "Exporting scan " + Math.min(done + 1, total) + " of " + total + "…", done, total); }
                                });
                            }
                        });
                    } finally {
                        os.close();
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "backup export", t);
                    err = t.getMessage() == null ? t.toString() : t.getMessage();
                }
                final String fe = err;
                final int fn = n;
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        try { dlg.dismiss(); } catch (Exception ignored) { }
                        if (fe != null) {
                            new AlertDialog.Builder(a).setTitle("Export failed").setMessage(fe).setPositiveButton("OK", null).show();
                        } else {
                            new AlertDialog.Builder(a).setTitle("Scans exported")
                                    .setMessage(fn + (fn == 1 ? " scan was" : " scans were") + " saved to the backup file, with their original photos and all edits.\n\n"
                                            + "To restore them, install the app on the other phone (or the other version), then go to Settings → Import scans.\n\n"
                                            + "The file contains your scans unencrypted. Keep it somewhere private.")
                                    .setPositiveButton("OK", null).show();
                        }
                    }
                });
            }
        });
    }

    static void runImport(final Activity a, final Uri uri) {
        final App app = App.get();
        final AlertDialog dlg = CameraActivity.progressDialog(a, "Reading backup…");
        app.export.execute(new Runnable() {
            @Override
            public void run() {
                String err = null;
                int[] r = {0, 0};
                try {
                    InputStream in = a.getContentResolver().openInputStream(uri);
                    if (in == null) throw new IOException("Could not open the file");
                    try {
                        r = read(app.saved(), in, new Progress() {
                            @Override
                            public void step(final int done, final int total) {
                                app.ui(new Runnable() {
                                    @Override public void run() { CameraActivity.setProgress(dlg, "Importing scan " + Math.min(done + 1, total) + " of " + total + "…", done, total); }
                                });
                            }
                        });
                    } finally {
                        in.close();
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "backup import", t);
                    err = t.getMessage() == null ? t.toString() : t.getMessage();
                }
                final String fe = err;
                final int added = r[0], skipped = r[1];
                app.ui(new Runnable() {
                    @Override
                    public void run() {
                        try { dlg.dismiss(); } catch (Exception ignored) { }
                        app.notifySaved();
                        if (fe != null) {
                            new AlertDialog.Builder(a).setTitle("Import failed").setMessage(fe
                                    + (added > 0 ? "\n\n" + added + " scan(s) were imported before the problem." : "")).setPositiveButton("OK", null).show();
                            return;
                        }
                        String msg = added == 0 && skipped == 0 ? "The backup had no scans."
                                : (added + (added == 1 ? " scan imported." : " scans imported."))
                                + (skipped > 0 ? "\n" + skipped + (skipped == 1 ? " was" : " were") + " already here and skipped." : "");
                        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle("Import finished").setMessage(msg).setNegativeButton("OK", null);
                        if (added > 0 && !(a instanceof SavedActivity)) {
                            b.setPositiveButton("Open saved scans", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface d, int w) { a.startActivity(new Intent(a, SavedActivity.class)); }
                            });
                        }
                        b.show();
                    }
                });
            }
        });
    }

    // ================================================================== format

    interface Progress { void step(int done, int total); }

    /** Writes every saved scan; returns how many were written. */
    static int write(SavedStore store, OutputStream os, Progress pr) throws Exception {
        List<SavedStore.Entry> all = store.list();
        ZipOutputStream z = new ZipOutputStream(new java.io.BufferedOutputStream(os, 1 << 16));
        JSONObject man = new JSONObject();
        man.put("app", "RadioFilm Scanner");
        man.put("kind", "scans");
        man.put("format", FORMAT);
        man.put("created", System.currentTimeMillis());
        JSONArray list = new JSONArray();
        for (SavedStore.Entry e : all) {
            JSONObject o = new JSONObject();
            o.put("id", e.id); o.put("name", e.name); o.put("format", e.format); o.put("where", e.where);
            o.put("time", e.time); o.put("pages", e.pages); o.put("files", e.files);
            list.put(o);
        }
        man.put("scans", list);
        z.setLevel(Deflater.DEFAULT_COMPRESSION);
        putBytes(z, "backup.json", man.toString(1).getBytes("UTF-8"));
        int n = 0;
        for (SavedStore.Entry e : all) {
            if (pr != null) pr.step(n, all.size());
            List<Page> pages = store.pages(e);
            JSONArray pj = new JSONArray();
            Map<String, String> names = new HashMap<String, String>();
            for (Page p : pages) {
                String base = names.get(p.file);
                if (base == null) {
                    base = new File(p.file).getName();
                    names.put(p.file, base);
                }
                Page c = p.copy();
                c.file = base;
                pj.put(c.toJson());
            }
            String dir = "scans/" + e.id + "/";
            z.setLevel(Deflater.DEFAULT_COMPRESSION);
            putBytes(z, dir + "pages.json", pj.toString().getBytes("UTF-8"));
            z.setLevel(Deflater.NO_COMPRESSION);   // photos are already compressed
            for (Map.Entry<String, String> f : names.entrySet()) putFile(z, dir + f.getValue(), new File(f.getKey()));
            if (e.thumb().isFile()) putFile(z, dir + "thumb.jpg", e.thumb());
            n++;
        }
        z.finish();
        z.flush();
        return n;
    }

    /** Restores scans from a backup; returns {imported, already present}. */
    static int[] read(SavedStore store, InputStream in, Progress pr) throws Exception {
        File stage = new File(store.root(), ".import");
        SavedStore.deleteDir(stage);
        if (!stage.mkdirs()) throw new IOException("Could not prepare a folder for the import");
        int added = 0, skipped = 0;
        try {
            ZipInputStream z = new ZipInputStream(new java.io.BufferedInputStream(in, 1 << 16));
            JSONArray scans = null;
            ZipEntry ze;
            byte[] buf = new byte[1 << 16];
            while ((ze = z.getNextEntry()) != null) {
                String name = ze.getName();
                if (ze.isDirectory()) continue;
                if (name.equals("backup.json")) {
                    JSONObject man = new JSONObject(new String(readAll(z), "UTF-8"));
                    if (!"scans".equals(man.optString("kind"))) throw new IOException("This file is not a scans backup from this app.");
                    if (man.optInt("format") > FORMAT) throw new IOException("This backup was made by a newer version of the app. Please update first.");
                    scans = man.getJSONArray("scans");
                    continue;
                }
                String[] parts = name.split("/");
                // only scans/<id>/<file>; anything else (or anything trying to leave the folder) is ignored
                if (parts.length != 3 || !parts[0].equals("scans") || !safeId(parts[1]) || !safeFile(parts[2])) continue;
                File d = new File(stage, parts[1]);
                d.mkdirs();
                OutputStream o = new FileOutputStream(new File(d, parts[2]));
                try {
                    int r;
                    while ((r = z.read(buf)) > 0) o.write(buf, 0, r);
                } finally {
                    o.close();
                }
            }
            if (scans == null) throw new IOException("This file is not a scans backup from this app.");
            for (int i = 0; i < scans.length(); i++) {
                if (pr != null) pr.step(i, scans.length());
                JSONObject o = scans.getJSONObject(i);
                String id = o.optString("id");
                long time = o.optLong("time");
                File d = new File(stage, id);
                if (!safeId(id) || !new File(d, "pages.json").isFile()) continue;
                if (store.has(id, time)) { skipped++; continue; }
                store.adopt(id, o.optString("name"), o.optString("format"), o.optString("where"), time, o.optInt("files"), d);
                added++;
            }
        } finally {
            SavedStore.deleteDir(stage);
        }
        return new int[]{added, skipped};
    }

    static boolean safeId(String s) { return s != null && s.matches("[A-Za-z0-9-]{1,40}"); }

    static boolean safeFile(String s) { return s != null && s.matches("[A-Za-z0-9_-][A-Za-z0-9._-]{0,99}") && !s.contains(".."); }

    private static void putBytes(ZipOutputStream z, String name, byte[] b) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(b);
        z.closeEntry();
    }

    private static void putFile(ZipOutputStream z, String name, File f) throws IOException {
        if (!f.isFile()) return;
        ZipEntry e = new ZipEntry(name);
        e.setTime(f.lastModified());
        z.putNextEntry(e);
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[1 << 16];
            int r;
            while ((r = in.read(buf)) > 0) z.write(buf, 0, r);
        } finally {
            in.close();
        }
        z.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) > 0) {
            bo.write(buf, 0, r);
            if (bo.size() > 8 << 20) throw new IOException("The backup list is too large");
        }
        return bo.toByteArray();
    }
}
