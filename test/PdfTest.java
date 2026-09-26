import com.filmscan.core.*; import java.io.*; import java.awt.image.*; import javax.imageio.*;
public class PdfTest { public static void main(String[] a) throws Exception {
  FileOutputStream fo = new FileOutputStream(new File(System.getProperty("java.io.tmpdir"), "t.pdf")); PdfWriter p = new PdfWriter(new BufferedOutputStream(fo));
  for (int i=0;i<3;i++){ BufferedImage im = new BufferedImage(600+i*200, 800, BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g=im.createGraphics(); g.setColor(java.awt.Color.WHITE); g.fillRect(0,0,2000,2000); g.setColor(java.awt.Color.RED); g.fillOval(50,50,300,300); g.setColor(java.awt.Color.BLACK); g.drawString("Page "+(i+1),100,500); g.dispose();
    ByteArrayOutputStream bo=new ByteArrayOutputStream(); ImageIO.write(im,"jpg",bo); p.addJpegPage(bo.toByteArray(), im.getWidth(), im.getHeight(), false); }
  p.finish("Test (scan)"); fo.close(); } }
