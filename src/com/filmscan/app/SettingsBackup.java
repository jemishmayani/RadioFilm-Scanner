package com.filmscan.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Saves all settings (including name suggestions and accent colour) to a JSON file and reads them back. */
public final class SettingsBackup {
    private SettingsBackup() {}

    private static final String KIND = "radiofilm-scanner-settings";

    /** Things that describe this phone or this moment rather than preferences. */
    private static boolean skip(String key) {
        return key.startsWith("hiResFailed_") || key.equals("editingId") || key.equals("recentNames") || key.equals("lastFilter");
    }

    public static String fileName() {
        return "RadioFilm-Scanner-settings-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
    }

    public static void write(Context c, Uri uri) throws Exception {
        SharedPreferences sp = App.get().prefs().raw();
        JSONObject prefs = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (skip(e.getKey())) continue;
            Object v = e.getValue();
            JSONObject o = new JSONObject();
            if (v instanceof Boolean) o.put("t", "b").put("v", v);
            else if (v instanceof Integer) o.put("t", "i").put("v", v);
            else if (v instanceof Long) o.put("t", "l").put("v", v);
            else if (v instanceof Float) o.put("t", "f").put("v", ((Float) v).doubleValue());
            else if (v instanceof String) o.put("t", "s").put("v", v);
            else if (v instanceof Set) {
                JSONArray a = new JSONArray();
                for (Object s : (Set<?>) v) a.put(String.valueOf(s));
                o.put("t", "set").put("v", a);
            } else continue;
            prefs.put(e.getKey(), o);
        }
        JSONObject root = new JSONObject();
        root.put("kind", KIND).put("version", 1).put("app", "RadioFilm Scanner")
                .put("exported", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date()))
                .put("prefs", prefs);
        OutputStream os = c.getContentResolver().openOutputStream(uri, "w");
        if (os == null) throw new Exception("The file could not be created");
        try {
            os.write(root.toString(2).getBytes("UTF-8"));
        } finally {
            os.close();
        }
    }

    /** Reads and checks a settings file. Returns the settings to apply. */
    public static JSONObject read(Context c, Uri uri) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("The file could not be opened");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > 2_000_000) throw new Exception("This file is too large to be a settings file");
            }
        } finally {
            in.close();
        }
        JSONObject root = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
        if (!KIND.equals(root.optString("kind")) || root.optJSONObject("prefs") == null) {
            throw new Exception("This is not a RadioFilm Scanner settings file");
        }
        return root;
    }

    public static int apply(JSONObject root) throws Exception {
        JSONObject prefs = root.getJSONObject("prefs");
        SharedPreferences.Editor ed = App.get().prefs().raw().edit();
        int count = 0;
        Iterator<String> it = prefs.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (skip(k)) continue;
            JSONObject o = prefs.optJSONObject(k);
            if (o == null) continue;
            String t = o.optString("t");
            if ("b".equals(t)) ed.putBoolean(k, o.optBoolean("v"));
            else if ("i".equals(t)) ed.putInt(k, o.optInt("v"));
            else if ("l".equals(t)) ed.putLong(k, o.optLong("v"));
            else if ("f".equals(t)) ed.putFloat(k, (float) o.optDouble("v"));
            else if ("s".equals(t)) ed.putString(k, o.optString("v"));
            else if ("set".equals(t)) {
                Set<String> s = new HashSet<String>();
                JSONArray a = o.optJSONArray("v");
                for (int i = 0; a != null && i < a.length(); i++) s.add(a.optString(i));
                ed.putStringSet(k, s);
            } else continue;
            count++;
        }
        ed.commit();
        Ui.applyAccent(App.get().prefs().accent());
        Branding.syncLauncher(App.get());
        return count;
    }
}
