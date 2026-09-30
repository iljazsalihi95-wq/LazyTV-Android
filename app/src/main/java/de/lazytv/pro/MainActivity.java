package de.lazytv.pro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.lazytv.pro.live.LiveTvActivity;
import de.lazytv.pro.playlist.PlaylistManagerActivity;
import de.lazytv.pro.playlist.PlaylistStorage;
import de.lazytv.pro.playlist.Playlist;

/**
 * Lightweight Fire TV first-frame shell.
 * No bitmap decoding, network, provider parsing or native player initialization happens here.
 */
public class MainActivity extends Activity {
 @Override protected void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().setBackgroundDrawableResource(android.R.color.black);

  LinearLayout root=new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  root.setGravity(Gravity.CENTER);
  root.setPadding(dp(34),dp(20),dp(34),dp(22));
  GradientDrawable homeBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(2,8,18),Color.rgb(5,20,37),Color.rgb(2,8,18)});root.setBackground(homeBg);

  TextView title=new TextView(this);
  java.text.SimpleDateFormat clockFmt=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault());java.text.SimpleDateFormat dayFmt=new java.text.SimpleDateFormat("EEEE, dd.MM.yyyy",java.util.Locale.getDefault());
  title.setText("LazyTV PRO                                      "+clockFmt.format(new java.util.Date()));
  final android.os.Handler clockHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  final Runnable clockTick=new Runnable(){public void run(){if(!isFinishing()){title.setText("LazyTV PRO                                      "+clockFmt.format(new java.util.Date()));clockHandler.postDelayed(this,30000);}}};clockHandler.postDelayed(clockTick,30000);
  title.setTextColor(Color.WHITE);
  title.setTextSize(24);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  title.setGravity(Gravity.CENTER_VERTICAL);
  title.setPadding(dp(16),0,0,0);
  root.addView(title,new LinearLayout.LayoutParams(-1,dp(58)));

  TextView subtitle=new TextView(this);subtitle.setText(dayFmt.format(new java.util.Date())+"     •     ENTERTAINMENT HUB");subtitle.setTextColor(Color.rgb(111,196,255));subtitle.setTextSize(12);subtitle.setLetterSpacing(.18f);subtitle.setGravity(Gravity.CENTER_VERTICAL);subtitle.setPadding(dp(16),0,0,0);root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));

  LinearLayout row=new LinearLayout(this);
  row.setOrientation(LinearLayout.HORIZONTAL);
  row.setGravity(Gravity.CENTER);
  root.addView(row,new LinearLayout.LayoutParams(-1,dp(156)));

  Button live=tile("LIVE TV");
  Button movies=tile("MOVIES");
  Button series=tile("SERIES");
  Button playlists=tile("PLAYLISTS");
  Button catchup=tile("CATCH-UP");
  row.addView(live,tileParams());
  row.addView(movies,tileParams());
  row.addView(series,tileParams());
  row.addView(playlists,tileParams());
  row.addView(catchup,tileParams());

  LinearLayout row2=new LinearLayout(this);
  row2.setOrientation(LinearLayout.HORIZONTAL);
  row2.setGravity(Gravity.CENTER);
  root.addView(row2,new LinearLayout.LayoutParams(-1,dp(104)));
  Button favorites=smallTile("FAVORITES");
  Button settings=smallTile("SETTINGS");
  Button reload=smallTile("RELOAD");
  Button exit=smallTile("EXIT");
  Button servers=smallTile("SERVERS");
  row2.addView(favorites,tileParams());
  row2.addView(settings,tileParams());
  row2.addView(reload,tileParams());
  row2.addView(exit,tileParams());

  LinearLayout row3=new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL); row3.setGravity(Gravity.CENTER); root.addView(row3,new LinearLayout.LayoutParams(-1,dp(96)));
  TextView serverLabel=new TextView(this);Playlist ap=active();String activeName=ap!=null?ap.getName():getSharedPreferences("lazytv_servers",MODE_PRIVATE).getString("active_server_name","LazyIPTV Master");serverLabel.setText("  ACTIVE PORTAL  •  "+activeName+"   |   OK to switch source");serverLabel.setTextColor(Color.rgb(150,200,230));serverLabel.setTextSize(14);serverLabel.setGravity(Gravity.CENTER_VERTICAL);row3.addView(serverLabel,new LinearLayout.LayoutParams(0,-1,1f));
  LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(310),-1);sp.setMargins(dp(7),dp(7),dp(7),dp(7));row3.addView(servers,sp);

  live.setOnClickListener(v->{
   Playlist p=active();
   Intent i=new Intent(this,LiveTvActivity.class);
   if(p!=null){i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");}
   else{i.putExtra("builtin_live",true);i.putExtra("source_scope","TRIAL");}
   startActivity(i);
  });
  playlists.setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  catchup.setOnClickListener(v->{Playlist p=active();if(p==null){openPlaylists();return;}android.widget.Toast.makeText(this,"Catch-up aktivizohet vetëm kur provider-i ofron arkivë",android.widget.Toast.LENGTH_LONG).show();});
  movies.setOnClickListener(v->openCatalog("MOVIES"));
  series.setOnClickListener(v->openCatalog("SERIES"));
  favorites.setOnClickListener(v->openCatalog(null));
  settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
  reload.setOnClickListener(v->reloadPortal());
  servers.setOnClickListener(v->showServers());
  exit.setOnClickListener(v->finish());

  live.requestFocus();
  setContentView(root);
 }

 private void reloadPortal(){Playlist p=active();if(p==null){openPlaylists();return;}Intent i=new Intent(this,LiveTvActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");i.putExtra("force_reload",true);startActivity(i);}
 private void showServers(){final String[] names={"LazyIPTV Master","Krystal","Saray","Server 4","Server 5","Server 6","Server 7","Server 8","Server 9","Server 10"};final android.content.SharedPreferences prefs=getSharedPreferences("lazytv_servers",MODE_PRIVATE);int checked=prefs.getInt("active_server",0);new android.app.AlertDialog.Builder(this).setTitle("LazyTV Servers").setSingleChoiceItems(names,checked,(d,w)->{prefs.edit().putInt("active_server",w).putString("active_server_name",names[w]).apply();d.dismiss();Intent i=new Intent(this,LiveTvActivity.class);i.putExtra("builtin_live",true);i.putExtra("source_scope","SERVER");i.putExtra("server_index",w);i.putExtra("server_name",names[w]);startActivity(i);}).setNegativeButton("Close",null).show();}
 private void openPlaylists(){startActivity(new Intent(this,PlaylistManagerActivity.class));}
 private Playlist active(){PlaylistStorage store=new PlaylistStorage(this);String id=store.getActiveId();return id==null?null:store.get(id);}
 private void openCatalog(String type){Playlist p=active();if(p==null){openPlaylists();return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");if(type!=null)i.putExtra(de.lazytv.pro.catalog.CatalogActivity.EXTRA_TYPE,type);startActivity(i);}
 private Button tile(String text){
  Button b=new Button(this);
  b.setText(text);
  b.setTextSize(17);
  b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  b.setGravity(Gravity.CENTER);
  b.setPadding(dp(10),dp(12),dp(10),dp(12));
  b.setTextColor(Color.WHITE);
  b.setAllCaps(false);
  b.setFocusable(true);
  b.setFocusableInTouchMode(true);
  applyTileStyle(b,false);
  b.setOnFocusChangeListener((v,focused)->{
   v.animate().scaleX(focused?1.06f:1f).scaleY(focused?1.06f:1f).setDuration(120).start();
   applyTileStyle((Button)v,focused);
  });
  return b;
 }
 private Button smallTile(String text){Button b=tile(text);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.NORMAL);return b;}
 private void applyTileStyle(Button b,boolean focused){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,focused?new int[]{Color.rgb(0,120,215),Color.rgb(0,63,125)}:new int[]{Color.rgb(8,34,57),Color.rgb(5,21,38)});g.setCornerRadius(dp(18));g.setStroke(dp(focused?3:1),focused?Color.rgb(100,210,255):Color.rgb(35,79,113));b.setBackground(g);b.setElevation(dp(focused?14:3));}
 private LinearLayout.LayoutParams tileParams(){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);
  p.setMargins(dp(7),dp(7),dp(7),dp(7));
  return p;
 }
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
