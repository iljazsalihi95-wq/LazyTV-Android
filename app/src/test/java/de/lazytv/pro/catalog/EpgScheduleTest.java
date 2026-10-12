package de.lazytv.pro.catalog;
import org.junit.Test;import static org.junit.Assert.*;
public class EpgScheduleTest {
 @Test public void actualCurrentAndNextAreSelectedFromUnorderedProviderRows()throws Exception {
  EpgSchedule schedule=EpgSchedule.parse("{\"epg_listings\":[{\"title\":\"Later\",\"start_timestamp\":3,\"stop_timestamp\":5},{\"title\":\"TmV3cw==\",\"start_timestamp\":1,\"stop_timestamp\":3},{\"title\":\"Past\",\"start_timestamp\":1,\"stop_timestamp\":2}]}","9",2500);
  assertEquals("News",schedule.current.title);assertEquals("Later",schedule.next.title);assertEquals(75,schedule.current.progress(2500));assertEquals("Later",schedule.active(3500).title);
 }
 @Test public void channelKeyedStalkerScheduleIsRead()throws Exception {
  EpgSchedule schedule=EpgSchedule.parse("{\"js\":{\"9\":[{\"name\":\"Fixture\",\"start_timestamp\":1,\"stop_timestamp\":3}]}}","9",2000);
  assertEquals("Fixture",schedule.current.title);assertEquals(50,schedule.current.progress(2000));
 }
 @Test public void absentTimesDoNotManufactureProgress()throws Exception {
  EpgSchedule schedule=EpgSchedule.parse("{\"js\":{\"data\":[{\"title\":\"Provider title\"}]}}","9",2000);
  assertFalse(schedule.current.timed());assertEquals(0,schedule.current.progress(2000));assertEquals("",schedule.current.time());
 }
 @Test public void missingEpgAndExpiredProgramsAreNotPresentedAsCurrent()throws Exception {
  assertNull(EpgSchedule.parse("{\"epg_listings\":[]}","9",2000).current);
  EpgSchedule schedule=EpgSchedule.parse("{\"epg_listings\":[{\"title\":\"Past\",\"start_timestamp\":1,\"stop_timestamp\":2}]}","9",3000);assertNull(schedule.current);
 }
}
