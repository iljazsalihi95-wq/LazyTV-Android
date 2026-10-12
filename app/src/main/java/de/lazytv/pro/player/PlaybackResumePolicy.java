package de.lazytv.pro.player;
public final class PlaybackResumePolicy {
 private PlaybackResumePolicy(){}
 public static boolean eligible(long position,long duration,long ageMillis){
  return position>=10000&&(duration<=0||position<duration&&duration-position>90000)&&ageMillis<=30L*24*60*60*1000;
 }
}
