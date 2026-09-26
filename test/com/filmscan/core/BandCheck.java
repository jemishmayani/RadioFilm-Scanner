package com.filmscan.core;
public class BandCheck { public static void main(String[] a){
  java.util.Random r=new java.util.Random(5); int w=300,h=460; int[] base=new int[w*h]; for(int i=0;i<base.length;i++) base[i]=0xff000000|r.nextInt(0xffffff);
  for (int fl=0; fl<Filters.COUNT; fl++){ int[] p1=base.clone(), p2=base.clone();
    Filters.BAND_OVERRIDE=0; Filters.apply(new IntImage(p1,w,h), fl, 10,20,50);
    Filters.BAND_OVERRIDE=13; Filters.apply(new IntImage(p2,w,h), fl, 10,20,50);
    int md=0; for(int i=0;i<p1.length;i++) for(int s=0;s<24;s+=8) md=Math.max(md, Math.abs(((p1[i]>>s)&255)-((p2[i]>>s)&255)));
    System.out.print(Filters.NAMES[fl]+" maxdiff="+md+"; ");
  } } }
