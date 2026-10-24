package de.lazytv.pro.player;

import java.util.Map;

/** Decoder fallback must never mask an HTTP/network refusal or discard provider headers. */
public final class PlayerFallbackPolicy {
 private PlayerFallbackPolicy(){}
 static boolean decoderFailure(int mediaError,int httpStatus){return httpStatus==0&&((mediaError>=3000&&mediaError<4000)||(mediaError>=4000&&mediaError<=4005));}
 static String header(Map<String,String> headers,String name){for(Map.Entry<String,String> h:headers.entrySet())if(name.equalsIgnoreCase(h.getKey()))return h.getValue();return null;}
 public static boolean vlcSupportsHeaders(Map<String,String> headers){
  for(Map.Entry<String,String> h:headers.entrySet()){
   if(h.getValue()==null||h.getValue().isEmpty())continue;
   String name=h.getKey();
   if("User-Agent".equalsIgnoreCase(name)||"Referer".equalsIgnoreCase(name))continue;
   if("Accept".equalsIgnoreCase(name)&&"*/*".equals(h.getValue()))continue;
   // The current LibVLC bridge only implements UA/referrer options. IJK receives the full map.
   return false;
  }
  return true;
 }
}
