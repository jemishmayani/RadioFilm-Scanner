package com.filmscan.core;

/**
 * Page geometry for PDF export, in PDF points (1/72 inch). The image always keeps its proportions:
 * "Fit to image" makes the page the image's shape; fixed sizes turn to match the image
 * (portrait or landscape) and centre it inside the margins.
 */
public final class PdfPage {
    private PdfPage() {}

    public static final int FIT = 0, A4 = 1, A3 = 2, LETTER = 3;
    public static final String[] SIZE_NAMES = {"Fit to image", "A4", "A3", "Letter"};
    public static final String[] MARGIN_NAMES = {"None", "Small", "Medium", "Large"};
    /** Margins: none, about 6 mm, 12.7 mm, 19 mm. */
    public static final double[] MARGINS = {0, 18, 36, 54};
    private static final double[][] SIZES = {null, {595.28, 841.89}, {841.89, 1190.55}, {612, 792}};

    /** Returns {pageW, pageH, x, y, drawW, drawH}; x,y is the image's lower-left corner (PDF coordinates). */
    public static double[] layout(int size, int margin, int imgW, int imgH) {
        double m = MARGINS[Math.max(0, Math.min(3, margin))];
        double aspect = imgW / (double) Math.max(1, imgH);
        if (size <= FIT || size > LETTER) {
            // long side of the image = 842 pt (A4 height), margins added around it
            double s = 842.0 / Math.max(imgW, imgH);
            double dw = imgW * s, dh = imgH * s;
            return new double[]{dw + 2 * m, dh + 2 * m, m, m, dw, dh};
        }
        double pw = SIZES[size][0], ph = SIZES[size][1];
        if (aspect > 1) { double t = pw; pw = ph; ph = t; }   // landscape image -> landscape page
        double aw = pw - 2 * m, ah = ph - 2 * m;
        double dw = aw, dh = aw / aspect;
        if (dh > ah) { dh = ah; dw = ah * aspect; }
        return new double[]{pw, ph, (pw - dw) / 2, (ph - dh) / 2, dw, dh};
    }
}
