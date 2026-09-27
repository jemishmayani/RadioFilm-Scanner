package com.filmscan.app;

import com.filmscan.core.Filters;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;

/** One scanned page: the original capture plus every non-destructive edit. */
public final class Page {
    public String id;
    public String file;          // original image, never modified
    public int rawW, rawH;       // stored pixel size (before EXIF rotation)
    public int exif = 1;         // EXIF orientation
    public float[] quad;         // normalized TL,TR,BR,BL in upright image; null = not set yet
    public boolean detected;     // edges were found automatically
    public int rot;              // extra quarter turns clockwise
    public int filter = Filters.ORIGINAL;
    public int bright, contrast, sharp;
    public int gridR = 1, gridC = 1;
    /** Monitor Mode (moiré reduction) strength 0..100; 0 = off. Its own layer, applied before filters. */
    public int moire;
    public ArrayList<float[]> redact = new ArrayList<float[]>(); // normalized l,t,r,b in final output
    public int version;

    public boolean swapsAxes() { return exif >= 5 && exif <= 8; }

    public int dispW() { return swapsAxes() ? rawH : rawW; }

    public int dispH() { return swapsAxes() ? rawW : rawH; }

    public boolean hasGrid() { return gridR > 1 || gridC > 1; }

    public Page copy() {
        Page p = new Page();
        p.id = id; p.file = file; p.rawW = rawW; p.rawH = rawH; p.exif = exif;
        p.quad = quad == null ? null : quad.clone();
        p.detected = detected; p.rot = rot; p.filter = filter;
        p.bright = bright; p.contrast = contrast; p.sharp = sharp;
        p.gridR = gridR; p.gridC = gridC;
        p.moire = moire;
        for (float[] r : redact) p.redact.add(r.clone());
        p.version = version;
        return p;
    }

    /** Key for caches of rendered output; changes whenever an edit changes the result. */
    public String renderKey() { return id + ":" + version; }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id); o.put("file", file); o.put("rawW", rawW); o.put("rawH", rawH); o.put("exif", exif);
        if (quad != null) o.put("quad", arr(quad));
        o.put("detected", detected); o.put("rot", rot); o.put("filter", filter);
        o.put("bright", bright); o.put("contrast", contrast); o.put("sharp", sharp);
        o.put("gridR", gridR); o.put("gridC", gridC); o.put("version", version);
        o.put("moire", moire);
        JSONArray rs = new JSONArray();
        for (float[] r : redact) rs.put(arr(r));
        o.put("redact", rs);
        return o;
    }

    public static Page fromJson(JSONObject o) throws JSONException {
        Page p = new Page();
        p.id = o.getString("id"); p.file = o.getString("file");
        p.rawW = o.getInt("rawW"); p.rawH = o.getInt("rawH"); p.exif = o.optInt("exif", 1);
        if (o.has("quad")) p.quad = floats(o.getJSONArray("quad"));
        p.detected = o.optBoolean("detected"); p.rot = o.optInt("rot"); p.filter = o.optInt("filter", Filters.ORIGINAL);
        p.bright = o.optInt("bright"); p.contrast = o.optInt("contrast"); p.sharp = o.optInt("sharp");
        p.gridR = Math.max(1, o.optInt("gridR", 1)); p.gridC = Math.max(1, o.optInt("gridC", 1));
        p.version = o.optInt("version");
        p.moire = Math.max(0, Math.min(100, o.optInt("moire", 0)));
        JSONArray rs = o.optJSONArray("redact");
        if (rs != null) for (int i = 0; i < rs.length(); i++) p.redact.add(floats(rs.getJSONArray(i)));
        return p;
    }

    private static JSONArray arr(float[] v) throws JSONException {
        JSONArray a = new JSONArray();
        for (float f : v) a.put((double) f);
        return a;
    }

    private static float[] floats(JSONArray a) throws JSONException {
        float[] v = new float[a.length()];
        for (int i = 0; i < v.length; i++) v[i] = (float) a.getDouble(i);
        return v;
    }
}
