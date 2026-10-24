package de.lazytv.pro.live;
import android.app.Activity;import android.os.Looper;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.catalog.*;
import java.util.*;import java.util.concurrent.atomic.AtomicInteger;import java.lang.reflect.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,application=android.app.Application.class) @LooperMode(LooperMode.Mode.PAUSED)
public class LiveRecoveryTest {
 private LivePlaybackSession session;private Activity activity;private final AtomicInteger fatal=new AtomicInteger();
 private final StreamItem item=new StreamItem("7","Fixture","1","","http://127.0.0.1:1/old.ts","",CatalogType.LIVE);
 private ResolvedStream stream(String suffix){return new ResolvedStream("http://127.0.0.1:1/"+suffix+".ts","ts","Fixture","","",Collections.singletonMap("Cookie","fixture="+suffix),null);}
 private void set(String name,Object value)throws Exception{Field f=LivePlaybackSession.class.getDeclaredField(name);f.setAccessible(true);f.set(session,value);}
 private Object get(String name)throws Exception{Field f=LivePlaybackSession.class.getDeclaredField(name);f.setAccessible(true);return f.get(session);}
 private void setup(LivePlaybackSession.RetryResolver resolver)throws Exception{activity=Robolectric.buildActivity(Activity.class).setup().get();
  session=new LivePlaybackSession(activity,new PlayerView(activity),resolver,new LivePlaybackSession.Listener(){public void onBuffering(boolean b){}public void onReady(){}public void onFatalPlaybackError(String message){fatal.incrementAndGet();}});
  set("generation",1L);set("currentItem",item);set("currentStream",stream("old"));set("lastHttpStatus",458);
 }
 private void retry()throws Exception{Method m=LivePlaybackSession.class.getDeclaredMethod("retryCurrent",long.class,StreamItem.class);m.setAccessible(true);m.invoke(session,1L,item);}
 private void waitUntil(java.util.function.BooleanSupplier done)throws Exception{long deadline=System.nanoTime()+5_000_000_000L;while(!done.getAsBoolean()&&System.nanoTime()<deadline){Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();}assertTrue(done.getAsBoolean());}
 @After public void close(){if(session!=null)session.release();if(activity!=null)activity.finish();Shadows.shadowOf(Looper.getMainLooper()).idle();}
 @Test public void freshLinkPreservesHeadersAnd458BudgetSurvivesCounterReset()throws Exception{AtomicInteger refresh=new AtomicInteger();setup(i->{refresh.incrementAndGet();return stream("fresh");});retry();
  waitUntil(()->{try{return ((ResolvedStream)get("currentStream")).url.endsWith("fresh.ts");}catch(Exception e){throw new AssertionError(e);}});
  assertEquals("fixture=fresh",((ResolvedStream)get("currentStream")).headers.get("Cookie"));
  set("retryCount",0);set("lastHttpStatus",458);retry();assertEquals(1,refresh.get());assertEquals(1,fatal.get());
 }
 @Test public void staleUrlIsRejectedAndNotRetried()throws Exception{AtomicInteger refresh=new AtomicInteger();setup(i->{refresh.incrementAndGet();return stream("old");});retry();waitUntil(()->fatal.get()==1);assertEquals(1,refresh.get());assertTrue(((ResolvedStream)get("currentStream")).url.endsWith("old.ts"));retry();assertEquals(1,refresh.get());}
 @Test public void failedCreateLinkStopsAfterOneAttempt()throws Exception{AtomicInteger refresh=new AtomicInteger();setup(i->{refresh.incrementAndGet();throw new CatalogException("Fixture resolver unavailable");});retry();waitUntil(()->fatal.get()==1);assertEquals(1,refresh.get());}
 @Test public void destroyedSessionDoesNotApplyLateResolverResult()throws Exception{java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);setup(i->{entered.countDown();release.await();return stream("fresh");});retry();assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS));session.release();release.countDown();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(((ResolvedStream)get("currentStream")).url.endsWith("old.ts"));}
}
