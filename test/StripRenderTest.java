import java.util.Random;
/** Simulates renderInStrips vs the one-piece render: same sampling, same transforms, pixel-compare. */
public class StripRenderTest {
  static int W=2400,H=1800; static float[] img=new float[W*H]; static int S=1;
  // 3x3 homography helpers (row-major), mimicking android.graphics.Matrix.setPolyToPoly/invert/mapPoints
  static double[] polyToPoly(float[] s,float[] d){
    double[][] A=new double[8][9];
    for(int i=0;i<4;i++){double x=s[2*i],y=s[2*i+1],u=d[2*i],v=d[2*i+1];
      A[2*i]=new double[]{x,y,1,0,0,0,-u*x,-u*y,u}; A[2*i+1]=new double[]{0,0,0,x,y,1,-v*x,-v*y,v};}
    for(int c=0;c<8;c++){int p=c;for(int r=c+1;r<8;r++)if(Math.abs(A[r][c])>Math.abs(A[p][c]))p=r;double[]t=A[c];A[c]=A[p];A[p]=t;
      for(int r=0;r<8;r++)if(r!=c){double f=A[r][c]/A[c][c];for(int k=c;k<9;k++)A[r][k]-=f*A[c][k];}}
    double[] h=new double[9];for(int i=0;i<8;i++)h[i]=A[i][8]/A[i][i];h[8]=1;return h;}
  static double[] mul(double[] a,double[] b){double[] r=new double[9];for(int i=0;i<3;i++)for(int j=0;j<3;j++){double s=0;for(int k=0;k<3;k++)s+=a[3*i+k]*b[3*k+j];r[3*i+j]=s;}return r;}
  static double[] inv(double[] m){double a=m[0],b=m[1],c=m[2],d=m[3],e=m[4],f=m[5],g=m[6],h=m[7],i=m[8];
    double A=e*i-f*h,B=-(d*i-f*g),C=d*h-e*g,det=a*A+b*B+c*C;
    return new double[]{A/det,-(b*i-c*h)/det,(b*f-c*e)/det,B/det,(a*i-c*g)/det,-(a*f-c*d)/det,C/det,-(a*h-b*g)/det,(a*e-b*d)/det};}
  static double[] map(double[] m,double x,double y){double w=m[6]*x+m[7]*y+m[8];return new double[]{(m[0]*x+m[1]*y+m[2])/w,(m[3]*x+m[4]*y+m[5])/w};}
  /** decodeRegion(rect, sample): box-average s x s blocks from the rect origin; size = ceil(w/s). */
  static float[][] decode(int l,int t,int r,int b,int s){int pw=(r-l+s-1)/s,ph=(b-t+s-1)/s;float[] px=new float[pw*ph];
    for(int y=0;y<ph;y++)for(int x=0;x<pw;x++){double sum=0;int n=0;for(int yy=t+y*s;yy<Math.min(b,t+y*s+s);yy++)for(int xx=l+x*s;xx<Math.min(r,l+x*s+s);xx++){sum+=img[yy*W+xx];n++;}px[y*pw+x]=(float)(sum/n);}
    return new float[][]{px,{pw,ph}};}
  /** draw part through matrix m (part px -> output px), bilinear at pixel centres, only rows y0..y1 */
  static void draw(float[] out,int fw,int y0,int y1,float[][] part,double[] m){float[] px=part[0];int pw=(int)part[1][0],ph=(int)part[1][1];double[] mi=inv(m);
    for(int y=y0;y<y1;y++)for(int x=0;x<fw;x++){double[] u=map(mi,x+0.5,y+0.5);double sx=u[0]-0.5,sy=u[1]-0.5;
      if(sx<-0.5||sy<-0.5||sx>pw-0.5||sy>ph-0.5){out[y*fw+x]=-1;continue;} // outside the decoded part = hole
      int x0=(int)Math.floor(sx),y0_=(int)Math.floor(sy);double fx=sx-x0,fy=sy-y0_;
      double v=0;for(int dy=0;dy<2;dy++)for(int dx=0;dx<2;dx++){int xx=Math.max(0,Math.min(pw-1,x0+dx)),yy=Math.max(0,Math.min(ph-1,y0_+dy));v+=px[yy*pw+xx]*(dx==0?1-fx:fx)*(dy==0?1-fy:fy);}
      out[y*fw+x]=(float)v;}}
  static double[] partToOut(int l,int t,int r,int b,float[][] part,double[] full){double sx=S,sy=S;
    double[] m={sx,0,l,0,sy,t,0,0,1};return mul(full,m);}
  public static void main(String[] a){
    Random rnd=new Random(4);for(int y=0;y<H;y++)for(int x=0;x<W;x++)img[y*W+x]=(float)(128+60*Math.sin(x*0.07)*Math.cos(y*0.05)+((x/9+y/13)%2)*40);
    int fails=0;
    for(int trial=0;trial<12;trial++){
      int s=new int[]{1,2,4}[trial%3]; S=s; int rot=trial%4;
      float[] qr={300+rnd.nextInt(200),200+rnd.nextInt(200), 2000+rnd.nextInt(300),150+rnd.nextInt(250), 2200+rnd.nextInt(150),1600+rnd.nextInt(150), 150+rnd.nextInt(200),1500+rnd.nextInt(250)};
      int outW=900/(s>1?1:1)+trial*7, outH=640+trial*5;
      int fw=(rot&1)==0?outW:outH, fh=(rot&1)==0?outH:outW; float[] c={0,0,fw,0,fw,fh,0,fh}; float[] dst=new float[8];
      for(int i=0;i<4;i++){int j=(i+rot)&3;dst[2*i]=c[2*j];dst[2*i+1]=c[2*j+1];}
      double[] full=polyToPoly(qr,dst), finv=inv(full);
      // one piece: bounding box of the quad, pad 2 (as the existing code)
      float mnx=1e9f,mny=1e9f,mxx=-1,mxy=-1;for(int i=0;i<4;i++){mnx=Math.min(mnx,qr[2*i]);mxx=Math.max(mxx,qr[2*i]);mny=Math.min(mny,qr[2*i+1]);mxy=Math.max(mxy,qr[2*i+1]);}
      int L=Math.max(0,(int)Math.floor(mnx)-2),T=Math.max(0,(int)Math.floor(mny)-2),R=Math.min(W,(int)Math.ceil(mxx)+2),B=Math.min(H,(int)Math.ceil(mxy)+2);
      // align like the app does for strips so both sample the same grid
      L=L/s*s; T=T/s*s; R=Math.min(W,(R+s-1)/s*s); B=Math.min(H,(B+s-1)/s*s);
      float[] one=new float[fw*fh]; float[][] part=decode(L,T,R,B,s); draw(one,fw,0,fh,part,partToOut(L,T,R,B,part,full));
      // strips
      float[] st=new float[fw*fh]; int strip=Math.max(64,Math.min(fh,(int)(60000/fw))); int pad=3*s+2; int strips=0;
      for(int y0=0;y0<fh;y0+=strip){int y1=Math.min(fh,y0+strip); double[][] pts={map(finv,0,y0),map(finv,fw,y0),map(finv,fw,y1),map(finv,0,y1)};
        double a0=1e18,b0=1e18,a1=-1e18,b1=-1e18;for(double[] p:pts){a0=Math.min(a0,p[0]);a1=Math.max(a1,p[0]);b0=Math.min(b0,p[1]);b1=Math.max(b1,p[1]);}
        int l=Math.max(0,((int)Math.floor(a0)-pad)/s*s),t=Math.max(0,((int)Math.floor(b0)-pad)/s*s),r=Math.min(W,((int)Math.ceil(a1)+pad+s-1)/s*s),b=Math.min(H,((int)Math.ceil(b1)+pad+s-1)/s*s);
        float[][] pp=decode(l,t,r,b,s); draw(st,fw,y0,y1,pp,partToOut(l,t,r,b,pp,full)); strips++;}
      int holes=0; double maxd=0; for(int i=0;i<fw*fh;i++){ if(st[i]<0&&one[i]>=0)holes++; if(st[i]>=0&&one[i]>=0) maxd=Math.max(maxd,Math.abs(st[i]-one[i])); }
      boolean ok=holes==0&&maxd<0.01; if(!ok)fails++;
      System.out.printf("trial %2d sample=%d rot=%d out=%dx%d strips=%2d holes=%d maxDiff=%.5f %s%n",trial,s,rot,fw,fh,strips,holes,maxd,ok?"OK":"FAIL");
    }
    System.out.println(fails==0?"STRIPS MATCH ONE-PIECE RENDER":"FAILURES: "+fails);
  }
}
