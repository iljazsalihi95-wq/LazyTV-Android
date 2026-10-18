package de.lazytv.pro.stb;
/** Fit decoded pixels and sample aspect ratio into the current attached preview/fullscreen frame. */
final class StbVideoBounds {
 static int[] fit(int width,int height,int videoWidth,int videoHeight,int sarNum,int sarDen){
  if(width<=0||height<=0||videoWidth<=0||videoHeight<=0)return new int[]{-1,-1};
  double ratio=(double)videoWidth/videoHeight;if(sarNum>0&&sarDen>0)ratio*=((double)sarNum/sarDen);
  int w=width,h=(int)Math.round(width/ratio);if(h>height){h=height;w=(int)Math.round(height*ratio);}
  return new int[]{Math.max(1,Math.min(width,w)),Math.max(1,Math.min(height,h))};
 }
}
