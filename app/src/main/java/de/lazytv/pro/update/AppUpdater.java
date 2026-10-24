package de.lazytv.pro.update;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import de.lazytv.pro.BuildConfig;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

/** Visible, user-confirmed package installation. Existing package/signature must match. */
public final class AppUpdater implements AutoCloseable {
  public static final String MANIFEST =
      "https://github.com/iljazsalihi95-wq/LazyTV-Android/releases/latest/download/update.json";
  private final Activity owner;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final OkHttpClient http =
      new OkHttpClient.Builder()
          .connectTimeout(15, TimeUnit.SECONDS)
          .readTimeout(30, TimeUnit.SECONDS)
          .callTimeout(10, TimeUnit.MINUTES)
          .build();
  private volatile boolean closed, busy;
  private volatile Call active;
  private AlertDialog dialog;
  private File ready;
  private UpdateManifest pending;
  private boolean waitingForPermission;

  public AppUpdater(Activity owner) {
    this.owner = owner;
  }

  public void check() {
    if (closed || busy) return;
    busy = true;
    io.execute(
        () -> {
          try {
            String json;
            try (Response response = execute(MANIFEST)) {
              if (!response.isSuccessful() || response.body() == null) throw new IOException();
              if (response.body().contentLength() > 65536) throw new IOException();
              try (InputStream stream = response.body().byteStream()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = stream.read(buffer)) != -1) {
                  if (bytes.size() + n > 65536) throw new IOException();
                  bytes.write(buffer, 0, n);
                }
                json = bytes.toString("UTF-8");
              }
            }
            UpdateManifest update = new UpdateManifest(json);
            if (update.newerThan(BuildConfig.VERSION_CODE)) {
              busy = false;
              post(() -> offer(update));
            }
          } catch (Exception unavailable) {
            android.util.Log.i("LazyTV-Update", "Public release check unavailable");
          } finally {
            busy = false;
          }
        });
  }

  private Response execute(String url) throws IOException {
    Call call = http.newCall(new Request.Builder().url(url).build());
    active = call;
    return call.execute();
  }

  private void offer(UpdateManifest update) {
    if (owner.isFinishing() || owner.isDestroyed()) return;
    dialog =
        new AlertDialog.Builder(owner)
            .setTitle("Version i ri i LazyTV PRO është gati")
            .setMessage("LazyTV PRO " + update.versionName)
            .setPositiveButton("UPDATE", (d, w) -> download(update))
            .setNegativeButton("MË VONË", null)
            .show();
  }

  private void download(UpdateManifest update) {
    if (closed || busy) return;
    busy = true;
    dialog =
        new AlertDialog.Builder(owner)
            .setTitle("LazyTV PRO UPDATE")
            .setMessage("Duke shkarkuar APK-në…")
            .setCancelable(false)
            .setNegativeButton(
                "Anulo",
                (d, w) -> {
                  Call call = active;
                  if (call != null) call.cancel();
                })
            .show();
    io.execute(
        () -> {
          File partial = null;
          try {
            File dir = new File(owner.getCacheDir(), "updates");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException();
            partial = new File(dir, "release.part");
            File apk = new File(dir, "LazyTV-PRO.apk");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long bytes = 0;
            try (Response response = execute(update.apkUrl)) {
              if (!response.isSuccessful() || response.body() == null) throw new IOException();
              long expected = response.body().contentLength();
              if (expected > 512L * 1024 * 1024) throw new IOException();
              try (InputStream input = response.body().byteStream();
                  FileOutputStream output = new FileOutputStream(partial)) {
                byte[] buffer = new byte[32768];
                int n;
                while (!closed && (n = input.read(buffer)) != -1) {
                  bytes += n;
                  if (bytes > 512L * 1024 * 1024) throw new IOException();
                  digest.update(buffer, 0, n);
                  output.write(buffer, 0, n);
                }
                output.getFD().sync();
              }
              if (closed || bytes == 0 || (expected >= 0 && bytes != expected))
                throw new IOException();
            }
            if (!hex(digest.digest()).equals(update.sha256)) throw new SecurityException("SHA256");
            verifyPackage(partial, update);
            if (apk.exists() && !apk.delete()) throw new IOException();
            if (!partial.renameTo(apk)) throw new IOException();
            post(
                () -> {
                  dismiss();
                  ready = apk;
                  pending = update;
                  install();
                });
          } catch (Exception failure) {
            if (partial != null) partial.delete();
            post(
                () -> {
                  dismiss();
                  message(
                      failure instanceof SecurityException
                          ? "Përditësimi u refuzua: versioni, paketa, SHA-256 ose nënshkrimi nuk"
                                + " përputhet."
                          : "Shkarkimi nuk përfundoi. Provo përsëri më vonë.");
                });
          } finally {
            busy = false;
          }
        });
  }

  @SuppressWarnings("deprecation")
  private void verifyPackage(File file, UpdateManifest update) throws Exception {
    PackageManager pm = owner.getPackageManager();
    int flags =
        Build.VERSION.SDK_INT >= 28
            ? PackageManager.GET_SIGNING_CERTIFICATES
            : PackageManager.GET_SIGNATURES;
    PackageInfo archive = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags),
        installed = pm.getPackageInfo(owner.getPackageName(), flags);
    if (archive == null || !owner.getPackageName().equals(archive.packageName))
      throw new SecurityException();
    long version = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
    if (version != update.versionCode || !update.newerThan(BuildConfig.VERSION_CODE))
      throw new SecurityException();
    Signature[] incoming = signatures(archive), current = signatures(installed);
    if (incoming == null
        || current == null
        || incoming.length == 0
        || !new HashSet<>(Arrays.asList(incoming)).equals(new HashSet<>(Arrays.asList(current))))
      throw new SecurityException();
  }

  @SuppressWarnings("deprecation")
  private static Signature[] signatures(PackageInfo p) {
    return Build.VERSION.SDK_INT >= 28 && p.signingInfo != null
        ? p.signingInfo.getApkContentsSigners()
        : p.signatures;
  }

  private void install() {
    if (ready == null || pending == null || !ready.isFile()) return;
    try {
      verifyPackage(ready, pending);
      if (Build.VERSION.SDK_INT >= 26 && !owner.getPackageManager().canRequestPackageInstalls()) {
        waitingForPermission = true;
        message("Lejo instalimin nga LazyTV PRO, pastaj kthehu në aplikacion.");
        owner.startActivity(
            new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + owner.getPackageName())));
        return;
      }
      Uri uri = FileProvider.getUriForFile(owner, owner.getPackageName() + ".updates", ready);
      Intent installer =
          new Intent(Intent.ACTION_VIEW)
              .setDataAndType(uri, "application/vnd.android.package-archive")
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      installer.setClipData(ClipData.newRawUri("LazyTV update", uri));
      owner.startActivity(installer);
      ready = null;
      pending = null;
    } catch (ActivityNotFoundException unavailable) {
      message("Hap cilësimet e pajisjes dhe lejo instalimin e aplikacioneve nga ky burim.");
    } catch (Exception rejected) {
      message(
          "Instalimi nuk mund të hapet ose nënshkrimi nuk përputhet me aplikacionin e instaluar.");
    }
  }

  public void resume() {
    if (waitingForPermission) {
      waitingForPermission = false;
      if (Build.VERSION.SDK_INT < 26 || owner.getPackageManager().canRequestPackageInstalls())
        install();
    }
  }

  private static String hex(byte[] data) {
    StringBuilder text = new StringBuilder();
    for (byte b : data) text.append(String.format(Locale.US, "%02x", b & 255));
    return text.toString();
  }

  private void post(Runnable task) {
    if (!closed)
      ui.post(
          () -> {
            if (!closed && !owner.isDestroyed() && !owner.isFinishing()) task.run();
          });
  }

  private void dismiss() {
    if (dialog != null) {
      dialog.dismiss();
      dialog = null;
    }
  }

  private void message(String text) {
    Toast.makeText(owner, text, Toast.LENGTH_LONG).show();
  }

  public void close() {
    closed = true;
    Call call = active;
    if (call != null) call.cancel();
    io.shutdownNow();
    ui.removeCallbacksAndMessages(null);
    dismiss();
  }
}
