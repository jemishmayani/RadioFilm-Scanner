import com.filmscan.core.*;
/** PDF page geometry and page-layout grids: proportions kept, everything inside margins and cells. */
public class LayoutGeometryTest {
  static int fails = 0;
  static void check(boolean ok, String m) { System.out.println((ok ? "PASS " : "FAIL ") + m); if (!ok) fails++; }
  static boolean near(double a, double b) { return Math.abs(a - b) < 0.01; }
  public static void main(String[] args) {
    // ---- PDF
    double[] f = PdfPage.layout(PdfPage.FIT, 0, 3000, 4000);
    check(near(f[5], 842) && near(f[4] / f[5], 0.75) && near(f[0], f[4]) && near(f[2], 0), "Fit to image, no margin: page = image shape, long side 842 pt");
    double[] fm = PdfPage.layout(PdfPage.FIT, 2, 3000, 4000);
    check(near(fm[0], fm[4] + 72) && near(fm[2], 36) && near(fm[3], 36), "Fit to image + medium margin: 36 pt all round");
    for (int size : new int[]{PdfPage.A4, PdfPage.A3, PdfPage.LETTER}) for (int m = 0; m < 4; m++) for (int[] img : new int[][]{{3000, 4000}, {4000, 3000}, {1000, 3000}, {5000, 1000}}) {
      double[] p = PdfPage.layout(size, m, img[0], img[1]);
      double mg = PdfPage.MARGINS[m];
      boolean inside = p[2] >= mg - 0.01 && p[3] >= mg - 0.01 && p[2] + p[4] <= p[0] - mg + 0.01 && p[3] + p[5] <= p[1] - mg + 0.01;
      boolean aspect = near(p[4] / p[5], img[0] / (double) img[1]);
      boolean orient = (img[0] > img[1]) == (p[0] > p[1]);
      boolean centred = near(p[2], p[0] - p[2] - p[4]) && near(p[3], p[1] - p[3] - p[5]);
      if (!(inside && aspect && orient && centred)) { check(false, PdfPage.SIZE_NAMES[size] + " margin " + m + " image " + img[0] + "x" + img[1]); }
    }
    check(true, "A4 / A3 / Letter x 4 margins x 4 image shapes: inside margins, centred, proportions kept, page turns with image");
    double[] a4 = PdfPage.layout(PdfPage.A4, 0, 2480, 3508);
    check(near(a4[0], 595.28) && near(a4[1], 841.89), "A4 portrait is 595 x 842 pt");
    // ---- layout grids
    float[][] cells = Collage.cells(2400, 3394, 3, 2, 100, 40);
    boolean ok = cells.length == 6;
    for (float[] c : cells) ok &= c[0] >= 100 - 0.01 && c[1] >= 100 - 0.01 && c[2] <= 2300 + 0.01 && c[3] <= 3294 + 0.01;
    ok &= near(cells[1][0] - cells[0][2], 40) && near(cells[2][1] - cells[0][3], 40);
    check(ok, "3x2 grid: 6 equal cells inside margins with exact gaps");
    float[] fit = Collage.place(new float[]{0, 0, 1000, 1000}, 3000, 4000, false);
    check(near((fit[6] - fit[4]) / (fit[7] - fit[5]), 0.75) && near(fit[7] - fit[5], 1000) && near(fit[4], 125), "Fit: whole page visible, centred, proportions kept");
    float[] fill = Collage.place(new float[]{0, 0, 1000, 500}, 3000, 4000, true);
    check(near((fill[2] - fill[0]) / (fill[3] - fill[1]), 2.0) && near(fill[6], 1000) && near(fill[1], (4000 - 1500) / 2.0), "Fill: covers the cell, crops evenly, no stretching");
    boolean cap = true; for (int n = 1; n <= 16; n++) for (boolean land : new boolean[]{false, true}) { int[] g = Collage.autoGrid(n, land); cap &= g[0] * g[1] >= n; }
    check(cap, "Auto grid always has room for 1-16 pages");
    System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
  }
}
