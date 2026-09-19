package de.lazytv.pro;
import de.lazytv.pro.activation.ActivationGuard;import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import de.lazytv.pro.playlist.PlaylistManagerActivity;
public class MainActivity extends Activity {
 @Override protected void onCreate(Bundle b){super.onCreate(b);if(!ActivationGuard.enforce(this))return;setContentView(R.layout.activity_main);
  Base64AssetImage.load(this,(ImageView)findViewById(R.id.home_logo),"lazytv_home.webp.b64");
  Base64AssetImage.load(this,(ImageView)findViewById(R.id.playlist_art),"lazytv_playlist.webp.b64");
  findViewById(R.id.manage_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.manage_playlists).requestFocus();
 }
}