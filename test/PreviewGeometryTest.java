import java.awt.geom.*;
public class PreviewGeometryTest {
  // Android Matrix post-ops == pre-concatenate in AffineTransform terms
  static AffineTransform newM(float n0,float n1,int vw,int vh,int rot){
    AffineTransform m=new AffineTransform();
    m.preConcatenate(AffineTransform.getScaleInstance(n0/vw,n1/vh));
    m.preConcatenate(AffineTransform.getTranslateInstance(-n0/2,-n1/2));
    m.preConcatenate(AffineTransform.getRotateInstance(Math.toRadians(rot)));
    float rw=rot%180!=0?n1:n0, rh=rot%180!=0?n0:n1; float k=Math.max(vw/rw,vh/rh);
    m.preConcatenate(AffineTransform.getScaleInstance(k,k));
    m.preConcatenate(AffineTransform.getTranslateInstance(vw/2.0,vh/2.0));
    return m;
  }
  // old Camera2Basic-style transform (pw x ph = preview size, rotation index 0..3)
  static AffineTransform oldM(int pw,int ph,int vw,int vh,int rotation){
    AffineTransform m=new AffineTransform(); double cx=vw/2.0, cy=vh/2.0;
    if(rotation==1||rotation==3){
      // setRectToRect(view -> buf centred), FILL
      double bw=ph, bh=pw; double sx=bw/vw, sy=bh/vh;
      m.preConcatenate(AffineTransform.getScaleInstance(sx,sy));
      m.preConcatenate(AffineTransform.getTranslateInstance(cx-bw/2, cy-bh/2));
      double s=Math.max(vh/(double)ph, vw/(double)pw);
      AffineTransform t=new AffineTransform(); t.translate(cx,cy); t.scale(s,s); t.translate(-cx,-cy); m.preConcatenate(t);
      AffineTransform r=new AffineTransform(); r.rotate(Math.toRadians(90*(rotation-2)),cx,cy); m.preConcatenate(r);
    } else if(rotation==2){ AffineTransform r=new AffineTransform(); r.rotate(Math.PI,cx,cy); m.preConcatenate(r);}
    return m;
  }
  static int fails=0;
  /** content (natural orientation, n0 x n1) stretched by TextureView into vw x vh, then M. */
  static String check(String name, float n0,float n1,int vw,int vh,AffineTransform M,int expectRot){
    AffineTransform T=new AffineTransform(M); T.concatenate(AffineTransform.getScaleInstance(vw/n0,vh/n1));
    double[] m=new double[6]; T.getMatrix(m); // [a c tx ; b d ty]
    double ex=Math.hypot(m[0],m[1]), ey=Math.hypot(m[2],m[3]);
    double distortion=Math.max(ex,ey)/Math.min(ex,ey);
    double ang=Math.toDegrees(Math.atan2(m[1],m[0])); ang=(ang%360+360)%360;
    Point2D c=T.transform(new Point2D.Double(n0/2,n1/2),null);
    boolean centred=Math.abs(c.getX()-vw/2.0)<0.5&&Math.abs(c.getY()-vh/2.0)<0.5;
    Rectangle2D b=T.createTransformedShape(new Rectangle2D.Double(0,0,n0,n1)).getBounds2D();
    boolean fills=b.getWidth()>=vw-0.5&&b.getHeight()>=vh-0.5;
    boolean ok=distortion<1.001&&Math.abs(ang-expectRot)<0.5&&centred&&fills;
    return String.format("%-44s stretch x%.2f  turn %3.0f° (want %3d)  %s",name,distortion,ang,expectRot,ok?"OK":"WRONG");
  }
  public static void main(String[] a){
    int pw=1440, ph=1080;
    Object[][] cases={
      // name, sensor, display deg, view w, view h
      {"Phone portrait (sensor 90)",90,0},
      {"Tablet landscape, natural (sensor 0)",0,0},
      {"Tablet portrait, turned left (sensor 0)",0,90},
      {"Tablet portrait, turned right (sensor 0)",0,270},
      {"Tablet landscape, upside down (sensor 0)",0,180},
      {"Tablet portrait (sensor 90 mount)",90,90},
      {"Tablet landscape (sensor 90 mount)",90,0},
    };
    for(Object[] cs:cases){
      String name=(String)cs[0]; int sensor=(Integer)cs[1], disp=(Integer)cs[2];
      float n0= sensor%180!=0?ph:pw, n1= sensor%180!=0?pw:ph;
      int rot=(360-disp)%360;
      // view shaped by applyAspect, scaled to a screen ~1080 px wide
      float uw= rot%180!=0?n1:n0, uh= rot%180!=0?n0:n1;
      int vw=1080, vh=Math.round(1080*uh/uw);
      String rNew=check("NEW "+name, n0,n1,vw,vh,newM(n0,n1,vw,vh,rot),rot);
      // old code: view shape from its own swap rule
      boolean swap=sensor%180!=0; if(disp==90||disp==270) swap=!swap;
      int ovw=1080, ovh= swap? Math.round(1080f*pw/ph) : Math.round(1080f*ph/pw);
      String rOld=check("old "+name, n0,n1,ovw,ovh,oldM(pw,ph,ovw,ovh,disp/90),rot);
      System.out.println(rOld); System.out.println(rNew);
      if(!rNew.endsWith("OK")) fails++;
    }
    // layout not settled yet: view still the wrong shape (e.g. full height before re-measure)
    String r=check("NEW phone, view shape momentarily wrong",1080,1440,1080,2000,newM(1080,1440,1080,2000,0),0);
    System.out.println(r); if(!r.endsWith("OK")) fails++;
    String ro=check("old phone, view shape momentarily wrong",1080,1440,1080,2000,new AffineTransform(),0);
    System.out.println(ro);
    System.out.println(fails==0?"NEW: ALL CASES UPRIGHT AND UNDISTORTED":"NEW: "+fails+" FAILED");
  }
}
