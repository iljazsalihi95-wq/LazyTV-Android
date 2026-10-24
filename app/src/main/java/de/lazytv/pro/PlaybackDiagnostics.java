package de.lazytv.pro;
import android.app.*;import android.os.Bundle;import android.util.Log;
/** Lifecycle/crash facts only: no Throwable messages, URLs, extras or credentials. */
public final class PlaybackDiagnostics extends Application implements Application.ActivityLifecycleCallbacks {
 private volatile String phase="application";
 @Override public void onCreate(){super.onCreate();registerActivityLifecycleCallbacks(this);
  Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   String safe=CrashReport.describe(phase,error);
   Log.e("LazyTV-Crash",safe);
   try{CrashReport.save(this,safe);}catch(RuntimeException unavailable){Log.e("LazyTV-Crash","Crash report could not be persisted");}
   // Keep default Android crash behavior; never swallow a fatal error.
   if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
  });
 }
 private void mark(Activity a,String event){phase=a.getClass().getSimpleName()+":"+event;Log.i("LazyTV-Lifecycle",phase);}
 public void onActivityCreated(Activity a,Bundle b){mark(a,"created");}public void onActivityStarted(Activity a){mark(a,"started");}
 public void onActivityResumed(Activity a){mark(a,"resumed");}public void onActivityPaused(Activity a){mark(a,"paused");}
 public void onActivityStopped(Activity a){mark(a,"stopped");}public void onActivityDestroyed(Activity a){mark(a,"destroyed");}
 public void onActivitySaveInstanceState(Activity a,Bundle b){mark(a,"saveState");}
}
