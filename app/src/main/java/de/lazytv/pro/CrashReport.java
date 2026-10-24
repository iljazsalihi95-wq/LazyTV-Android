package de.lazytv.pro;

import android.content.Context;
import java.util.*;

/** Exception types + code locations only. Throwable messages can contain stream secrets. */
public final class CrashReport {
  public static String describe(String phase, Throwable error) {
    StringBuilder safe =
        new StringBuilder("LazyTV ")
            .append(BuildConfig.VERSION_NAME)
            .append(" phase=")
            .append(phase);
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
    while (error != null && seen.add(error) && safe.length() < 12000) {
      safe.append("\nexception=").append(error.getClass().getName());
      for (StackTraceElement frame : error.getStackTrace()) {
        if (safe.length() > 12000) break;
        safe.append('\n').append(frame.toString());
      }
      error = error.getCause();
    }
    return safe.toString();
  }

  static void save(Context context, String report) {
    context
        .getSharedPreferences("crash_report", Context.MODE_PRIVATE)
        .edit()
        .putString("last", report)
        .commit();
  }

  public static String read(Context context) {
    return context
        .getSharedPreferences("crash_report", Context.MODE_PRIVATE)
        .getString(
            "last",
            "Nuk ka raport Java crash të ruajtur. Mbylljet native ose nga sistemi mund të kërkojnë"
                + " logun e pajisjes.");
  }
}
