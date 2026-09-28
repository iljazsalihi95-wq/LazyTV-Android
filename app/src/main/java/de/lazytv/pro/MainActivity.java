package de.lazytv.pro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.graphics.Color;
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
  root.setPadding(dp(36),dp(28),dp(36),dp(28));
  root.setBackgroundColor(Color.rgb(2,8,18));

  TextView title=new TextView(this);
  title.setText("LazyTV PRO");
  title.setTextColor(Color.WHITE);
  title.setTextSize(30);
  title.setGravity(Gravity.CENTER);
  root.addView(title,new LinearLayout.LayoutParams(-1,dp(64)));

  LinearLayout row=new LinearLayout(this);
  row.setOrientation(LinearLayout.HORIZONTAL);
  row.setGravity(Gravity.CENTER);
  root.addView(row,new LinearLayout.LayoutParams(-1,dp(110)));

  Button live=tile("LIVE TV");
  Button movies=tile("MOVIES");
  Button series=tile("SERIES");
  Button playlists=tile("PLAYLISTS");
  row.addView(live,tileParams());
  row.addView(movies,tileParams());
  row.addView(series,tileParams());
  row.addView(playlists,tileParams());

  LinearLayout row2=new LinearLayout(this);
  row2.setOrientation(LinearLayout.HORIZONTAL);
  row2.setGravity(Gravity.CENTER);
  root.addView(row2,new LinearLayout.LayoutParams(-1,dp(92)));
  Button favorites=tile("★ FAVORITES");
  Button settings=tile("⚙ SETTINGS");
  Button reload=tile("↻ RELOAD");
  Button exit=tile("EXIT");
  Button servers=tile("SERVERS");
  row2.addView(favorites,tileParams());
  row2.addView(settings,tileParams());
  row2.addView(reload,tileParams());
  row2.addView(exit,tileParams());

  LinearLayout row3=new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL); row3.setGravity(Gravity.CENTER); root.addView(row3,new LinearLayout.LayoutParams(-1,dp(92)));
  row3.addView(servers,tileParams());

  live.setOnClickListener(v->{
   Intent i=new Intent(this,LiveTvActivity.class);
   i.putExtra("builtin_live",true);
   i.putExtra("source_scope","TRIAL");
   startActivity(i);
  });
  playlists.setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
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
 private void showServers(){final String[] names={"LazyIPTV Master","Krystal","Saray","Server 4","Server 5","Server 6","Server 7","Server 8","Server 9","Server 10"};new android.app.AlertDialog.Builder(this).setTitle("LazyTV Servers").setItems(names,(d,w)->android.widget.Toast.makeText(this,names[w]+" selected",android.widget.Toast.LENGTH_SHORT).show()).setNegativeButton("Close",null).show();}
 private void openPlaylists(){startActivity(new Intent(this,PlaylistManagerActivity.class));}
 private Playlist active(){PlaylistStorage store=new PlaylistStorage(this);String id=store.getActiveId();return id==null?null:store.get(id);}
 private void openCatalog(String type){Playlist p=active();if(p==null){openPlaylists();return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");if(type!=null)i.putExtra(de.lazytv.pro.catalog.CatalogActivity.EXTRA_TYPE,type);startActivity(i);}
 private Button tile(String text){
  Button b=new Button(this);
  b.setText(text);
  b.setTextSize(18);
  b.setTextColor(Color.WHITE);
  b.setAllCaps(false);
  b.setFocusable(true);
  b.setFocusableInTouchMode(true);
  b.setBackgroundResource(R.drawable.bg_card);
  b.setOnFocusChangeListener((v,focused)->{
   v.setScaleX(focused?1.07f:1f);
   v.setScaleY(focused?1.07f:1f);
  });
  return b;
 }
 private LinearLayout.LayoutParams tileParams(){
  LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);
  p.setMargins(dp(7),dp(7),dp(7),dp(7));
  return p;
 }
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
