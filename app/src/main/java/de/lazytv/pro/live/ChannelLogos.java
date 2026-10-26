package de.lazytv.pro.live;

import android.graphics.*;
import java.io.*;
import java.net.*;

/**
 * Bounded network input and sampled bitmap; provider artwork cannot allocate an arbitrary bitmap.
 */
final class ChannelLogos {
  static Bitmap load(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(6000);
    connection.setReadTimeout(8000);
    try {
      de.lazytv.pro.catalog.RequestCancellation.register(connection);
      if (connection.getContentLength() > 2 * 1024 * 1024) throw new IOException();
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (InputStream input = connection.getInputStream()) {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
          if (output.size() + n > 2 * 1024 * 1024) throw new IOException();
          output.write(buffer, 0, n);
        }
      }
      byte[] bytes = output.toByteArray();
      BitmapFactory.Options bounds = new BitmapFactory.Options();
      bounds.inJustDecodeBounds = true;
      BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
      bounds.inSampleSize = 1;
      while (bounds.outWidth / bounds.inSampleSize > 128
          || bounds.outHeight / bounds.inSampleSize > 128) bounds.inSampleSize *= 2;
      bounds.inJustDecodeBounds = false;
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    } finally {
      de.lazytv.pro.catalog.RequestCancellation.unregister(connection);
      connection.disconnect();
    }
  }
}
