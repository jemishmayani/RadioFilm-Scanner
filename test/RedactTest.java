import com.filmscan.core.Redact;
/** Anonymising maths: blur really smooths, pixelate averages blocks, old saves stay black boxes. */
public class RedactTest {
  static int fails = 0;
  static void check(boolean ok, String m) { System.out.println((ok ? "PASS " : "FAIL ") + m); if (!ok) fails++; }
  static double contrast(int[] px) { // mean absolute difference between neighbours (text-like detail)
    double s = 0; for (int i = 1; i < px.length; i++) s += Math.abs(((px[i] >> 8) & 255) - ((px[i - 1] >> 8) & 255)); return s / px.length; }
  public static void main(String[] a) {
    check(Redact.type(new float[]{0, 0, 1, 1}) == Redact.BOX, "4-number regions from older saves are black boxes");
    check(Redact.type(new float[]{0, 0, 1, 1, 1}) == Redact.BLUR && Redact.type(new float[]{0, 0, 1, 1, 2}) == Redact.PIXELATE, "blur/pixelate types read back");
    check(Redact.type(new float[]{0, 0, 1, 1, 7}) == Redact.BOX, "unknown type falls back to the safe black box");
    int w = 200, h = 60; int[] px = new int[w * h];
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { boolean ink = ((x / 3) % 2 == 0) && y > 15 && y < 45; int v = ink ? 20 : 235; px[y * w + x] = 0xFF000000 | v << 16 | v << 8 | v; }
    double before = contrast(px);
    int[] b = px.clone(); Redact.blur(b, w, h, 4);
    double after = contrast(b);
    check(after < before * 0.1, String.format("blur removes fine detail (%.1f -> %.2f)", before, after));
    long sumA = 0, sumB = 0; for (int i = 0; i < px.length; i++) { sumA += px[i] & 255; sumB += b[i] & 255; }
    check(Math.abs(sumA - sumB) / (double) px.length < 3, "blur keeps overall brightness");
    int[] avg = Redact.blockAverages(px, w, h, 10, 3);
    check(avg.length == 30, "pixelate grid size");
    boolean uniformOk = true;
    int[] flat = new int[40 * 40]; java.util.Arrays.fill(flat, 0xFF336699);
    for (int c : Redact.blockAverages(flat, 40, 40, 4, 4)) if (c != 0xFF336699) uniformOk = false;
    check(uniformOk, "pixelate of a flat colour keeps the colour");
    check(Redact.blurRadius(1800, 1200) == Math.round(1800 * 0.008f) && Redact.blurRadius(6000, 4000) > Redact.blurRadius(1800, 1200), "blur strength scales with image size (preview matches export)");
    check(Redact.blockSize(100, 100) >= 4, "pixel blocks never tiny");
    System.out.println(fails == 0 ? "ALL PASSED" : fails + " FAILED");
  }
}
