package de.lazytv.pro.player;
import de.lazytv.pro.catalog.*;import java.util.Collections;import org.junit.Test;import static org.junit.Assert.*;
public class PlayerContentPolicyTest {
 @Test public void parentSeriesNeverPlaysEvenIfProviderReturnsUrl(){assertFalse(PlayerContentPolicy.playable(new Series("1","Series","cat","","https://provider/parent","")));assertTrue(PlayerContentPolicy.playable(new Episode("2","Episode","1","cat","","https://provider/episode","mp4","",1,1,Collections.emptyMap())));}
}
