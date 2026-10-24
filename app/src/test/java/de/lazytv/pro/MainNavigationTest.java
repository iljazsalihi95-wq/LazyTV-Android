package de.lazytv.pro;
import android.app.*;import android.content.Intent;import android.view.*;import android.widget.*;
import de.lazytv.pro.live.*;import de.lazytv.pro.playlist.*;
import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.*;
import java.lang.reflect.*;import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={25,28},application=android.app.Application.class,qualifiers="land")
@LooperMode(LooperMode.Mode.PAUSED)
public class MainNavigationTest {
 private MainActivity activity;
 @Before public void home()throws Exception{activity=Robolectric.buildActivity(MainActivity.class).get();Field loading=MainActivity.class.getDeclaredField("serverRegistryLoading");loading.setAccessible(true);loading.set(activity,true);Method build=MainActivity.class.getDeclaredMethod("buildHome");build.setAccessible(true);build.invoke(activity);}
 @After public void close(){activity.finish();}
 private Button button(View v,String label){if(v instanceof Button&&label.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){Button result=button(((ViewGroup)v).getChildAt(n),label);if(result!=null)return result;}return null;}
 private void click(String label){Button b=button(activity.findViewById(android.R.id.content),label);assertNotNull(b);b.performClick();assertFalse(activity.isFinishing());}
 private Intent started(){return Shadows.shadowOf(activity).getNextStartedActivity();}
 @Test public void homePremiumPlaylistsAndStbLaunchTheirOwnActivities(){click("PREMIUM");assertEquals(PremiumLivePlayerActivity.class.getName(),started().getComponent().getClassName());click("PLAYLISTS");assertEquals(PlaylistManagerActivity.class.getName(),started().getComponent().getClassName());click("LAZY STB");assertEquals(de.lazytv.pro.stb.LazyStbActivity.class.getName(),started().getComponent().getClassName());}
 @Test public void liveAndServersRouteConfiguredProvidersImmediately(){PlaylistStorage store=new PlaylistStorage(activity);
  for(PlaylistType type:new PlaylistType[]{PlaylistType.XTREAM_CODES,PlaylistType.M3U_URL,PlaylistType.STALKER_PORTAL}){
   Playlist p=new Playlist("fixture-"+type,"Fixture "+type,type,"https://fixture.invalid","fixture","fixture","00:11:22:33:44:55",1);store.save(p);store.setActive(p.getId());
   click("LIVE TV");assertEquals(LiveProviderRouter.activity(p).getName(),started().getComponent().getClassName());
   click("SERVERS");AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();ListView list=d.getListView();int chosen=-1;
   for(int n=0;n<list.getAdapter().getCount();n++){String label=String.valueOf(list.getAdapter().getItem(n));assertFalse(label.contains("po përgatiten"));if(label.startsWith(p.getName()+" •"))chosen=n;}
   assertTrue(chosen>=0);list.performItemClick(null,chosen,chosen);assertEquals(LiveProviderRouter.activity(p).getName(),started().getComponent().getClassName());assertFalse(activity.isFinishing());d.dismiss();
  }
 }
}
