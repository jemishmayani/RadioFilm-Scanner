package com.filmscan.core;
public class DetectTestBridge {
  static void seed(long s){ try { Class<?> c=Class.forName("DetectTest"); c.getField("rnd").set(null,new java.util.Random(s)); } catch(Exception e){throw new RuntimeException(e);} }
  static java.awt.image.BufferedImage content(int t,int w,int h){ try { return (java.awt.image.BufferedImage)Class.forName("DetectTest").getMethod("content",int.class,int.class,int.class).invoke(null,t,w,h);} catch(Exception e){throw new RuntimeException(e);} }
}
