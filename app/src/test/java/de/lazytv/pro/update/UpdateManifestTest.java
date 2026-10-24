package de.lazytv.pro.update;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdateManifestTest {
 private String manifest(String url)throws Exception{return new JSONObject().put("versionCode",615).put("versionName","615").put("apkUrl",url).put("sha256",new String(new char[64]).replace('\0','a')).toString();}
 @Test public void publicReleaseComparesMonotonicVersion()throws Exception{UpdateManifest m=new UpdateManifest(manifest("https://github.com/iljazsalihi95-wq/LazyTV-Android/releases/download/v615/LazyTV-PRO.apk"));assertTrue(m.newerThan(614));assertFalse(m.newerThan(615));assertFalse(m.newerThan(616));}
 @Test public void refusesWrongRepoAndExpiringActionsUrls()throws Exception{for(String url:new String[]{"http://github.com/iljazsalihi95-wq/LazyTV-Android/releases/download/v615/a.apk","https://github.com/other/repo/releases/download/v615/a.apk","https://github.com/iljazsalihi95-wq/LazyTV-Android/actions/artifacts/1","https://github.com/iljazsalihi95-wq/LazyTV-Android/releases/download/v615/a.apk?token=expired"}){try{new UpdateManifest(manifest(url));fail(url);}catch(IllegalArgumentException expected){}}}
 @Test public void missingOrInvalidHashIsRejected()throws Exception{JSONObject json=new JSONObject(manifest("https://github.com/iljazsalihi95-wq/LazyTV-Android/releases/download/v615/a.apk"));json.put("sha256","invalid");try{new UpdateManifest(json.toString());fail();}catch(IllegalArgumentException expected){}}
}
