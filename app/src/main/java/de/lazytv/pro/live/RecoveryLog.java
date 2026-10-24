package de.lazytv.pro.live;

import android.util.Log;
import de.lazytv.pro.catalog.*;

/** Never log URL paths/queries, provider credentials, raw IDs or exception messages. */
final class RecoveryLog {
  static void event(
      String provider,
      StreamItem item,
      ResolvedStream rejected,
      int status,
      int attempt,
      ResolvedStream fresh) {
    String host = "n/a";
    try {
      host = new java.net.URL(rejected.url).getHost();
    } catch (Exception ignored) {
    }
    String id =
        item.id == null
            ? "none"
            : okio.ByteString.encodeUtf8(item.id).sha256().hex().substring(0, 12);
    Log.i(
        "LazyTV-Recovery",
        "provider="
            + provider
            + " channelRef="
            + id
            + " host="
            + host
            + " http="
            + status
            + " refreshAttempt="
            + attempt
            + " fresh="
            + (fresh != null)
            + " urlChanged="
            + (fresh != null && !rejected.url.equals(fresh.url)));
  }
}
