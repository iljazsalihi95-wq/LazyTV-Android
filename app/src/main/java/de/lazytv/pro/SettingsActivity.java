package de.lazytv.pro;
import android.app.*;import android.os.*;import android.widget.*;import android.content.*;import de.lazytv.pro.playlist.*;
public class SettingsActivity extends Activity{
 protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_settings);
  refreshActivePlaylist();
  findViewById(R.id.settings_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.settings_activation).setOnClickListener(v->startActivity(new Intent(this,de.lazytv.pro.activation.ActivationActivity.class)));
  findViewById(R.id.settings_player).setOnClickListener(v->showPlayerSettings());
  findViewById(R.id.settings_back).setOnClickListener(v->finish());
 }
 private void showPlayerSettings(){
  final String[] modes={"FIT — entire picture","ZOOM — fill screen","FILL — stretch"};
  final android.content.SharedPreferences p=getSharedPreferences("lazytv_player_settings",MODE_PRIVATE);
  int selected=p.getInt("aspect_mode",0);
  new AlertDialog.Builder(this).setTitle("Player Settings • Screen format").setSingleChoiceItems(modes,selected,(d,which)->{p.edit().putInt("aspect_mode",which).apply();d.dismiss();Toast.makeText(this,"Player format saved",Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancel",null).show();
 }
 @Override protected void onResume(){super.onResume();refreshActivePlaylist();}
 private void refreshActivePlaylist(){
  PlaylistStorage s=new PlaylistStorage(this);Playlist p=s.get(s.getActiveId());
  TextView active=findViewById(R.id.settings_active);
  if(active!=null)active.setText(p==null?"Asnjë playlist aktive":"Playlist aktive: "+p.getName());
 }
}