package de.lazytv.pro.activation;
import android.app.Activity;import android.content.Intent;import de.lazytv.pro.BuildConfig;
public final class ActivationGuard {
 private ActivationGuard(){}
 public static boolean enforce(Activity a){
  if(BuildConfig.DEBUG && BuildConfig.DEV_ACTIVATION_BYPASS)return true;
  if(new ActivationCache(a).validOfflineGrace(ActivationGateActivity.OFFLINE_GRACE_MS))return true;
  Intent i=new Intent(a,ActivationGateActivity.class);
  i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);
  a.startActivity(i);a.finish();return false;
 }
}