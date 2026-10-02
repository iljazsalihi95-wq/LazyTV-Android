package de.lazytv.pro;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.Geocoder;
import android.Manifest;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
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
  root.setPadding(dp(14),dp(8),dp(14),dp(8));
  GradientDrawable homeBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(1,7,18),Color.rgb(3,28,55),Color.rgb(1,10,24)});root.setBackground(homeBg);

  TextView title=new TextView(this);
  java.text.SimpleDateFormat clockFmt=new java.text.SimpleDateFormat("HH:mm",java.util.Locale.getDefault());java.text.SimpleDateFormat dayFmt=new java.text.SimpleDateFormat("EEEE, dd.MM.yyyy",java.util.Locale.getDefault());
  title.setText("LazyTV PRO                                      "+clockFmt.format(new java.util.Date()));
  final android.os.Handler clockHandler=new android.os.Handler(android.os.Looper.getMainLooper());
  final Runnable clockTick=new Runnable(){public void run(){if(!isFinishing()){title.setText("LazyTV PRO                                      "+clockFmt.format(new java.util.Date()));clockHandler.postDelayed(this,30000);}}};clockHandler.postDelayed(clockTick,30000);
  title.setTextColor(Color.WHITE);
  title.setTextSize(24);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  title.setGravity(Gravity.CENTER_VERTICAL);
  title.setPadding(dp(16),0,0,0);
  root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));

  TextView subtitle=new TextView(this);subtitle.setText(dayFmt.format(new java.util.Date())+"     •     ENTERTAINMENT HUB");subtitle.setTextColor(Color.rgb(111,196,255));subtitle.setTextSize(12);subtitle.setLetterSpacing(.18f);subtitle.setGravity(Gravity.CENTER_VERTICAL);subtitle.setPadding(dp(16),0,0,0);root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(24)));

  TextView localInfo=new TextView(this);localInfo.setText("Location • Weather • Prayer times");localInfo.setTextColor(Color.rgb(226,241,250));localInfo.setTextSize(13);localInfo.setTypeface(Typeface.DEFAULT,Typeface.BOLD);localInfo.setGravity(Gravity.CENTER_VERTICAL);localInfo.setPadding(dp(18),0,dp(18),0);GradientDrawable prayerBar=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{Color.rgb(5,38,57),Color.rgb(7,72,91),Color.rgb(5,38,57)});prayerBar.setCornerRadius(dp(12));prayerBar.setStroke(dp(1),Color.rgb(40,153,181));localInfo.setBackground(prayerBar);LinearLayout.LayoutParams prayerParams=new LinearLayout.LayoutParams(-1,dp(34));prayerParams.setMargins(dp(14),dp(2),dp(14),dp(5));root.addView(localInfo,prayerParams);updateLocalInfo(localInfo);

  LinearLayout row=new LinearLayout(this);
  row.setOrientation(LinearLayout.HORIZONTAL);
  row.setGravity(Gravity.CENTER);
  root.addView(row,new LinearLayout.LayoutParams(-1,0,1.55f));

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
  root.addView(row2,new LinearLayout.LayoutParams(-1,0,1.05f));
  Button favorites=smallTile("FAVORITES");
  Button settings=smallTile("SETTINGS");
  Button reload=smallTile("RELOAD");
  Button exit=smallTile("EXIT");
  Button servers=smallTile("SERVERS");
  row2.addView(favorites,tileParams());
  row2.addView(settings,tileParams());
  row2.addView(reload,tileParams());
  row2.addView(exit,tileParams());
  row2.addView(servers,tileParams());

  LinearLayout row3=new LinearLayout(this); row3.setOrientation(LinearLayout.HORIZONTAL); row3.setGravity(Gravity.CENTER); root.addView(row3,new LinearLayout.LayoutParams(-1,dp(42)));
  TextView serverLabel=new TextView(this);Playlist ap=active();String activeName=ap!=null?ap.getName():getSharedPreferences("lazytv_servers",MODE_PRIVATE).getString("active_server_name","LazyIPTV Master");serverLabel.setText("  ACTIVE PORTAL  •  "+activeName+"   |   OK to switch source");serverLabel.setTextColor(Color.rgb(150,200,230));serverLabel.setTextSize(12);serverLabel.setGravity(Gravity.CENTER_VERTICAL);row3.addView(serverLabel,new LinearLayout.LayoutParams(0,-1,1f));
  

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

 private void updateLocalInfo(TextView v){
  String city=getSharedPreferences("lazytv_local",MODE_PRIVATE).getString("city","");
  if(!city.isEmpty())v.setText(city+"   •   Local info loading");
  if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},41);return;}
  try{LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);Location loc=null;if(lm!=null){loc=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);if(loc==null)loc=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);}if(loc!=null){final Location found=loc;new Thread(()->loadLocalData(v,found)).start();}}
  catch(SecurityException ignored){}
 }
 private void loadLocalData(TextView v,Location found){
  String city="Current location";try{Geocoder g=new Geocoder(this,java.util.Locale.getDefault());List<android.location.Address>a=g.getFromLocation(found.getLatitude(),found.getLongitude(),1);if(a!=null&&!a.isEmpty()&&a.get(0).getLocality()!=null)city=a.get(0).getLocality();}catch(Exception ignored){}
  String temp="--°C",prayers="";OkHttpClient http=new OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(8)).build();
  try{String u="https://api.open-meteo.com/v1/forecast?latitude="+found.getLatitude()+"&longitude="+found.getLongitude()+"&current=temperature_2m&timezone=auto";try(Response r=http.newCall(new Request.Builder().url(u).build()).execute()){if(r.isSuccessful()&&r.body()!=null){JSONObject j=new JSONObject(r.body().string());temp=Math.round(j.getJSONObject("current").getDouble("temperature_2m"))+"°C";}}}catch(Exception ignored){}
  try{String u="https://api.aladhan.com/v1/timings?latitude="+found.getLatitude()+"&longitude="+found.getLongitude()+"&method=3";try(Response r=http.newCall(new Request.Builder().url(u).build()).execute()){if(r.isSuccessful()&&r.body()!=null){JSONObject t=new JSONObject(r.body().string()).getJSONObject("data").getJSONObject("timings");prayers="Sabahu "+cleanTime(t.optString("Fajr"))+"  •  Lindja "+cleanTime(t.optString("Sunrise"))+"  •  Dreka "+cleanTime(t.optString("Dhuhr"))+"  •  Ikindia "+cleanTime(t.optString("Asr"))+"  •  Akshami "+cleanTime(t.optString("Maghrib"))+"  •  Jacia "+cleanTime(t.optString("Isha"));}}}catch(Exception ignored){}
  final String c=city,tt=temp,pp=prayers;getSharedPreferences("lazytv_local",MODE_PRIVATE).edit().putString("city",c).apply();runOnUiThread(()->v.setText(c+"   •   "+tt+(pp.isEmpty()?"":"   •   "+pp)));
 }
 private String cleanTime(String x){if(x==null)return"--:--";int p=x.indexOf(' ');return p>0?x.substring(0,p):x;}

 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==41){android.widget.Toast.makeText(this,grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED?"Location enabled":"Choose city manually in Settings",android.widget.Toast.LENGTH_SHORT).show();recreate();}}

 private void reloadPortal(){Playlist p=active();if(p==null){openPlaylists();return;}Intent i=new Intent(this,LiveTvActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");i.putExtra("force_reload",true);startActivity(i);}
 private void showServers(){Playlist p=active();if(p!=null){new android.app.AlertDialog.Builder(this).setTitle("Active TV source").setMessage(p.getName()+"\n\nLive TV, Movies and Series use this active playlist/provider.").setPositiveButton("Open Live TV",(d,w)->{Intent i=new Intent(this,LiveTvActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");startActivity(i);}).setNeutralButton("Manage Playlists",(d,w)->openPlaylists()).setNegativeButton("Close",null).show();return;}new android.app.AlertDialog.Builder(this).setTitle("No active server").setMessage("No real server/provider is configured. Add or activate a playlist first.").setPositiveButton("Manage Playlists",(d,w)->openPlaylists()).setNegativeButton("Close",null).show();}
 private void openPlaylists(){startActivity(new Intent(this,PlaylistManagerActivity.class));}
 private Playlist active(){PlaylistStorage store=new PlaylistStorage(this);String id=store.getActiveId();return id==null?null:store.get(id);}
 private void openCatalog(String type){Playlist p=active();if(p==null){openPlaylists();return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");if(type!=null)i.putExtra(de.lazytv.pro.catalog.CatalogActivity.EXTRA_TYPE,type);startActivity(i);}
 private Button tile(String text){
  Button b=new Button(this);
  b.setText(text);
  b.setTextSize(17);
  b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
  b.setGravity(Gravity.CENTER);
  b.setPadding(dp(6),dp(6),dp(6),dp(5));
  Drawable icon=new MenuIconDrawable(text); icon.setBounds(0,0,dp(42),dp(42)); b.setCompoundDrawables(null,icon,null,null); b.setCompoundDrawablePadding(dp(4));
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
  p.setMargins(dp(4),dp(5),dp(4),dp(5));
  return p;
 }

 private final class MenuIconDrawable extends Drawable {
  private final String type; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  MenuIconDrawable(String type){this.type=type;}
  public void draw(Canvas c){Rect r=getBounds();float w=r.width(),h=r.height(),cx=r.centerX(),cy=r.centerY();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3f,w*.075f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(iconColor());
   if("LIVE TV".equals(type)){c.drawRoundRect(w*.12f,h*.20f,w*.88f,h*.72f,w*.08f,w*.08f,p);c.drawLine(w*.36f,h*.84f,w*.64f,h*.84f,p);c.drawLine(cx,h*.72f,cx,h*.84f,p);c.drawLine(w*.38f,h*.08f,cx,h*.20f,p);c.drawLine(w*.62f,h*.08f,cx,h*.20f,p);}
   else if("MOVIES".equals(type)){c.drawCircle(cx,cy,w*.30f,p);c.drawCircle(cx,cy,w*.06f,p);for(int i=0;i<4;i++){double a=i*Math.PI/2;c.drawCircle(cx+(float)Math.cos(a)*w*.17f,cy+(float)Math.sin(a)*w*.17f,w*.055f,p);}c.drawLine(w*.70f,h*.72f,w*.90f,h*.82f,p);}
   else if("SERIES".equals(type)){for(int i=0;i<3;i++)c.drawRoundRect(w*.16f,h*(.16f+i*.25f),w*.84f,h*(.31f+i*.25f),w*.05f,w*.05f,p);}
   else if("PLAYLISTS".equals(type)||"SERVERS".equals(type)){for(int i=0;i<3;i++){float y=h*(.22f+i*.27f);c.drawRoundRect(w*.18f,y,w*.82f,y+h*.14f,w*.04f,w*.04f,p);}}
   else if("CATCH-UP".equals(type)||"RELOAD".equals(type)){c.drawArc(w*.18f,h*.18f,w*.82f,h*.82f,-55,285,false,p);c.drawLine(w*.18f,h*.34f,w*.18f,h*.12f,p);c.drawLine(w*.18f,h*.12f,w*.38f,h*.18f,p);}
   else if("FAVORITES".equals(type)){android.graphics.Path q=new android.graphics.Path();q.moveTo(cx,h*.82f);q.cubicTo(w*.08f,h*.52f,w*.18f,h*.18f,cx,h*.34f);q.cubicTo(w*.82f,h*.18f,w*.92f,h*.52f,cx,h*.82f);c.drawPath(q,p);}
   else if("SETTINGS".equals(type)){c.drawCircle(cx,cy,w*.29f,p);c.drawCircle(cx,cy,w*.10f,p);for(int i=0;i<8;i++){double a=i*Math.PI/4;float x1=cx+(float)Math.cos(a)*w*.29f,y1=cy+(float)Math.sin(a)*w*.29f,x2=cx+(float)Math.cos(a)*w*.40f,y2=cy+(float)Math.sin(a)*w*.40f;c.drawLine(x1,y1,x2,y2,p);}}
   else if("EXIT".equals(type)){c.drawArc(w*.18f,h*.18f,w*.82f,h*.86f,-55,290,false,p);c.drawLine(cx,h*.08f,cx,h*.48f,p);}
  }
  private int iconColor(){if("MOVIES".equals(type)||"EXIT".equals(type)||"FAVORITES".equals(type))return Color.rgb(255,80,95);if("SERIES".equals(type))return Color.rgb(175,95,255);if("CATCH-UP".equals(type)||"SERVERS".equals(type))return Color.rgb(255,180,55);return Color.rgb(55,185,255);}
  public void setAlpha(int a){p.setAlpha(a);} public void setColorFilter(android.graphics.ColorFilter f){p.setColorFilter(f);} public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;} public int getIntrinsicWidth(){return dp(52);} public int getIntrinsicHeight(){return dp(52);}
 }
 private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
