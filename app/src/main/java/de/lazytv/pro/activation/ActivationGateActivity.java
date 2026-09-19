package de.lazytv.pro.activation;
import android.app.*;import android.content.*;import android.os.*;import de.lazytv.pro.*;import java.util.concurrent.*;
public class ActivationGateActivity extends Activity {
 public static final long OFFLINE_GRACE_MS=24L*60L*60L*1000L;private final ExecutorService pool=Executors.newSingleThreadExecutor();private volatile boolean destroyed;
 @Override protected void onCreate(Bundle b){super.onCreate(b);if(BuildConfig.DEBUG&&BuildConfig.DEV_ACTIVATION_BYPASS){main();return;}check();}
 private void check(){String base=BuildConfig.ACTIVATION_BASE_URL;if(base==null||base.trim().isEmpty()){activation("Activation service not configured");return;}DeviceIdentityManager id=new DeviceIdentityManager(this);pool.execute(()->{try{ActivationResult r=new ActivationApiClient().check(base,id.getDeviceId(),id.getSerial(),id.getLazyTvId(),BuildConfig.VERSION_NAME);new ActivationCache(this).save(r);runOnUiThread(()->{if(destroyed)return;if(r.status==ActivationStatus.ACTIVE)main();else activation(null);});}catch(ActivationApiClient.ActivationException e){boolean grace=e.network&&new ActivationCache(this).validOfflineGrace(OFFLINE_GRACE_MS);runOnUiThread(()->{if(destroyed)return;if(grace)main();else activation(e.getMessage());});}});}
 private void main(){startActivity(new Intent(this,MainActivity.class));finish();}private void activation(String error){Intent i=new Intent(this,ActivationActivity.class);if(error!=null)i.putExtra(ActivationActivity.EXTRA_ERROR,error);startActivity(i);finish();}
 @Override protected void onDestroy(){destroyed=true;pool.shutdownNow();super.onDestroy();}
}
