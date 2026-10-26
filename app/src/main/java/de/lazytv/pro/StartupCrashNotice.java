package de.lazytv.pro;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.widget.*;

/** Show the persisted failure after reopening; no provider URLs, tokens or credentials. */
public final class StartupCrashNotice {
  public static void showIfNew(Activity activity) {
    if(activity.isFinishing()||activity.isDestroyed())return;
    android.content.SharedPreferences prefs=activity.getSharedPreferences("crash_report",Context.MODE_PRIVATE);
    String report=prefs.getString("last","");
    if(Build.VERSION.SDK_INT>=30){
      try{
        ActivityManager manager=(ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
        java.util.List<android.app.ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(activity.getPackageName(),0,1);
        if(!exits.isEmpty()){
          android.app.ApplicationExitInfo exit=exits.get(0);int reason=exit.getReason();
          if((reason==android.app.ApplicationExitInfo.REASON_CRASH_NATIVE||reason==android.app.ApplicationExitInfo.REASON_LOW_MEMORY||reason==android.app.ApplicationExitInfo.REASON_ANR)
              &&exit.getTimestamp()>prefs.getLong("system_exit_seen",0)){
            report="LazyTV "+BuildConfig.VERSION_NAME+" previous process exit="+reason+" time="+exit.getTimestamp()+" status="+exit.getStatus()+" pssKB="+exit.getPss()+" rssKB="+exit.getRss()+"\n"+report;
            prefs.edit().putLong("system_exit_seen",exit.getTimestamp()).apply();
          }
        }
      }catch(RuntimeException unavailable){/* Older Fire OS builds may not expose process exit history. */}
    }
    if(report.isEmpty()||report.equals(prefs.getString("shown","")))return;
    prefs.edit().putString("shown",report).apply();
    TextView text=new TextView(activity);text.setText(report);text.setTextIsSelectable(true);text.setPadding(20,16,20,16);
    ScrollView scroll=new ScrollView(activity);scroll.addView(text);
    final String copy=report;
    new AlertDialog.Builder(activity).setTitle("Raporti i mbylljes së mëparshme").setView(scroll)
      .setPositiveButton("Kopjo raportin",(dialog,which)->{
        ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("LazyTV crash",copy));
      }).setNegativeButton("Mbyll",null).show();
  }
  private StartupCrashNotice(){}
}
