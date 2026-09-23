package de.lazytv.pro;
import de.lazytv.pro.activation.*;import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import android.view.View;import android.graphics.*;import android.util.Base64;import java.io.*;import de.lazytv.pro.playlist.*;
public class MainActivity extends Activity {
 private Playlist active(){String id=new PlaylistStorage(this).getActiveId();return id==null?null:new PlaylistStorage(this).get(id);}
 private void openBuiltIn(String type){Intent i=new Intent(this,de.lazytv.pro.live.LiveTvActivity.class);i.putExtra("builtin_live",true);if(type!=null)i.putExtra("catalog_type",type);startActivity(i);}
 @Override protected void onCreate(Bundle b){super.onCreate(b);if(!ActivationGuard.enforce(this))return;setContentView(R.layout.activity_main);
  ((ImageView)findViewById(R.id.home_logo)).setImageResource(R.drawable.lazytv_official_logo);Base64AssetImage.load(this,(ImageView)findViewById(R.id.playlist_art),"lazytv_playlist.webp.b64");loadHomeTiles();
  DeviceIdentityManager d=new DeviceIdentityManager(this);((TextView)findViewById(R.id.home_device)).setText("Device ID  "+d.getLazyTvId());((TextView)findViewById(R.id.home_serial)).setText("Serial  "+d.getSerial());
  findViewById(R.id.home_activate).setOnClickListener(v->startActivity(new Intent(this,ActivationActivity.class)));
  findViewById(R.id.manage_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.home_live).setOnClickListener(v->openLive());
  findViewById(R.id.playlist_art).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.home_movies).setOnClickListener(v->openCatalog("MOVIES"));findViewById(R.id.home_series).setOnClickListener(v->openCatalog("SERIES"));findViewById(R.id.home_favorites).setOnClickListener(v->openCatalog(null));findViewById(R.id.home_search).setOnClickListener(v->openFreePlayer());
  findViewById(R.id.home_settings).setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
  findViewById(R.id.home_islam_films).setOnClickListener(v->startActivity(new Intent(this,de.lazytv.pro.cms.IslamicHubActivity.class)));
  
  
  
  findViewById(R.id.home_live).requestFocus();
 }
 private void loadHomeTiles(){try{InputStream in=getAssets().open("home_tiles.jpg.b64");ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);in.close();byte[] raw=Base64.decode(out.toString("UTF-8").trim(),Base64.DEFAULT);Bitmap sheet=BitmapFactory.decodeByteArray(raw,0,raw.length);if(sheet==null)return;int cw=sheet.getWidth()/4,ch=sheet.getHeight()/2;int[] ids={R.id.home_live,R.id.home_movies,R.id.home_series,R.id.home_islam_films,R.id.home_islam_series,R.id.home_lectures,R.id.home_docs,R.id.home_kids};for(int i=0;i<ids.length;i++){Bitmap tile=Bitmap.createBitmap(sheet,(i%4)*cw,(i/4)*ch,cw,ch);((ImageView)findViewById(ids[i])).setImageBitmap(tile);}}catch(Exception ignored){}}
 private void openIslamic(String c){Intent i=new Intent(this,de.lazytv.pro.cms.IslamicCatalogActivity.class);i.putExtra(de.lazytv.pro.cms.IslamicCatalogActivity.EXTRA_CATEGORY,c);startActivity(i);}
 private void openFreePlayer(){Playlist p=active();if(p==null){startActivity(new Intent(this,PlaylistManagerActivity.class));return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");startActivity(i);}
 private void openLive(){Playlist p=active();Intent i=new Intent(this,de.lazytv.pro.live.LiveTvActivity.class);if(p==null){i.putExtra("builtin_live",true);i.putExtra("source_scope","TRIAL");}else{i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");}startActivity(i);}
 private void openCatalog(String type){Playlist p=active();if(p==null){startActivity(new Intent(this,PlaylistManagerActivity.class));return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());i.putExtra("source_scope","PROVIDER");if(type!=null)i.putExtra(de.lazytv.pro.catalog.CatalogActivity.EXTRA_TYPE,type);startActivity(i);}
}