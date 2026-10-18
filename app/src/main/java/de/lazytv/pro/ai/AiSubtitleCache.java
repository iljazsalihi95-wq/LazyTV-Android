package de.lazytv.pro.ai;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Persistent private cache for generated/translated subtitle documents.
 * Provider credentials and stream URLs are never used as file names and are
 * never written to logs. A stable media identity + language produces one file.
 */
public final class AiSubtitleCache {
    private static final long MAX_ENTRY_BYTES = 2_000_000L;
    private final File directory;

    public AiSubtitleCache(Context context) {
        directory = new File(context.getApplicationContext().getFilesDir(), "ai_subtitles");
        if (!directory.exists()) directory.mkdirs();
    }

    public String get(String mediaIdentity, String language) {
        File file = file(mediaIdentity, language);
        if (!file.isFile() || file.length() <= 0 || file.length() > MAX_ENTRY_BYTES) return null;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int offset = 0;
            while (offset < data.length) {
                int read = input.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            if (offset != data.length) return null;
            String value = new String(data, StandardCharsets.UTF_8).trim();
            return value.isEmpty() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean put(String mediaIdentity, String language, String subtitleDocument) {
        if (subtitleDocument == null) return false;
        byte[] data = subtitleDocument.getBytes(StandardCharsets.UTF_8);
        if (data.length == 0 || data.length > MAX_ENTRY_BYTES) return false;
        if (!directory.exists() && !directory.mkdirs()) return false;
        File target = file(mediaIdentity, language);
        File temp = new File(directory, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(data);
            output.flush();
            if (target.exists() && !target.delete()) return false;
            return temp.renameTo(target);
        } catch (Exception ignored) {
            temp.delete();
            return false;
        }
    }

    public void remove(String mediaIdentity, String language) {
        File target = file(mediaIdentity, language);
        if (target.isFile()) target.delete();
    }

    public void clear(){File[] files=directory.listFiles();if(files!=null)for(File f:files)if(f.isFile())f.delete();}

    private File file(String mediaIdentity, String language) {
        String identity = mediaIdentity == null ? "" : mediaIdentity.trim();
        String lang = language == null ? "sq" : language.trim().toLowerCase(java.util.Locale.ROOT);
        return new File(directory, sha256(identity + "|" + lang) + ".json");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return out.toString();
        } catch (Exception impossible) {
            return Integer.toHexString(value.hashCode());
        }
    }
}
