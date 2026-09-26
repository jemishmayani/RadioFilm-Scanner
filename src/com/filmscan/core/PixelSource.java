package com.filmscan.core;

/** Row-band access to an image so very large images can be processed with small buffers. */
public interface PixelSource {
    int width();
    int height();
    /** Read {@code rows} rows starting at {@code y} into dst (row stride = width) at offset. */
    void read(int[] dst, int off, int y, int rows);
    void write(int[] src, int off, int y, int rows);
}
