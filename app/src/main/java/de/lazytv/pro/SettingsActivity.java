package de.lazytv.pro;
import android.app.*;import android.os.*;import android.widget.*;import android.content.*;import de.lazytv.pro.playlist.*;
public class SettingsActivity extends Activity{
 protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_settings);
  refreshActivePlaylist();
  findViewById(R.id.settings_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.settings_activation).setOnClickListener(v->startActivity(new Intent(this,de.lazytv.pro.activation.ActivationActivity.class)));
  findViewById(R.id.settings_back).setOnClickListener(v->finish());
 }
 @Override protected void onResume(){super.onResume();refreshActivePlaylist();}
 private void refreshActivePlaylist(){
  PlaylistStorage s=new PlaylistStorage(this);Playlist p=s.get(s.getActiveId());
  TextView active=findViewById(R.id.settings_active);
  if(active!=null)active.setText(p==null?"Asnjë playlist aktive":"Playlist aktive: "+p.getName());
 }
}