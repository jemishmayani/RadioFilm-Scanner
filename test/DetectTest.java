import com.filmscan.core.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

public class DetectTest {
    public static Random rnd;

    // Heckbert square-to-quad
    static double[] sq2quad(float[] q) {
        double x0 = q[0], y0 = q[1], x1 = q[2], y1 = q[3], x2 = q[4], y2 = q[5], x3 = q[6], y3 = q[7];
        double dx1 = x1 - x2, dx2 = x3 - x2, dx3 = x0 - x1 + x2 - x3;
        double dy1 = y1 - y2, dy2 = y3 - y2, dy3 = y0 - y1 + y2 - y3;
        double det = dx1 * dy2 - dx2 * dy1;
        double g = (dx3 * dy2 - dx2 * dy3) / det, h = (dx1 * dy3 - dx3 * dy1) / det;
        double a = x1 - x0 + g * x1, b = x3 - x0 + h * x3, c = x0;
        double d = y1 - y0 + g * y1, e = y3 - y0 + h * y3, f = y0;
        return new double[]{a, b, c, d, e, f, g, h, 1};
    }

    static double[] inv3(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
        double A = e * i - f * h, B = -(d * i - f * g), C = d * h - e * g;
        double det = a * A + b * B + c * C;
        return new double[]{A / det, -(b * i - c * h) / det, (b * f - c * e) / det,
                B / det, (a * i - c * g) / det, -(a * f - c * d) / det,
                C / det, -(a * h - b * g) / det, (a * e - b * d) / det};
    }

    public static BufferedImage content(int type, int cw, int ch) {
        BufferedImage img = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (type == 0) { // CT film
            g.setColor(new Color(28, 32, 40)); g.fillRect(0, 0, cw, ch);
            int rows = 4, cols = 3, m = cw / 30;
            int pw = (cw - m * (cols + 1)) / cols, ph = (ch - m * (rows + 1)) / rows;
            for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
                int x = m + c * (pw + m), y = m + r * (ph + m);
                g.setColor(new Color(8, 8, 10)); g.fillRect(x, y, pw, ph);
                int b = 140 + rnd.nextInt(80);
                g.setColor(new Color(b, b, b)); g.fillOval(x + pw / 8, y + ph / 8, pw * 3 / 4, ph * 3 / 4);
                g.setColor(new Color(b / 2, b / 2, b / 2)); g.fillOval(x + pw / 3, y + ph / 3, pw / 3, ph / 4);
                g.setColor(new Color(200, 200, 200)); g.setFont(new Font("SansSerif", Font.PLAIN, ph / 12));
                g.drawString("SE:3 IM:" + (r * cols + c), x + 4, y + ph / 10);
            }
        } else if (type == 1) { // paper report
            g.setColor(new Color(236, 234, 228)); g.fillRect(0, 0, cw, ch);
            g.setColor(new Color(40, 40, 50));
            g.setFont(new Font("Serif", Font.PLAIN, ch / 45));
            for (int y = ch / 10; y < ch * 9 / 10; y += ch / 30) {
                StringBuilder sb = new StringBuilder();
                int len = 20 + rnd.nextInt(40);
                for (int i = 0; i < len; i++) sb.append((char) ('a' + rnd.nextInt(26))).append(rnd.nextInt(6) == 0 ? " " : "");
                g.drawString(sb.toString(), cw / 12, y);
            }
        } else { // MRI film, lighter panels
            g.setColor(new Color(20, 22, 30)); g.fillRect(0, 0, cw, ch);
            for (int k = 0; k < 20; k++) {
                int b = 90 + rnd.nextInt(120);
                g.setColor(new Color(b, b, b));
                int x = rnd.nextInt(cw), y = rnd.nextInt(ch);
                g.fillOval(x - cw / 10, y - ch / 10, cw / 5, ch / 5);
            }
        }
        g.dispose();
        return img;
    }

    static int bgPixel(int bg, int x, int y, int W, int H) {
        double n = rnd.nextGaussian() * 4;
        if (bg == 0) { // lightbox / window: bright gradient
            double v = 225 + 25 * Math.sin(x * 0.004) - 20.0 * y / H + n;
            int c = clamp((int) v);
            return (clamp(c - 8) << 16) | (clamp(c - 2) << 8) | clamp(c + 6);
        } else if (bg == 1) { // wood table
            double s = Math.sin((x + 30 * Math.sin(y * 0.01)) * 0.05) * 18 + Math.sin(x * 0.31) * 6;
            int r = clamp((int) (140 + s + n)), gg = clamp((int) (95 + s * 0.8 + n)), b = clamp((int) (60 + s * 0.5 + n));
            return (r << 16) | (gg << 8) | b;
        } else { // grey cluttered desk
            int v = (int) (120 + n + ((x / 90 + y / 70) % 2 == 0 ? 12 : -12));
            if (Math.abs(y - H * 0.85) < 3 || Math.abs(x - W * 0.1) < 2) v = 40;
            v = clamp(v);
            return (v << 16) | (v << 8) | v;
        }
    }

    static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    static BufferedImage scene(int W, int H, float[] quad, BufferedImage content, int bg) {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        double[] Hm = sq2quad(quad), Hi = inv3(Hm);
        int cw = content.getWidth(), ch = content.getHeight();
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            // 2x2 supersampling for anti-aliased borders
            int rs = 0, gs = 0, bs = 0;
            for (int s = 0; s < 4; s++) {
                double px = x + 0.25 + 0.5 * (s & 1), py = y + 0.25 + 0.5 * (s >> 1);
                double wz = Hi[6] * px + Hi[7] * py + Hi[8];
                double u = (Hi[0] * px + Hi[1] * py + Hi[2]) / wz, v = (Hi[3] * px + Hi[4] * py + Hi[5]) / wz;
                int p;
                if (u >= 0 && u < 1 && v >= 0 && v < 1) p = content.getRGB((int) (u * cw), (int) (v * ch));
                else p = bgPixel(bg, x, y, W, H);
                rs += (p >> 16) & 255; gs += (p >> 8) & 255; bs += p & 255;
            }
            int n = (int) (rnd.nextGaussian() * 3);
            out.setRGB(x, y, (clamp(rs / 4 + n) << 16) | (clamp(gs / 4 + n) << 8) | clamp(bs / 4 + n));
        }
        return out;
    }

    static BufferedImage scaleTo(BufferedImage src, int maxSide) {
        double s = maxSide / (double) Math.max(src.getWidth(), src.getHeight());
        int w = (int) Math.round(src.getWidth() * s), h = (int) Math.round(src.getHeight() * s);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.getScaledInstance(w, h, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return out;
    }

    public static void main(String[] args) throws Exception {
        int trials = args.length > 0 ? Integer.parseInt(args[0]) : 24;
        int ok = 0, miss = 0; double sumErr = 0;
        for (int t = 0; t < trials; t++) {
            rnd = new Random(1000 + t);
            int W = 1200, H = 1600;
            int type = t % 3, bg = (t / 3) % 3;
            // random perspective quad
            float cx = W / 2f + (rnd.nextFloat() - 0.5f) * W * 0.1f, cy = H / 2f + (rnd.nextFloat() - 0.5f) * H * 0.1f;
            float hw = W * (0.28f + rnd.nextFloat() * 0.16f), hh = H * (0.28f + rnd.nextFloat() * 0.16f);
            float[] q = new float[8];
            float[][] base = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            double rot = (rnd.nextFloat() - 0.5) * 0.4;
            for (int i = 0; i < 4; i++) {
                double bx = base[i][0] * hw * (1 + (rnd.nextFloat() - 0.5) * 0.25), by = base[i][1] * hh * (1 + (rnd.nextFloat() - 0.5) * 0.25);
                q[2 * i] = (float) (cx + bx * Math.cos(rot) - by * Math.sin(rot));
                q[2 * i + 1] = (float) (cy + bx * Math.sin(rot) + by * Math.cos(rot));
            }
            BufferedImage sc = scene(W, H, q, content(type, 900, 1200), bg);
            BufferedImage small = scaleTo(sc, 560);
            int w = small.getWidth(), h = small.getHeight();
            int[] px = small.getRGB(0, 0, w, h, null, 0, w);
            long t0 = System.nanoTime();
            EdgeDetector.Result r = EdgeDetector.detect(px, w, h);
            long ms = (System.nanoTime() - t0) / 1000000;
            float s = W / (float) w;
            double err = -1;
            if (r != null) {
                err = 0;
                for (int i = 0; i < 8; i++) err = Math.max(err, Math.abs(r.quad[i] * s - q[i]));
                err = err / Math.hypot(W, H) * 100;
                sumErr += err;
                if (err < 1.0) ok++;
            } else miss++;
            System.out.printf("trial %2d type=%d bg=%d  %s err=%.2f%% of diag  score=%.3f sup=%.2f %dms%n", t, type, bg,
                    r == null ? "MISS" : "found", err, r == null ? 0 : r.score, r == null ? 0 : r.support, ms);
            if (args.length > 1) {
                Graphics2D g = small.createGraphics();
                g.setColor(Color.RED);
                if (r != null) for (int i = 0; i < 4; i++) g.drawLine((int) r.quad[2 * i], (int) r.quad[2 * i + 1], (int) r.quad[2 * ((i + 1) & 3)], (int) r.quad[2 * ((i + 1) & 3) + 1]);
                g.dispose();
                ImageIO.write(small, "png", new File(System.getProperty("java.io.tmpdir"), "det_" + t + ".png"));
            }
        }
        System.out.printf("accurate(<1%%): %d/%d  miss: %d  meanErr=%.2f%%%n", ok, trials, miss, sumErr / Math.max(1, trials - miss));
    }
}
