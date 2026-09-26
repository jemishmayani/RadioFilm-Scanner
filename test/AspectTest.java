import com.filmscan.core.*; import java.util.*;
public class AspectTest { public static void main(String[] a){
  Random r=new Random(2); double worstEst=0, worstSide=0; int n=0;
  for(int t=0;t<200;t++){
    double W=3000,H=4000,f=W*(0.8+r.nextDouble()*0.6);
    double rw=1, rh=0.6+r.nextDouble()*1.2; // true ratio w/h = rw/rh
    double ax=Math.toRadians((r.nextDouble()-0.5)*50), ay=Math.toRadians((r.nextDouble()-0.5)*50), az=Math.toRadians((r.nextDouble()-0.5)*30);
    double dist=2.2+r.nextDouble();
    double[][] c={{-rw/2,-rh/2,0},{rw/2,-rh/2,0},{rw/2,rh/2,0},{-rw/2,rh/2,0}}; float[] q=new float[8]; boolean ok=true;
    for(int i=0;i<4;i++){ double x=c[i][0],y=c[i][1],z=c[i][2];
      double y1=y*Math.cos(ax)-z*Math.sin(ax), z1=y*Math.sin(ax)+z*Math.cos(ax); y=y1; z=z1;
      double x1=x*Math.cos(ay)+z*Math.sin(ay); z1=-x*Math.sin(ay)+z*Math.cos(ay); x=x1; z=z1;
      x1=x*Math.cos(az)-y*Math.sin(az); y1=x*Math.sin(az)+y*Math.cos(az); x=x1; y=y1; z+=dist;
      q[2*i]=(float)(W/2+f*x/z); q[2*i+1]=(float)(H/2+f*y/z);
      if(q[2*i]<0||q[2*i]>W||q[2*i+1]<0||q[2*i+1]>H) ok=false; }
    if(!ok) continue; n++;
    double[] sz=Geom.outputSize(q,W,H); double est=sz[0]/sz[1], tr=rw/rh;
    double side=((Geom.dist(q,0,1)+Geom.dist(q,3,2))/2)/((Geom.dist(q,0,3)+Geom.dist(q,1,2))/2);
    worstEst=Math.max(worstEst,Math.abs(est/tr-1)); worstSide=Math.max(worstSide,Math.abs(side/tr-1));
  }
  System.out.printf("n=%d worst ratio error: estimated %.1f%%  side-length %.1f%%%n", n, worstEst*100, worstSide*100);
}}
