package de.lazytv.pro.ai;

import android.content.Context;
import de.lazytv.pro.BuildConfig;
import de.lazytv.pro.activation.DeviceIdentityManager;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

/** Native client for the owner-controlled LazyTV_Master_Backend AI routes. No OpenAI secret lives in the APK. */
public final class AiBackendClient {
    public static final String BASE_URL = "https://script.google.com/macros/s/AKfycbyHyk5HUeYPJhNaiQ0nUjmb90sDKSLmvShsQzNEXx3ShkxQyoT1ACUoysYVcaCAafEOTg/exec";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 65_000;
    private static final int MAX_RESPONSE_CHARS = 2_000_000;
    private final DeviceIdentityManager identity;
    private String sessionToken="";

    public AiBackendClient(Context context) {
        if (context == null) throw new IllegalArgumentException("Context required");
        identity = new DeviceIdentityManager(context.getApplicationContext());
    }

    public static final class AiException extends Exception {
        public final boolean retryable;
        public AiException(String message, boolean retryable) { super(message); this.retryable = retryable; }
    }

    public JSONObject health() throws AiException { return get("aihealth"); }
    public JSONObject translateSubtitles(JSONObject request) throws AiException { return post("subtitletranslate", request); }
    public JSONObject transcribeSubtitles(JSONObject request) throws AiException { return post("subtitletranscribe", request); }
    public JSONObject translateDescription(JSONObject request) throws AiException { return post("descriptiontranslate", request); }

    private JSONObject get(String action) throws AiException {
        HttpsURLConnection connection = null;
        try {
            connection = open(action);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            addIdentityHeaders(connection);
            return response(connection);
        } catch (AiException e) { throw e; }
        catch (IOException e) { throw new AiException("LazyTV AI service unavailable", true); }
        catch (Exception e) { throw new AiException("Invalid LazyTV AI response", false); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private JSONObject post(String action, JSONObject request) throws AiException {
        if (request == null) throw new AiException("AI request missing", false);
        HttpsURLConnection connection = null;
        try {
            connection = open(action);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            addIdentityHeaders(connection);
            if(!"login".equals(action)&&sessionToken.isEmpty()){
                JSONObject login=post("login",new JSONObject());sessionToken=login.optString("token","");
                if(sessionToken.isEmpty())throw new AiException("AI_DEVICE_NOT_ACTIVE",false);
            }
            JSONObject envelope = new JSONObject(request.toString());
            envelope.put("device_id", identity.getDeviceId());
            envelope.put("device_key", identity.getDeviceKey());
            envelope.put("serial", identity.getSerial());
            envelope.put("lazytv_id", identity.getLazyTvId());
            envelope.put("app_version", BuildConfig.VERSION_NAME);
            if(!"login".equals(action))envelope.put("token",sessionToken);
            byte[] body = envelope.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            return response(connection);
        } catch (AiException e) { throw e; }
        catch (IOException e) { throw new AiException("LazyTV AI service unavailable", true); }
        catch (Exception e) { throw new AiException("Invalid LazyTV AI response", false); }
        finally { if (connection != null) connection.disconnect(); }
    }

    private void addIdentityHeaders(HttpsURLConnection connection) {
        connection.setRequestProperty("X-LazyTV-Device-Id", identity.getDeviceId());
        connection.setRequestProperty("X-LazyTV-Device-Key", identity.getDeviceKey());
        connection.setRequestProperty("X-LazyTV-Serial", identity.getSerial());
        connection.setRequestProperty("X-LazyTV-Id", identity.getLazyTvId());
        connection.setRequestProperty("X-LazyTV-App-Version", BuildConfig.VERSION_NAME);
    }

    private HttpsURLConnection open(String action) throws Exception {
        if (action == null || !action.matches("[a-z]+")) throw new AiException("Invalid AI action", false);
        URL url = new URL(BASE_URL + "?action=" + action);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new AiException("LazyTV AI requires HTTPS", false);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        return connection;
    }

    private JSONObject response(HttpURLConnection connection) throws Exception {
        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = read(stream);
        JSONObject json;
        try { json = new JSONObject(text); }
        catch (Exception invalid) { throw new AiException("Invalid LazyTV AI response", false); }
        if (code < 200 || code >= 300 || !json.optBoolean("ok", false)) {
            String error = json.optString("error", "AI_SERVICE_ERROR").toUpperCase(Locale.US);if(!error.matches("[A-Z_]{1,64}"))error="AI_SERVICE_ERROR";if("SESSION_EXPIRED".equals(error))sessionToken="";
            boolean retryable = code == 408 || code == 429 || code >= 500 || "BUSY".equals(error) || error.contains("LIMIT") || error.contains("UPSTREAM");
            throw new AiException("LazyTV AI: " + error, retryable);
        }
        return json;
    }

    private String read(InputStream input) throws IOException {
        if (input == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() + line.length() > MAX_RESPONSE_CHARS) throw new IOException("AI response too large");
                out.append(line);
            }
            return out.toString();
        }
    }
}
