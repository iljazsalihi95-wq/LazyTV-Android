package de.lazytv.pro;
import android.app.Activity;
import android.widget.*;
import android.os.Looper;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk={25,28},application=android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class HomeFocusTest {
 @Test public void postedHomeTileFocusSurvivesDetachment()throws Exception{
  org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class);MainActivity a=controller.get();
  Method factory=MainActivity.class.getDeclaredMethod("tile",String.class);factory.setAccessible(true);
  Button tile=(Button)factory.invoke(a,"PREMIUM");LinearLayout root=new LinearLayout(a);root.addView(tile);a.setContentView(root);
  controller.visible();tile.setFocusableInTouchMode(true);tile.requestFocus();assertTrue(tile.isAttachedToWindow());tile.getOnFocusChangeListener().onFocusChange(tile,true);root.removeView(tile);
  Shadows.shadowOf(Looper.getMainLooper()).idle();assertNull(tile.getParent());assertFalse(a.isFinishing());
 }
 @Test public void focusCallbackOnAlreadyDetachedTileIsHarmless(){Activity a=Robolectric.buildActivity(Activity.class).setup().get();HomeFocus.request(new Button(a));assertFalse(a.isFinishing());}
}
