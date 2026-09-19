package de.lazytv.pro.activation;
import android.app.Activity;import android.content.Intent;
public final class ActivationGuard {private ActivationGuard(){} public static boolean enforce(Activity a){if(new ActivationCache(a).validOfflineGrace(ActivationGateActivity.OFFLINE_GRACE_MS))return true;Intent i=new Intent(a,ActivationGateActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_NEW_TASK);a.startActivity(i);a.finish();return false;}}
