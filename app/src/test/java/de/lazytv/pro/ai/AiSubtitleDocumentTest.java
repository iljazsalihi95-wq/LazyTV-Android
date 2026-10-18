package de.lazytv.pro.ai;
import org.junit.Test;
import static org.junit.Assert.*;

public class AiSubtitleDocumentTest {
 @Test public void keepsExplicitMillisecondsWithoutHeuristicOrClamping()throws Exception{
  AiSubtitleDocument d=AiSubtitleDocument.parse("{\"language\":\"sq\",\"timeUnit\":\"ms\",\"cues\":[{\"start\":1500,\"end\":125000,\"text\":\"Test\"}]}");
  assertEquals(1500,d.cues.get(0).startMs);assertEquals(125000,d.cues.get(0).endMs);
  assertNull(d.cueAt(1499));assertNotNull(d.cueAt(1500));assertNull(d.cueAt(125000));
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsOutOfOrderInsteadOfSorting()throws Exception{
  AiSubtitleDocument.parse("{\"cues\":[{\"start_ms\":2000,\"end_ms\":3000,\"text\":\"B\"},{\"start_ms\":1000,\"end_ms\":1500,\"text\":\"A\"}]}");
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsBadRowInsteadOfDroppingCue()throws Exception{
  AiSubtitleDocument.parse("{\"cues\":[{\"start_ms\":0,\"end_ms\":1000,\"text\":\"A\"},null]}");
 }
 @Test(expected=IllegalArgumentException.class) public void rejectsFractionalMilliseconds()throws Exception{
  AiSubtitleDocument.parse("{\"cues\":[{\"start_ms\":0.5,\"end_ms\":1000,\"text\":\"A\"}]}");
 }
}
