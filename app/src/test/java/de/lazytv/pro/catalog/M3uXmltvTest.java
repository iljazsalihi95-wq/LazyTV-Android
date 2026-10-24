package de.lazytv.pro.catalog;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.Test;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,application=android.app.Application.class)
public class M3uXmltvTest {
 @Test public void headerGuideSurvivesParsingAndNowNextUsesRealDates()throws Exception{
  Catalog catalog=M3uParser.parse(new BufferedReader(new StringReader("#EXTM3U x-tvg-url=\"https://fixture.invalid/epg.xml\"\n#EXTINF:-1 tvg-id=\"seven\",Seven\nhttps://fixture.invalid/live.ts\n")));
  assertEquals("https://fixture.invalid/epg.xml",catalog.items.get(0).meta("xmltv_url"));
  String xml="<tv><programme channel='seven' start='20261024090000 +0000' stop='20261024100000 +0000'><title>Current</title></programme><programme channel='seven' start='20261024100000 +0000' stop='20261024110000 +0000'><title>Next</title></programme></tv>";
  long now=java.time.Instant.parse("2026-10-24T09:30:00Z").toEpochMilli();Map<String,EpgSchedule> result=M3uXmltv.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),now);
  assertEquals("Current",result.get("seven").active(now).title);assertEquals("Next",result.get("seven").upcoming(now).title);assertEquals(50,result.get("seven").active(now).progress(now));
 }
 @Test public void externalEntityDocumentIsRejected()throws Exception{String xml="<!DOCTYPE tv [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><tv><title>&secret;</title></tv>";try{M3uXmltv.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),0);fail();}catch(Exception expected){}}
}
