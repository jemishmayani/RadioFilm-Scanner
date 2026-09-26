import com.filmscan.core.*; import java.awt.image.*; import java.util.*;
public class SnapTest { public static void main(String[] a) throws Exception {
  int okC=0, totC=0, okS=0, totS=0, nullC=0, nullS=0; double sumC=0, sumS=0;
  for (int t=0;t<30;t++){
    DetectTest.rnd = new Random(500+t);
    Random rnd = DetectTest.rnd;
    int W=1200,H=1600; int type=t%3==1?1:0, bg=t%3;
    float[] q = {W*0.2f+rnd.nextInt(80), H*0.15f+rnd.nextInt(80), W*0.8f-rnd.nextInt(80), H*0.17f+rnd.nextInt(80), W*0.82f-rnd.nextInt(80), H*0.85f-rnd.nextInt(80), W*0.18f+rnd.nextInt(80), H*0.83f-rnd.nextInt(80)};
    BufferedImage sc = DetectTest.scene(W,H,q,DetectTest.content(type,900,1200),bg);
    BufferedImage sm = DetectTest.scaleTo(sc, 1024);
    int w=sm.getWidth(), h=sm.getHeight(); float s = w/(float)W;
    EdgeMap em = EdgeMap.fromArgb(sm.getRGB(0,0,w,h,null,0,w), w, h, 1);
    float[] tq = Geom.scale(q, s, s);
    for (int c=0;c<4;c++){
      float[] pq = tq.clone(); double ang = rnd.nextDouble()*6.28, r = 6+rnd.nextDouble()*16;
      pq[2*c]+= (float)(r*Math.cos(ang)); pq[2*c+1]+=(float)(r*Math.sin(ang));
      float[] p = em.snapCorner(pq, c, 26, 1); totC++;
      if (p==null){nullC++; continue;}
      double e = Math.hypot(p[0]-tq[2*c], p[1]-tq[2*c+1]); sumC+=e; if (e<2.5) okC++;
    }
    for (int sd=0; sd<4; sd++){
      float[] pq = tq.clone(); int e2=(sd+1)&3; float dx=tq[2*e2]-tq[2*sd], dy=tq[2*e2+1]-tq[2*sd+1]; float len=(float)Math.hypot(dx,dy);
      float off = (rnd.nextFloat()-0.5f)*30; float nx=-dy/len*off, ny=dx/len*off;
      pq[2*sd]+=nx; pq[2*sd+1]+=ny; pq[2*e2]+=nx; pq[2*e2+1]+=ny;
      float[] r = em.snapSide(pq, sd, 20); totS++;
      if (r==null){nullS++; continue;}
      double e = Math.max(Math.hypot(r[2*sd]-tq[2*sd], r[2*sd+1]-tq[2*sd+1]), Math.hypot(r[2*e2]-tq[2*e2], r[2*e2+1]-tq[2*e2+1]));
      sumS+=e; if (e<3) okS++;
    }
  }
  System.out.printf("corner: ok %d/%d null %d meanErr %.2fpx | side: ok %d/%d null %d meanErr %.2fpx%n", okC,totC,nullC,sumC/(totC-nullC), okS,totS,nullS,sumS/(totS-nullS));
}}
