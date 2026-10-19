package de.lazytv.pro.ai;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ExternalSubtitleTracksTest {
 @Test public void preservesLanguagesAndDeduplicatesNormalizedAlias(){
  Map<String,String> metadata=new HashMap<>();
  metadata.put("subtitles","[{\"url\":\"https://provider.test/en.srt\",\"language\":\"English\"},{\"file\":\"https://provider.test/de.vtt\",\"lang\":\"Deutsch\"}]");
  metadata.put("subtitle_url","https://provider.test/en.srt");
  List<ExternalSubtitleTracks.Track> tracks=ExternalSubtitleTracks.read(metadata);
  assertEquals(2,tracks.size());assertEquals("English",tracks.get(0).label);
  assertEquals("Deutsch",tracks.get(1).label);assertEquals("https://provider.test/de.vtt",tracks.get(1).url);
 }
 @Test public void handlesDirectAndObjectMetadataWithoutExposingUrlsInLabels(){
  Map<String,String> metadata=new HashMap<>();
  metadata.put("srt","https://provider.test/user/secret/sub.srt");
  metadata.put("subtitle","{\"subtitle_url\":\"https://provider.test/de.vtt\",\"label\":\"Deutsch\"}");
  List<ExternalSubtitleTracks.Track> tracks=ExternalSubtitleTracks.read(metadata);
  assertEquals(2,tracks.size());assertEquals("Deutsch",tracks.get(0).label);
  assertFalse(tracks.get(1).label.contains("secret"));
 }
 @Test public void rejectsUnsupportedUrlsAndEmptyMetadata(){
  Map<String,String> metadata=new HashMap<>();
  metadata.put("subtitles","[\"file:///private.srt\",{\"url\":\"javascript:bad\"},null,{}]");
  assertTrue(ExternalSubtitleTracks.read(metadata).isEmpty());
  assertTrue(ExternalSubtitleTracks.read(null).isEmpty());
 }
}
