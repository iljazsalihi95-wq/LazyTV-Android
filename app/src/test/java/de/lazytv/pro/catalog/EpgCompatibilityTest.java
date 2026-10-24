package de.lazytv.pro.catalog;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.RobolectricTestRunner;import org.robolectric.annotation.Config;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25,application=android.app.Application.class)
public class EpgCompatibilityTest {
 @Test public void bothExistingEpgReadersRetainUnicodeAndLineBreaks()throws Exception{
  String text="Lajmet • Shqip\nProgrami i radhës";String encoded=android.util.Base64.encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.NO_WRAP);
  for(Object reader:new Object[]{new StreamResolver(new StalkerCatalogSource()),new XtreamCatalogSource()}){java.lang.reflect.Method decode=reader.getClass().getDeclaredMethod("decodeEpg",String.class);decode.setAccessible(true);assertEquals(text,decode.invoke(reader,encoded));assertEquals("",decode.invoke(reader,(String)null));}
 }
}
