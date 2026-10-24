package de.lazytv.pro.update;

import java.net.URI;
import org.json.JSONObject;

/** Public release metadata only; no expiring Actions URLs or authenticated downloads. */
public final class UpdateManifest {
  public final int versionCode;
  public final String versionName, apkUrl, sha256;

  public UpdateManifest(String json) throws Exception {
    JSONObject obj = new JSONObject(json);
    versionCode = obj.getInt("versionCode");
    versionName = obj.getString("versionName");
    apkUrl = obj.getString("apkUrl");
    sha256 = obj.getString("sha256").toLowerCase(java.util.Locale.US);
    if (versionCode <= 0 || versionName.length() > 80 || !sha256.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid release metadata");
    URI uri = new URI(apkUrl);
    if (!"https".equals(uri.getScheme())
        || !"github.com".equals(uri.getHost())
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !uri.getPath().startsWith("/iljazsalihi95-wq/LazyTV-Android/releases/download/")
        || !uri.getPath().endsWith(".apk"))
      throw new IllegalArgumentException("APK must be a public LazyTV release asset");
  }

  public boolean newerThan(long installed) {
    return versionCode > installed;
  }
}
