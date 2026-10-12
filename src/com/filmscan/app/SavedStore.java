package com.filmscan.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Batches the user has saved. Each keeps its original photos and every edit, so it can be viewed,
 * shared again or reopened for editing later. Files live in filesDir/saved/&lt;id&gt;/.
 */
public final class SavedStore {
    public static final class Entry {
        public String id, name, format, where;
        public long time;
        public int pages, files;
        File dir;

        public File thumb() { return new File(dir, "thumb.jpg"); }
    }

    private final File root, index;
    private final ArrayList<Entry> entries = new ArrayList<Entry>();

    SavedStore(Context c) {
        root = new File(c.getFilesDir(), "saved");
        root.mkdirs();
        index = new File(root, "index.json");
        load();
    }

    public synchronized List<Entry> list() { return new ArrayList<Entry>(entries); }

    public synchronized Entry find(String id) {
        for (Entry e : entries) if (e.id.equals(id)) return e;
        return null;
    }

    public static String formatLabel(int format) {
        return format == Exporter.PDF ? "PDF" : format == Exporter.PNG ? "PNG" : "JPEG";
    }

    /** Moves the pages' photos out of the working session into a new saved entry. */
    public Entry add(String name, int format, List<Page> pages, Exporter.Result r) throws IOException {
        Entry e = new Entry();
        e.id = UUID.randomUUID().toString().substring(0, 12);
        e.name = name == null || name.trim().isEmpty() ? "Scan" : name.trim();
        e.format = formatLabel(format);
        e.where = r.where;
        e.time = System.currentTimeMillis();
        e.pages = pages.size();
        e.files = r.files;
        e.dir = new File(root, e.id);
        if (!e.dir.mkdirs()) throw new IOException("Could not create the saved folder");
        Map<String, String> moved = new HashMap<String, String>();
        JSONArray arr = new JSONArray();
        try {
            for (Page p : pages) {
                String dst = moved.get(p.file);
                if (dst == null) {
                    File src = new File(p.file);
                    File to = new File(e.dir, src.getName());
                    if (!src.renameTo(to)) { copy(src, to); src.delete(); }
                    dst = to.getPath();
                    moved.put(p.file, dst);
                }
                Page c = p.copy();
                c.file = dst;
                arr.put(c.toJson());
            }
            writeAtomic(new File(e.dir, "pages.json"), arr.toString());
        } catch (Exception ex) {
            throw new IOException(ex.getMessage());
        }
        writeThumb(e, pages(e));
        synchronized (this) {
            entries.add(0, e);
            saveIndex();
        }
        return e;
    }

    public List<Page> pages(Entry e) {
        List<Page> out = new ArrayList<Page>();
        try {
            String text = read(new File(e.dir, "pages.json")).trim();
            JSONArray a = text.startsWith("[") ? new JSONArray(text) : new JSONObject(text).getJSONArray("pages");
            for (int i = 0; i < a.length(); i++) {
                Page p = Page.fromJson(a.getJSONObject(i));
                if (new File(p.file).exists()) out.add(p);
            }
        } catch (Exception ex) {
            Log.w("RadioFilm", "saved pages", ex);
        }
        return out;
    }

    public synchronized void rename(Entry e, String name) {
        if (name == null || name.trim().isEmpty()) return;
        e.name = name.trim();
        saveIndex();
    }

    public synchronized void delete(Entry e) {
        if (!entries.contains(e) && !e.dir.exists()) return;
        entries.remove(e);
        saveIndex();
        File[] fs = e.dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        e.dir.delete();
    }

    /** After re-saving an edited scan: new name, format and export details; moves it to the top. */
    public void update(Entry e, String name, int format, Exporter.Result r) {
        List<Page> ps = pages(e);
        writeThumb(e, ps);
        synchronized (this) {
            if (name != null && !name.trim().isEmpty()) e.name = name.trim();
            e.format = formatLabel(format);
            e.where = r.where;
            e.files = r.files;
            e.pages = ps.size();
            e.time = System.currentTimeMillis();
            entries.remove(e);
            entries.add(0, e);
            saveIndex();
        }
    }

    /** Refreshes the cover and page count after in-place edits; removes a scan left with no pages. */
    public void refresh(Entry e) {
        List<Page> ps = pages(e);
        if (ps.isEmpty()) { delete(e); App.get().notifySaved(); return; }
        writeThumb(e, ps);
        synchronized (this) {
            e.pages = ps.size();
            saveIndex();
        }
        App.get().notifySaved();
    }

    private void writeThumb(Entry e, List<Page> ps) {
        try {
            if (ps.isEmpty()) return;
            Bitmap t = Imaging.renderFinished(ps.get(0), 360);
            if (t == null) return;
            File tmp = new File(e.dir, "thumb.tmp");
            OutputStream os = new FileOutputStream(tmp);
            t.compress(Bitmap.CompressFormat.JPEG, 88, os);
            os.close();
            tmp.renameTo(e.thumb());
        } catch (Throwable t) {
            Log.w("RadioFilm", "thumb", t);
        }
    }

    /** Cache key that changes whenever the cover is rewritten. */
    public static String thumbKey(Entry e) { return "saved:" + e.id + ":" + e.thumb().lastModified(); }

    // ------------------------------------------------------------------ backup / restore

    /** The folder all saved scans live in (used by backups to stage imported scans). */
    File root() { return root; }

    /** True if this exact scan (same id, saved at the same time) is already here. */
    public synchronized boolean has(String id, long time) {
        for (Entry e : entries) if (e.id.equals(id) && e.time == time) return true;
        return false;
    }

    /**
     * Adopts a scan restored from a backup. {@code staged} holds its photos and a pages.json whose
     * file names are bare; they are made to point at the scan's new folder. Keeps the original id
     * when it is free, so importing the same backup twice can be detected.
     */
    public Entry adopt(String id, String name, String format, String where, long time, int files, File staged) throws IOException {
        Entry e = new Entry();
        synchronized (this) {
            e.id = id != null && id.matches("[A-Za-z0-9-]{1,40}") && find(id) == null && !new File(root, id).exists()
                    ? id : UUID.randomUUID().toString().substring(0, 12);
        }
        e.name = name == null || name.trim().isEmpty() ? "Scan" : name.trim();
        e.format = format == null ? "JPEG" : format;
        e.where = where == null ? "" : where;
        e.time = time > 0 ? time : System.currentTimeMillis();
        e.files = files;
        e.dir = new File(root, e.id);
        if (!staged.renameTo(e.dir)) throw new IOException("Could not move the restored scan into place");
        JSONArray out = new JSONArray();
        try {
            String text = read(new File(e.dir, "pages.json")).trim();
            JSONArray a = text.startsWith("[") ? new JSONArray(text) : new JSONObject(text).getJSONArray("pages");
            for (int i = 0; i < a.length(); i++) {
                Page p = Page.fromJson(a.getJSONObject(i));
                File f = new File(e.dir, new File(p.file).getName());
                if (!f.isFile()) continue;
                p.file = f.getPath();
                out.put(p.toJson());
            }
            writeAtomic(new File(e.dir, "pages.json"), out.toString());
        } catch (Exception ex) {
            deleteDir(e.dir);
            throw new IOException("A scan in the backup is damaged: " + ex.getMessage());
        }
        if (out.length() == 0) { deleteDir(e.dir); throw new IOException("A scan in the backup has no readable pages"); }
        e.pages = out.length();
        if (!e.thumb().isFile()) writeThumb(e, pages(e));
        synchronized (this) {
            int at = 0;
            while (at < entries.size() && entries.get(at).time > e.time) at++;   // newest first
            entries.add(at, e);
            saveIndex();
        }
        return e;
    }

    static void deleteDir(File d) {
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) { if (f.isDirectory()) deleteDir(f); else f.delete(); }
        d.delete();
    }

    // ------------------------------------------------------------------ persistence

    private void saveIndex() {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("id", e.id); o.put("name", e.name); o.put("format", e.format); o.put("where", e.where);
                o.put("time", e.time); o.put("pages", e.pages); o.put("files", e.files);
                a.put(o);
            }
            writeAtomic(index, a.toString());
        } catch (Exception ex) {
            Log.w("RadioFilm", "saved index", ex);
        }
    }

    private void load() {
        if (!index.exists()) return;
        try {
            JSONArray a = new JSONArray(read(index));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.getString("id");
                e.name = o.optString("name", "Scan");
                e.format = o.optString("format", "JPEG");
                e.where = o.optString("where", "");
                e.time = o.optLong("time");
                e.pages = o.optInt("pages");
                e.files = o.optInt("files");
                e.dir = new File(root, e.id);
                if (e.dir.isDirectory()) entries.add(e);
            }
        } catch (Exception ex) {
            Log.w("RadioFilm", "saved load", ex);
        }
    }

    private static void writeAtomic(File f, String s) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(s.getBytes("UTF-8"));
        fo.getFD().sync();
        fo.close();
        if (!tmp.renameTo(f)) throw new IOException("rename failed");
    }

    private static String read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        byte[] b = new byte[(int) f.length()];
        int off = 0;
        while (off < b.length) { int n = in.read(b, off, b.length - off); if (n < 0) break; off += n; }
        in.close();
        return new String(b, 0, off, "UTF-8");
    }

    static void copy(File src, File dst) throws IOException {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally {
            in.close();
            out.close();
        }
    }
}
