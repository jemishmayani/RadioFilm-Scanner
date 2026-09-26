package com.filmscan.core;

/** In-memory ARGB image. */
public final class IntImage implements PixelSource {
    public final int[] px;
    public final int w, h;

    public IntImage(int[] px, int w, int h) { this.px = px; this.w = w; this.h = h; }

    public int width() { return w; }
    public int height() { return h; }

    public void read(int[] dst, int off, int y, int rows) { System.arraycopy(px, y * w, dst, off, rows * w); }

    public void write(int[] src, int off, int y, int rows) { System.arraycopy(src, off, px, y * w, rows * w); }
}
