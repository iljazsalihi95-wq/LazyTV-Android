package de.lazytv.pro;
import android.app.*;import android.os.*;import android.widget.*;import android.content.*;import de.lazytv.pro.playlist.*;
public class SettingsActivity extends Activity{
 protected void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_settings);
  refreshActivePlaylist();
  findViewById(R.id.settings_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.settings_activation).setOnClickListener(v->startActivity(new Intent(this,de.lazytv.pro.activation.ActivationActivity.class)));
  findViewById(R.id.settings_player).setOnClickListener(v->showPlayerSettings());
  findViewById(R.id.settings_language).setOnClickListener(v->showLanguageSettings());
  findViewById(R.id.settings_parental).setOnClickListener(v->showParentalSettings());
  findViewById(R.id.settings_stream).setOnClickListener(v->showStreamFormatSettings());
  findViewById(R.id.settings_back).setOnClickListener(v->finish());
 }
 private void showStreamFormatSettings(){
  final String[] formats={"AUTO — provider/default","MPEG-TS (.ts)","HLS (.m3u8)"};final String[] values={"auto","ts","m3u8"};final android.content.SharedPreferences p=getSharedPreferences("lazytv_player_settings",MODE_PRIVATE);String current=p.getString("live_stream_format","auto");int selected=0;for(int i=0;i<values.length;i++)if(values[i].equals(current))selected=i;new AlertDialog.Builder(this).setTitle("Live Stream Format").setSingleChoiceItems(formats,selected,(d,which)->{p.edit().putString("live_stream_format",values[which]).apply();d.dismiss();Toast.makeText(this,"Live format saved: "+formats[which],Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancel",null).show();
 }
 private void showParentalSettings(){
  final android.content.SharedPreferences p=getSharedPreferences("lazytv_parental",MODE_PRIVATE);final boolean enabled=p.getBoolean("enabled",false);final EditText pin=new EditText(this);pin.setHint(enabled?"New 4-digit PIN (leave blank to disable)":"Create 4-digit PIN");pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);new AlertDialog.Builder(this).setTitle("Parental Control").setMessage(enabled?"Parental Control is ON. Enter a new PIN to change it, or leave blank to turn it off.":"Set a 4-digit PIN to enable Parental Control.").setView(pin).setPositiveButton("Save",(d,w)->{String value=pin.getText().toString().trim();if(value.isEmpty()&&enabled){p.edit().clear().apply();Toast.makeText(this,"Parental Control disabled",Toast.LENGTH_SHORT).show();return;}if(!value.matches("\\d{4}")){Toast.makeText(this,"PIN must contain exactly 4 digits",Toast.LENGTH_LONG).show();return;}String hash=sha256(value);p.edit().putBoolean("enabled",true).putString("pin_hash",hash).apply();Toast.makeText(this,"Parental Control enabled",Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancel",null).show();
 }
 private String sha256(String value){try{java.security.MessageDigest md=java.security.MessageDigest.getInstance("SHA-256");byte[] b=md.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte x:b)out.append(String.format(java.util.Locale.US,"%02x",x));return out.toString();}catch(Exception e){return "";}}
 private void showLanguageSettings(){
  final String[] languages={"Shqip","Deutsch","English"};final android.content.SharedPreferences p=getSharedPreferences("lazytv_ui_settings",MODE_PRIVATE);int selected=p.getInt("language",0);new AlertDialog.Builder(this).setTitle("Language / Gjuha").setSingleChoiceItems(languages,selected,(d,which)->{p.edit().putInt("language",which).putString("language_name",languages[which]).apply();d.dismiss();Toast.makeText(this,"Language saved: "+languages[which],Toast.LENGTH_SHORT).show();}).setNegativeButton("Cancel",null).show();
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