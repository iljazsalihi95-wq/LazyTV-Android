package de.lazytv.pro.stb;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;
public class StbVodProgressTest {
 @Test public void resumeOnlyUsefulIncompletePosition(){assertEquals(0,StbVodProgress.resumable(1000,100000));assertEquals(45000,StbVodProgress.resumable(45000,100000));assertEquals(0,StbVodProgress.resumable(95000,100000));assertEquals(0,StbVodProgress.resumable(45000,0));}
 @Test public void seekIsBoundedAndUnknownDurationDoesNotSeek(){assertEquals(0,StbVodProgress.clamp(-30000,100000));assertEquals(99999,StbVodProgress.clamp(130000,100000));assertEquals(0,StbVodProgress.clamp(5000,0));assertEquals(30000,StbVodProgress.clamp(30000,100000));}
 @Test public void episodeAndProfileProgressStaySeparate()throws Exception {StbProtocol.Channel season=new StbProtocol.Channel(new JSONObject().put("id","51").put("name","Season").put("is_season",1).put("cmd","/media/7.mpg"));StbProtocol.Channel one=season.numberedEpisode("1"),two=season.numberedEpisode("2");assertNotEquals(StbVodProgress.key("profileA",one),StbVodProgress.key("profileA",two));assertNotEquals(StbVodProgress.key("profileA",one),StbVodProgress.key("profileB",one));}
 @Test public void displayUsesMilliseconds(){assertEquals("1:02:03",StbVodProgress.time(3723000));assertEquals("0:00:00",StbVodProgress.time(-1));}
}
