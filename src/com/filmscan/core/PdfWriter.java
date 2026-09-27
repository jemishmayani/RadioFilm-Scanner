package com.filmscan.core;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal PDF writer that embeds JPEG pages directly (DCTDecode), so pages keep their full
 * resolution without re-encoding and the file stays small. Pages are streamed one at a time.
 */
public final class PdfWriter {
    private static final Charset ASCII = Charset.forName("US-ASCII");
    private final OutputStream out;
    private long pos = 0;
    private final List<Long> offsets = new ArrayList<Long>();
    private final List<Integer> pageIds = new ArrayList<Integer>();
    private int nextId = 3; // 1 = catalog, 2 = pages
    private boolean finished;
    private String producer = "RadioFilm Scanner";

    public void setProducer(String p) { if (p != null) producer = p; }

    public PdfWriter(OutputStream out) throws IOException {
        this.out = out;
        offsets.add(0L); offsets.add(0L); offsets.add(0L);
        write("%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n".getBytes("ISO-8859-1"));
    }

    private void write(byte[] b) throws IOException { out.write(b); pos += b.length; }

    private void write(String s) throws IOException { write(s.getBytes(ASCII)); }

    private void beginObj(int id) throws IOException {
        while (offsets.size() <= id) offsets.add(0L);
        offsets.set(id, pos);
        write(id + " 0 obj\n");
    }

    /**
     * Adds one page showing a JPEG image. The page keeps the image aspect ratio with its long side
     * at 842 pt (A4 height) so it prints naturally; the image keeps all of its pixels.
     */
    public void addJpegPage(byte[] jpeg, int pxW, int pxH, boolean gray) throws IOException {
        addJpegPage(new java.io.ByteArrayInputStream(jpeg), jpeg.length, pxW, pxH);
    }

    /** Adds a page from a JPEG file, copied in small chunks so it never has to fit in memory. */
    public void addJpegPage(java.io.File jpeg, int pxW, int pxH, boolean gray) throws IOException {
        addJpegPage(jpeg, pxW, pxH, PdfPage.layout(PdfPage.FIT, 0, pxW, pxH));
    }

    /** Adds a page with a given layout {pageW, pageH, x, y, drawW, drawH} in points (see PdfPage). */
    public void addJpegPage(java.io.File jpeg, int pxW, int pxH, double[] layout) throws IOException {
        java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(jpeg), 1 << 16);
        try {
            addJpegPage(in, jpeg.length(), pxW, pxH, layout);
        } finally {
            in.close();
        }
    }

    private void addJpegPage(java.io.InputStream jpeg, long length, int pxW, int pxH) throws IOException {
        addJpegPage(jpeg, length, pxW, pxH, PdfPage.layout(PdfPage.FIT, 0, pxW, pxH));
    }

    private void addJpegPage(java.io.InputStream jpeg, long length, int pxW, int pxH, double[] lay) throws IOException {
        String pw = fmt(lay[0]), ph = fmt(lay[1]);
        int img = nextId++, content = nextId++, page = nextId++;
        beginObj(img);
        write("<< /Type /XObject /Subtype /Image /Width " + pxW + " /Height " + pxH
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + length + " >>\nstream\n");
        byte[] buf = new byte[1 << 16];
        long copied = 0;
        int n;
        while ((n = jpeg.read(buf)) > 0) {
            out.write(buf, 0, n);
            pos += n;
            copied += n;
        }
        if (copied != length) throw new IOException("JPEG size changed while writing the PDF");
        write("\nendstream\nendobj\n");
        String cs = "q " + fmt(lay[4]) + " 0 0 " + fmt(lay[5]) + " " + fmt(lay[2]) + " " + fmt(lay[3]) + " cm /Im0 Do Q\n";
        beginObj(content);
        write("<< /Length " + cs.length() + " >>\nstream\n" + cs + "endstream\nendobj\n");
        beginObj(page);
        write("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pw + " " + ph + "] /Resources << /XObject << /Im0 "
                + img + " 0 R >> /ProcSet [/PDF /ImageC] >> /Contents " + content + " 0 R >>\nendobj\n");
        pageIds.add(page);
    }

    public void finish(String title) throws IOException {
        if (finished) return;
        finished = true;
        beginObj(1);
        write("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        beginObj(2);
        StringBuilder kids = new StringBuilder();
        for (int id : pageIds) kids.append(id).append(" 0 R ");
        write("<< /Type /Pages /Kids [" + kids + "] /Count " + pageIds.size() + " >>\nendobj\n");
        int info = nextId++;
        beginObj(info);
        write("<< /Title (" + escape(title) + ") /Producer (" + escape(producer) + ") >>\nendobj\n");
        long xref = pos;
        int size = nextId;
        StringBuilder sb = new StringBuilder();
        sb.append("xref\n0 ").append(size).append("\n0000000000 65535 f \n");
        for (int i = 1; i < size; i++) {
            long off = i < offsets.size() ? offsets.get(i) : 0;
            String o = String.valueOf(off);
            for (int k = o.length(); k < 10; k++) sb.append('0');
            sb.append(o).append(" 00000 n \n");
        }
        sb.append("trailer\n<< /Size ").append(size).append(" /Root 1 0 R /Info ").append(info)
                .append(" 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        write(sb.toString());
        out.flush();
    }

    public int pageCount() { return pageIds.size(); }

    private static String fmt(double v) {
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == ')' || c == '\\') b.append('\\');
            b.append(c < 128 ? c : '_');
        }
        return b.toString();
    }
}
