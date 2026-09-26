package com.filmscan.core;
import java.awt.*; import java.awt.image.*; import java.io.*; import javax.imageio.*; import java.util.Random;
public class FilterTest {
  static BufferedImage photoOfFilm(int W, int H, int type) {
    DetectTestBridge.seed(7);
    BufferedImage c = DetectTestBridge.content(type, W, H);
    // simulate uneven lightbox + color cast + noise + slight blur
    Random r = new Random(3);
    for (int y=0;y<H;y++) for (int x=0;x<W;x++){ int p=c.getRGB(x,y);
      double light = 0.75 + 0.35*Math.exp(-((x-W*0.3)*(x-W*0.3)+(y-H*0.4)*(y-H*0.4))/(2.0*W*W*0.15)) - 0.2*y/H;
      int R=(int)(((p>>16)&255)*light*0.95+r.nextGaussian()*3), G=(int)(((p>>8)&255)*light+r.nextGaussian()*3), B=(int)((p&255)*light*1.08+10+r.nextGaussian()*3);
      c.setRGB(x,y,(cl(R)<<16)|(cl(G)<<8)|cl(B)); }
    return c;
  }
  static int cl(int v){return v<0?0:v>255?255:v;}
  public static void main(String[] a) throws Exception {
    // 1) band-equivalence test for every filter
    BufferedImage f = photoOfFilm(300, 460, 0);
    int w=f.getWidth(), h=f.getHeight(); int[] base=f.getRGB(0,0,w,h,null,0,w);
    for (int fl=0; fl<Filters.COUNT; fl++){
      int[] p1=base.clone(), p2=base.clone();
      Filters.BAND_OVERRIDE=0; Filters.apply(new IntImage(p1,w,h), fl, 10, 20, 50);
      Filters.BAND_OVERRIDE=13; Filters.apply(new IntImage(p2,w,h), fl, 10, 20, 50);
      int diff=0; for(int i=0;i<p1.length;i++) if(p1[i]!=p2[i]) diff++;
      System.out.println(Filters.NAMES[fl]+": band mismatch pixels="+diff);
    }
    Filters.BAND_OVERRIDE=0;
    // 2) visual sheet
    for (int type : new int[]{0,1}) {
      BufferedImage src = type==0? photoOfFilm(360,480,0) : photoOfFilm(360,480,1);
      int tw=src.getWidth(), th=src.getHeight();
      BufferedImage sheet = new BufferedImage(tw*6, th*2+40, BufferedImage.TYPE_INT_RGB);
      Graphics2D g=sheet.createGraphics(); g.setColor(Color.DARK_GRAY); g.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
      for (int fl=0; fl<Filters.COUNT+1 && fl<12; fl++){
        int[] px=src.getRGB(0,0,tw,th,null,0,tw);
        long t0=System.nanoTime();
        if (fl<Filters.COUNT) Filters.apply(new IntImage(px,tw,th), fl, 0,0,0);
        BufferedImage o=new BufferedImage(tw,th,BufferedImage.TYPE_INT_RGB); o.setRGB(0,0,tw,th,px,0,tw);
        int cx=(fl%6)*tw, cy=(fl/6)*(th+20);
        g.drawImage(o,cx,cy,null); g.setColor(Color.YELLOW); g.drawString(fl<Filters.COUNT?Filters.NAMES[fl]:"-", cx+5, cy+th+14);
      }
      ImageIO.write(sheet,"png",new File(System.getProperty("java.io.tmpdir"), "filters_"+type+".png"));
    }
    // 3) speed on a 12MP image
    int W=3000,H=4000; int[] big=new int[W*H]; Random r=new Random(1); for(int i=0;i<big.length;i++) big[i]=0xff000000|r.nextInt(0xffffff);
    for (int fl : new int[]{1,2,4,5,9}) { long t0=System.nanoTime(); Filters.apply(new IntImage(big,W,H), fl,0,0,0); System.out.println("12MP "+Filters.NAMES[fl]+": "+(System.nanoTime()-t0)/1000000+"ms"); }
  }
}
