package de.lazytv.pro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.GridLayout;
import de.lazytv.pro.live.LiveTvActivity;
import de.lazytv.pro.playlist.PlaylistManagerActivity;
import de.lazytv.pro.playlist.PlaylistStorage;
import de.lazytv.pro.playlist.Playlist;

public class MainActivity extends Activity {
 @Override protected void onCreate(Bundle state){
  super.onCreate(state);
  boolean portrait=getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT;
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(8),dp(8),dp(8),dp(8));root.setBackgroundColor(Color.rgb(0,14,27));
  TextView title=new TextView(this);title.setText("LazyTV PRO");title.setTextColor(Color.WHITE);title.setTextSize(28);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);root.addView(title,new LinearLayout.LayoutParams(-1,dp(55)));
  TextView sub=new TextView(this);sub.setText("LIVE TV  •  PREMIUM  •  MOVIES  •  SERIES");sub.setTextColor(Color.rgb(90,190,255));sub.setTextSize(12);sub.setGravity(Gravity.CENTER_VERTICAL);root.addView(sub,new LinearLayout.LayoutParams(-1,dp(35)));
  Button live=tile("LIVE TV"),premium=tile("PREMIUM"),movies=tile("MOVIES"),series=tile("SERIES"),playlists=tile("PLAYLISTS"),catchup=tile("CATCH-UP"),favorites=tile("FAVORITES"),settings=tile("SETTINGS"),reload=tile("RELOAD"),servers=tile("SERVERS"),exit=tile("EXIT");
  if(portrait){GridLayout g=new GridLayout(this);g.setColumnCount(2);root.addView(g,new LinearLayout.LayoutParams(-1,0,1));Button[] a={live,premium,movies,series,playlists,catchup,favorites,settings,reload,servers,exit};for(Button b:a){GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(125);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(4),dp(4),dp(4),dp(4));g.addView(b,p);}}
  else{LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);root.addView(r,new LinearLayout.LayoutParams(-1,0,1));Button[] a={live,premium,movies,series,playlists,catchup};for(Button b:a)r.addView(b,tileParams());LinearLayout r2=new LinearLayout(this);r2.setOrientation(LinearLayout.HORIZONTAL);root.addView(r2,new LinearLayout.LayoutParams(-1,0,1));Button[] b={favorites,settings,reload,servers,exit};for(Button x:b)r2.addView(x,tileParams());}
  TextView status=new TextView(this);Playlist ap=active();status.setText("ACTIVE PORTAL  •  "+(ap==null?"FREE TV":ap.getName()));status.setTextColor(Color.rgb(120,180,220));status.setGravity(Gravity.CENTER_VERTICAL);root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));
  live.setOnClickListener(v->openLive(false));
  premium.setOnClickListener(v->openPremium());
  playlists.setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  movies.setOnClickListener(v->openCatalog("MOVIES"));series.setOnClickListener(v->openCatalog("SERIES"));favorites.setOnClickListener(v->openCatalog(null));
  settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));servers.setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));reload.setOnClickListener(v->openLive(false));exit.setOnClickListener(v->finish());catchup.setOnClickListener(v->android.widget.Toast.makeText(this,"Catch-up varet nga provider-i",android.widget.Toast.LENGTH_SHORT).show());
  setContentView(root);live.requestFocus();
 }
 private void openLive(boolean premium){Playlist p=active();Intent i=new Intent(this,LiveTvActivity.class);if(p!=null){i.putExtra("playlist_id",p.getId());i.putExtra("source_scope",premium?"PREMIUM":"PROVIDER");}else{i.putExtra("builtin_live",true);i.putExtra("source_scope","TRIAL");}startActivity(i);}
 private void openPremium(){PlaylistStorage s=new PlaylistStorage(this);java.util.List<Playlist> all=s.getAll();java.util.ArrayList<Playlist> paid=new java.util.ArrayList<>();for(Playlist p:all){if(!"FREE TV".equalsIgnoreCase(p.getName()))paid.add(p);}if(paid.isEmpty()){new android.app.AlertDialog.Builder(this).setTitle("PREMIUM").setMessage("Server 10 / Premium nuk është ngarkuar ende në këtë pajisje. Importo M3U, Xtream ose Stalker dhe pastaj hape nga PREMIUM.").setPositiveButton("SHTO PREMIUM",(d,w)->startActivity(new Intent(this,PlaylistManagerActivity.class))).setNegativeButton("MBYLL",null).show();return;}String[] names=new String[paid.size()];for(int i=0;i<paid.size();i++)names[i]=paid.get(i).getName()+"  •  "+paid.get(i).getType().name();new android.app.AlertDialog.Builder(this).setTitle("PREMIUM • Zgjidh serverin").setItems(names,(d,w)->{Playlist p=paid.get(w);s.setActive(p.getId());Intent x=new Intent(this,LiveTvActivity.class);x.putExtra("playlist_id",p.getId());x.putExtra("source_scope","PREMIUM");startActivity(x);}).setNegativeButton("MBYLL",null).show();}
 private Playlist active(){PlaylistStorage s=new PlaylistStorage(this);String id=s.getActiveId();return id==null?null:s.get(id);}
 private void openCatalog(String type){Playlist p=active();if(p==null){startActivity(new Intent(this,PlaylistManagerActivity.class));return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");if(type!=null)i.putExtra(de.lazytv.pro.catalog.CatalogActivity.EXTRA_TYPE,type);startActivity(i);}
 private Button tile(String t){Button b=new Button(this);b.setText(t);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setFocusable(true);GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(6,43,72),Color.rgb(1,21,40)});g.setCornerRadius(dp(18));g.setStroke(dp(1),"PREMIUM".equals(t)?Color.rgb(255,180,40):Color.rgb(35,90,130));b.setBackground(g);return b;}
 private LinearLayout.LayoutParams tileParams(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}
 private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
}
