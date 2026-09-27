package com.filmscan.core;

/**
 * Geometry for page layouts (several pages on one image): grid cells inside margins with gaps,
 * and placing a page in a cell either whole ("fit") or filling the cell ("fill", cropped to fit).
 */
public final class Collage {
    private Collage() {}

    /** Cell rectangles {left, top, right, bottom} in canvas pixels, row by row. */
    public static float[][] cells(int canvasW, int canvasH, int rows, int cols, float margin, float gap) {
        rows = Math.max(1, rows);
        cols = Math.max(1, cols);
        float gw = Math.max(0, (canvasW - 2 * margin - gap * (cols - 1)) / cols);
        float gh = Math.max(0, (canvasH - 2 * margin - gap * (rows - 1)) / rows);
        float[][] out = new float[rows * cols][];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float l = margin + c * (gw + gap), t = margin + r * (gh + gap);
                out[r * cols + c] = new float[]{l, t, l + gw, t + gh};
            }
        }
        return out;
    }

    /**
     * Where to draw an image of imgW x imgH in a cell. Returns {srcL, srcT, srcR, srcB, dstL, dstT, dstR, dstB}:
     * fit shows the whole image centred; fill covers the cell and crops the image's edges evenly.
     */
    public static float[] place(float[] cell, int imgW, int imgH, boolean fill) {
        float cw = cell[2] - cell[0], ch = cell[3] - cell[1];
        float ia = imgW / (float) Math.max(1, imgH), ca = cw / Math.max(1e-6f, ch);
        if (!fill) {
            float dw = cw, dh = cw / ia;
            if (dh > ch) { dh = ch; dw = ch * ia; }
            float l = cell[0] + (cw - dw) / 2, t = cell[1] + (ch - dh) / 2;
            return new float[]{0, 0, imgW, imgH, l, t, l + dw, t + dh};
        }
        float sw = imgW, sh = imgH;
        if (ia > ca) sw = imgH * ca; else sh = imgW / ca;
        float sl = (imgW - sw) / 2, st = (imgH - sh) / 2;
        return new float[]{sl, st, sl + sw, st + sh, cell[0], cell[1], cell[2], cell[3]};
    }

    /** Rows and columns for "Auto": few pages side by side, more pages in a balanced grid. */
    public static int[] autoGrid(int n, boolean landscape) {
        if (n <= 1) return new int[]{1, 1};
        if (n == 2) return landscape ? new int[]{1, 2} : new int[]{2, 1};
        if (n <= 4) return new int[]{2, 2};
        if (n <= 6) return landscape ? new int[]{2, 3} : new int[]{3, 2};
        if (n <= 9) return new int[]{3, 3};
        if (n <= 12) return landscape ? new int[]{3, 4} : new int[]{4, 3};
        return new int[]{4, 4};
    }
}
