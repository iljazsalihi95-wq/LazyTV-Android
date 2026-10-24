package de.lazytv.pro.recording;
import org.junit.Test;import static org.junit.Assert.*;import java.util.*;
public class RecordingPolicyTest {
 @Test public void rejectsManifestsAndKnownProtection(){assertFalse(RecordingPolicy.candidate("https://fixture/video.mpd",null));assertFalse(RecordingPolicy.candidate("https://fixture/live.m3u8",null));assertFalse(RecordingPolicy.candidate("https://fixture/live.ts",Collections.singletonMap("drm","widevine")));assertTrue(RecordingPolicy.candidate("https://fixture/live.ts",null));}
 @Test public void rejectsScrambledTsPackets(){byte[] packet=new byte[188];packet[0]=0x47;assertTrue(RecordingPolicy.clearPacket(packet));packet[3]=(byte)0x80;assertFalse(RecordingPolicy.clearPacket(packet));packet[0]=0;packet[3]=0;assertFalse(RecordingPolicy.clearPacket(packet));}
 @Test public void safeFilenameContainsNoPathOrQuery(){String name=RecordingPolicy.filename("One/../../?token=secret",0);assertFalse(name.contains("/"));assertFalse(name.contains("?"));assertTrue(name.endsWith(".ts"));}
}
