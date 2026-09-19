package de.lazytv.pro.activation;
import android.content.Context;import android.provider.Settings;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.util.Locale;
public final class DeviceIdentityManager {
 private final String deviceId,lazyId,serial;
 public DeviceIdentityManager(Context c){String raw=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ANDROID_ID);if(raw==null||raw.trim().isEmpty())raw="android-id-unavailable";String hex=sha256("LazyTV|"+raw);deviceId="LTV-"+hex.substring(0,24).toUpperCase(Locale.US);String mac=hex.substring(24,36).toUpperCase(Locale.US);StringBuilder b=new StringBuilder();for(int i=0;i<12;i+=2){if(i>0)b.append(':');b.append(mac,i,i+2);}lazyId=b.toString();String s=hex.substring(36,48).toUpperCase(Locale.US);serial="LTV-"+s.substring(0,4)+"-"+s.substring(4,8)+"-"+s.substring(8,12);}
 public String getDeviceId(){return deviceId;} public String getLazyTvId(){return lazyId;} public String getSerial(){return serial;}
 private static String sha256(String s){try{byte[]d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.US,"%02x",x&255));return b.toString();}catch(Exception e){throw new IllegalStateException("Device identity unavailable",e);}}
}
