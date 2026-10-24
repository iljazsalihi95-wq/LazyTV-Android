package de.lazytv.pro.catalog;

import java.io.*;
import java.text.*;
import java.util.*;
import okhttp3.*;
import org.xmlpull.v1.*;

/** Per-catalog XMLTV cache; streaming parse, bounded decompressed input, no external entities. */
final class M3uXmltv {
  private String cachedUrl = "";
  private long loaded;
  private Map<String, EpgSchedule> cache = Collections.emptyMap();

  synchronized EpgSchedule schedule(StreamItem item) throws CatalogException {
    String url = item.meta("xmltv_url");
    if (url.isEmpty() || item.tvgId == null || item.tvgId.isEmpty()) return EpgSchedule.empty();
    if (!url.equals(cachedUrl) || System.currentTimeMillis() - loaded > 300000) {
      try {
        Request.Builder request = new Request.Builder().url(url);
        try (Response response =
            new OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request.build())
                .execute()) {
          if (!response.isSuccessful() || response.body() == null) throw new IOException();
          cache = parse(response.body().byteStream(), System.currentTimeMillis());
          cachedUrl = url;
          loaded = System.currentTimeMillis();
        }
      } catch (Exception unavailable) {
        throw new CatalogException("EPG/XMLTV nuk mund të ngarkohet.");
      }
    }
    return cache.getOrDefault(item.tvgId, EpgSchedule.empty());
  }

  static Map<String, EpgSchedule> parse(InputStream input, long now) throws Exception {
    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
    factory.setNamespaceAware(false);
    XmlPullParser parser = factory.newPullParser();
    parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
    InputStream bounded =
        new FilterInputStream(input) {
          long count;

          private void check(int n) throws IOException {
            if (n > 0 && (count += n) > 64L * 1024 * 1024) throw new IOException("XMLTV limit");
          }

          @Override
          public int read() throws IOException {
            int n = in.read();
            check(n < 0 ? 0 : 1);
            return n;
          }

          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            int n = in.read(b, off, len);
            check(n);
            return n;
          }
        };
    parser.setInput(bounded, null);
    Map<String, EpgSchedule.Program[]> pairs = new HashMap<>();
    String channel = "", title = "", description = "";
    long start = 0, end = 0;
    for (int event = parser.getEventType();
        event != XmlPullParser.END_DOCUMENT;
        event = parser.nextToken()) {
      if (event == XmlPullParser.DOCDECL || event == XmlPullParser.ENTITY_REF)
        throw new IOException("XMLTV entities unsupported");
      if (event == XmlPullParser.START_TAG) {
        String name = parser.getName();
        if ("programme".equals(name)) {
          channel = parser.getAttributeValue(null, "channel");
          start = time(parser.getAttributeValue(null, "start"));
          end = time(parser.getAttributeValue(null, "stop"));
          title = "";
          description = "";
        } else if ("title".equals(name)) title = parser.nextText();
        else if ("desc".equals(name)) description = parser.nextText();
      } else if (event == XmlPullParser.END_TAG
          && "programme".equals(parser.getName())
          && channel != null
          && !channel.isEmpty()
          && !title.isEmpty()
          && end > now
          && end > start) {
        if (!pairs.containsKey(channel) && pairs.size() >= 50000)
          throw new IOException("XMLTV channel limit");
        EpgSchedule.Program[] pair =
            pairs.computeIfAbsent(channel, id -> new EpgSchedule.Program[2]);
        EpgSchedule.Program p = new EpgSchedule.Program(title, description, start, end);
        if (start <= now) {
          if (pair[0] == null || start > pair[0].start) pair[0] = p;
        } else if (pair[1] == null || start < pair[1].start) pair[1] = p;
      }
    }
    Map<String, EpgSchedule> result = new HashMap<>();
    for (Map.Entry<String, EpgSchedule.Program[]> e : pairs.entrySet())
      result.put(e.getKey(), new EpgSchedule(e.getValue()[0], e.getValue()[1]));
    return result;
  }

  private static long time(String text) {
    if (text == null) return 0;
    try {
      String value = text.trim();
      SimpleDateFormat format =
          new SimpleDateFormat(
              value.contains(" ") ? "yyyyMMddHHmmss Z" : "yyyyMMddHHmmss", Locale.US);
      format.setTimeZone(TimeZone.getTimeZone("UTC"));
      format.setLenient(false);
      return format.parse(value).getTime();
    } catch (Exception invalid) {
      return 0;
    }
  }
}
