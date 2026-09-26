package com.filmscan.app;

import com.filmscan.core.Filters;
import java.util.List;

/**
 * Helpers for undo/redo and reset. Edits are only settings stored with a page (crop, rotation,
 * filter, adjustments, grid, anonymised areas); the original photo is never changed, so any state
 * can be restored exactly.
 */
public final class EditHistory {
    private EditHistory() {}

    public static boolean sameQuad(float[] a, float[] b) {
        if (a == null || b == null) return a == b;
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (Math.abs(a[i] - b[i]) > 1e-4f) return false;
        return true;
    }

    private static boolean sameBoxes(List<float[]> a, List<float[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!sameQuad(a.get(i), b.get(i))) return false;
        return true;
    }

    /** True when two states would produce the same result. */
    public static boolean sameEdits(Page a, Page b) {
        return sameQuad(a.quad, b.quad) && a.rot == b.rot && a.filter == b.filter
                && a.bright == b.bright && a.contrast == b.contrast && a.sharp == b.sharp
                && a.gridR == b.gridR && a.gridC == b.gridC && sameBoxes(a.redact, b.redact);
    }

    /** Copies the edit settings of {@code from} into {@code to} (same photo, same id). */
    public static void apply(Page from, Page to) {
        to.quad = from.quad == null ? null : from.quad.clone();
        to.detected = from.detected;
        to.rot = from.rot;
        to.filter = from.filter;
        to.bright = from.bright;
        to.contrast = from.contrast;
        to.sharp = from.sharp;
        to.gridR = from.gridR;
        to.gridC = from.gridC;
        to.redact.clear();
        for (float[] r : from.redact) to.redact.add(r.clone());
    }

    /** Back to the original photo: automatic edges (found again on load), no filter or other edits. */
    public static void clearEdits(Page p) {
        p.quad = null;
        p.detected = false;
        p.rot = 0;
        p.filter = Filters.ORIGINAL;
        p.bright = 0;
        p.contrast = 0;
        p.sharp = 0;
        p.gridR = 1;
        p.gridC = 1;
        p.redact.clear();
    }
}
