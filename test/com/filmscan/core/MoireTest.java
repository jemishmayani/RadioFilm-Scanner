package com.filmscan.core;
/** Monitor Mode: removes screen moiré, keeps real edges, same result at any strip size, off = untouched. */
public class MoireTest {
  static int fails = 0;
  static void check(boolean ok, String m) { System.out.println((ok ? "PASS " : "FAIL ") + m); if (!ok) fails++; }
  static int[] scene(int w, int h) {
    int[] px = new int[w * h];
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
      double base = x < w / 2 ? 60 : 190;                       // a strong real edge (anatomy / text)
      double ripple = 22 * Math.sin(x * 0.9 + y * 0.35);         // fine interference pattern
      double hue = 30 * Math.sin(x * 0.55 - y * 0.8);            // rainbow colour fringes
      int r = cl(base + ripple + hue), g = cl(base + ripple), b = cl(base + ripple - hue);
      px[y * w + x] = 0xFF000000 | r << 16 | g << 8 | b; }
    return px; }
  static int cl(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
  /** mean |neighbour difference| inside the flat left area: fine-pattern energy */
  static double ripple(int[] px, int w, int h) { double s = 0; int n = 0;
    for (int y = 10; y < h - 10; y++) for (int x = 10; x < w / 2 - 20; x++) { int a = px[y * w + x], b = px[y * w + x + 1];
      s += Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)); n++; } return s / n; }
  /** colour spread (R-B) inside the flat left area */
  static double colour(int[] px, int w, int h) { double s = 0; int n = 0;
    for (int y = 10; y < h - 10; y++) for (int x = 10; x < w / 2 - 20; x++) { int p = px[y * w + x];
      s += Math.abs(((p >> 16) & 255) - (p & 255)); n++; } return s / n; }
  /** step height across the real edge, measured away from it */
  static double edge(int[] px, int w, int h) { int y = h / 2; int a = px[y * w + w / 2 - 12], b = px[y * w + w / 2 + 12];
    return ((b >> 8) & 255) - ((a >> 8) & 255); }
  public static void main(String[] args) {
    int w = 900, h = 600; int[] src = scene(w, h);
    int[] off = src.clone(); Moire.apply(new IntImage(off, w, h), 0);
    check(java.util.Arrays.equals(off, src), "Off leaves the picture untouched");
    double r0 = ripple(src, w, h), c0 = colour(src, w, h), e0 = edge(src, w, h);
    double lastR = r0;
    for (int s : new int[]{Moire.LOW, Moire.MEDIUM, Moire.HIGH}) {
      int[] px = src.clone(); Moire.apply(new IntImage(px, w, h), s);
      double r = ripple(px, w, h), c = colour(px, w, h), e = edge(px, w, h);
      System.out.printf("  %-6s ripple %.1f->%.1f  colour fringes %.1f->%.1f  real edge %.0f->%.0f%n", Moire.label(s), r0, r, c0, c, e0, e);
      check(r < lastR, Moire.label(s) + ": stronger setting removes more pattern");
      check(c < c0 * (s == Moire.LOW ? 0.5 : 0.3), Moire.label(s) + (s == Moire.LOW ? ": at least half the colour fringes gone" : ": colour fringes mostly gone"));
      check(e > e0 * 0.9, Moire.label(s) + ": real edges keep their contrast");
      lastR = r;
    }
    int[] a = src.clone(), b = src.clone();
    Filters.BAND_OVERRIDE = 0; Moire.apply(new IntImage(a, w, h), Moire.HIGH);
    Filters.BAND_OVERRIDE = 37; Moire.apply(new IntImage(b, w, h), Moire.HIGH);
    Filters.BAND_OVERRIDE = 0;
    int maxd = 0; for (int i = 0; i < a.length; i++) for (int sft = 0; sft < 24; sft += 8) maxd = Math.max(maxd, Math.abs(((a[i] >> sft) & 255) - ((b[i] >> sft) & 255)));
    check(maxd <= 1, "same result whatever the strip size (max difference " + maxd + ")");
    System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
  }
}
