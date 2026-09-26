package com.filmscan.app;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.UUID;

/** The current batch of pages. Persisted to disk after every change so nothing is lost. */
public final class Session {
    public final ArrayList<Page> pages = new ArrayList<Page>();
    private final File dir, json;
    /** True for the new-scan workspace, false when editing a saved scan in place. */
    private final boolean ownsDir;

    /** The workspace for new scans. */
    Session(Context c) {
        this(new File(c.getFilesDir(), "pages"), new File(c.getFilesDir(), "session.json"), true);
    }

    /** A workspace backed by any folder, e.g. a saved scan being edited. */
    Session(File dir, File json, boolean ownsDir) {
        this.dir = dir;
        this.json = json;
        this.ownsDir = ownsDir;
        dir.mkdirs();
        load();
    }

    /** When this workspace was last saved (0 if never). */
    public long lastSaved() { return json.lastModified(); }

    public File newImageFile(String ext) {
        return new File(dir, UUID.randomUUID().toString() + ext);
    }

    public static String newId() { return UUID.randomUUID().toString().substring(0, 13); }

    public Page find(String id) {
        if (id == null) return null;
        for (Page p : pages) if (id.equals(p.id)) return p;
        return null;
    }

    public int indexOf(Page p) { return pages.indexOf(p); }

    public void add(Page p) { pages.add(p); save(); }

    public void remove(Page p) {
        pages.remove(p);
        boolean shared = false;
        for (Page o : pages) if (o.file.equals(p.file)) { shared = true; break; }
        if (!shared) new File(p.file).delete();
        save();
    }

    public void clear() {
        if (!ownsDir) {
            // editing a saved scan: remove its pages one by one, never the folder's other files
            for (Page p : new ArrayList<Page>(pages)) remove(p);
            return;
        }
        pages.clear();
        File[] fs = dir.listFiles();
        if (fs != null) for (File f : fs) f.delete();
        save();
    }

    /** Rewrites the page order (used by drag-to-reorder). */
    public void setOrder(java.util.List<Page> order) {
        if (order.size() != pages.size()) return;
        pages.clear();
        pages.addAll(order);
        save();
    }

    public void move(int from, int to) {
        if (from < 0 || to < 0 || from >= pages.size() || to >= pages.size()) return;
        Page p = pages.remove(from);
        pages.add(to, p);
        save();
    }

    public synchronized void save() {
        try {
            JSONArray a = new JSONArray();
            for (Page p : pages) a.put(p.toJson());
            JSONObject o = new JSONObject();
            o.put("pages", a);
            File tmp = new File(json.getPath() + ".tmp");
            FileOutputStream fo = new FileOutputStream(tmp);
            fo.write(o.toString().getBytes("UTF-8"));
            fo.getFD().sync();
            fo.close();
            tmp.renameTo(json);
        } catch (Exception e) {
            Log.w("FilmScan", "session save failed", e);
        }
    }

    private void load() {
        if (!json.exists()) return;
        try {
            FileInputStream in = new FileInputStream(json);
            byte[] b = new byte[(int) json.length()];
            int off = 0;
            while (off < b.length) { int n = in.read(b, off, b.length - off); if (n < 0) break; off += n; }
            in.close();
            String text = new String(b, 0, off, "UTF-8").trim();
            JSONArray a = text.startsWith("[") ? new JSONArray(text) : new JSONObject(text).getJSONArray("pages");
            for (int i = 0; i < a.length(); i++) {
                Page p = Page.fromJson(a.getJSONObject(i));
                if (new File(p.file).exists()) pages.add(p);
            }
        } catch (Exception e) {
            Log.w("FilmScan", "session load failed", e);
        }
    }
}
