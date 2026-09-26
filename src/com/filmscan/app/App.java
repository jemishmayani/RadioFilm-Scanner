package com.filmscan.app;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class App extends Application {
    private static App instance;
    private Session session;
    private Prefs prefs;
    private SavedStore saved;
    public final Handler main = new Handler(Looper.getMainLooper());
    public final ExecutorService work = Executors.newFixedThreadPool(2);
    public final ExecutorService export = Executors.newSingleThreadExecutor();
    private LruCache<String, Bitmap> cache;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Ui.applyAccent(prefs().accent());
        long max = Runtime.getRuntime().maxMemory();
        int size = (int) Math.min(96L << 20, max / 5);
        cache = new LruCache<String, Bitmap>(size) {
            @Override
            protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };
    }

    public static App get() { return instance; }

    private Session editSession;
    private SavedStore.Entry editing;

    /** The active workspace: the saved scan being edited, or else the new-scan session. */
    public synchronized Session session() {
        if (editSession == null) {
            String id = prefs().editingId();
            SavedStore.Entry e = id == null ? null : saved().find(id);
            if (e != null) openEdit(e);
            else if (id != null) prefs().editingId(null);
        }
        if (editSession != null) return editSession;
        if (session == null) session = new Session(this);
        return session;
    }

    public synchronized Session mainSession() {
        if (session == null) session = new Session(this);
        return session;
    }

    public synchronized boolean isEditingSaved() { return editing != null || prefs().editingId() != null; }

    public synchronized SavedStore.Entry editingEntry() {
        session();
        return editing;
    }

    /** Starts editing one saved scan in its own workspace. Other scans are never mixed in. */
    public synchronized Session beginEdit(SavedStore.Entry e) {
        if (editing != null && editing != e) endEdit();
        if (editing == e && editSession != null) return editSession;
        openEdit(e);
        prefs().editingId(e.id);
        return editSession;
    }

    private void openEdit(SavedStore.Entry e) {
        editing = e;
        editSession = new Session(e.dir, new java.io.File(e.dir, "pages.json"), false);
    }

    /** Leaves edit mode. The group keeps its edits; an emptied group is removed. */
    public synchronized void endEdit() {
        final SavedStore.Entry e = editing;
        editing = null;
        editSession = null;
        prefs().editingId(null);
        if (e != null) {
            // refresh its cover and page count off the UI thread
            work.execute(new Runnable() {
                @Override public void run() { saved().refresh(e); }
            });
        }
    }

    private Runnable savedListener;

    /** Set once the start-up check for an unfinished scan has run in this process. */
    public boolean recoveryChecked;

    /** The Saved scans screen listens so covers refresh as soon as they are re-rendered. */
    public void setSavedListener(Runnable r) { savedListener = r; }

    void notifySaved() {
        main.post(new Runnable() {
            @Override public void run() { if (savedListener != null) savedListener.run(); }
        });
    }

    public synchronized SavedStore saved() {
        if (saved == null) saved = new SavedStore(this);
        return saved;
    }

    public synchronized Prefs prefs() {
        if (prefs == null) prefs = new Prefs(this);
        return prefs;
    }

    public Bitmap cached(String key) { return cache.get(key); }

    public void cache(String key, Bitmap b) { if (b != null) cache.put(key, b); }

    public void run(Runnable r) { work.execute(r); }

    public void ui(Runnable r) { main.post(r); }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) cache.evictAll();
        else if (level >= TRIM_MEMORY_RUNNING_LOW) cache.trimToSize(cache.maxSize() / 2);
    }
}
