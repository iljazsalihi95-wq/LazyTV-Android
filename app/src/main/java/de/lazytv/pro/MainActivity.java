package de.lazytv.pro;
import de.lazytv.pro.activation.*;import android.app.*;import android.content.*;import android.os.*;import android.widget.*;import android.view.View;import de.lazytv.pro.playlist.*;
public class MainActivity extends Activity {
 private Playlist active(){String id=new PlaylistStorage(this).getActiveId();return id==null?null:new PlaylistStorage(this).get(id);}
 private void openBuiltIn(String type){Intent i=new Intent(this,de.lazytv.pro.live.LiveTvActivity.class);i.putExtra("builtin_live",true);if(type!=null)i.putExtra("catalog_type",type);startActivity(i);}
 @Override protected void onCreate(Bundle b){super.onCreate(b);if(!ActivationGuard.enforce(this))return;setContentView(R.layout.activity_main);
  Base64AssetImage.load(this,(ImageView)findViewById(R.id.home_logo),"lazytv_home.webp.b64");Base64AssetImage.load(this,(ImageView)findViewById(R.id.playlist_art),"lazytv_playlist.webp.b64");
  DeviceIdentityManager d=new DeviceIdentityManager(this);((TextView)findViewById(R.id.home_device)).setText("Device ID  "+d.getLazyTvId());((TextView)findViewById(R.id.home_serial)).setText("Serial  "+d.getSerial());
  findViewById(R.id.home_activate).setOnClickListener(v->startActivity(new Intent(this,ActivationActivity.class)));
  findViewById(R.id.manage_playlists).setOnClickListener(v->startActivity(new Intent(this,PlaylistManagerActivity.class)));
  findViewById(R.id.home_live).setOnClickListener(v->openLive());
  findViewById(R.id.home_movies).setOnClickListener(v->openCatalog("MOVIES"));findViewById(R.id.home_series).setOnClickListener(v->openCatalog("SERIES"));findViewById(R.id.home_favorites).setOnClickListener(v->openCatalog(null));findViewById(R.id.home_search).setOnClickListener(v->openCatalog(null));
  findViewById(R.id.home_settings).setOnClickListener(v->Toast.makeText(this,"Settings",Toast.LENGTH_SHORT).show());findViewById(R.id.home_live).requestFocus();
 }
 private void openLive(){Playlist p=active();Intent i=new Intent(this,de.lazytv.pro.live.LiveTvActivity.class);if(p==null)i.putExtra("builtin_live",true);else i.putExtra("playlist_id",p.getId());startActivity(i);}
 private void openCatalog(String type){Playlist p=active();if(p==null){startActivity(new Intent(this,PlaylistManagerActivity.class));return;}Intent i=new Intent(this,de.lazytv.pro.catalog.CatalogActivity.class);i.putExtra("playlist_id",p.getId());if(type!=null)i.putExtra(de.lazytv.pro.catalog.CatalogActivity.EXTRA_TYPE,type);startActivity(i);}
}