package de.lazytv.pro.catalog;
import android.graphics.*;import android.view.*;import android.widget.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import de.lazytv.pro.R;
public class CatalogAdapter extends BaseAdapter{
 private final List<StreamItem>x=new ArrayList<>();private final List<String>labels=new ArrayList<>();private boolean labelMode;private final ExecutorService images=Executors.newFixedThreadPool(4);private final Map<String,Bitmap> cache=Collections.synchronizedMap(new LinkedHashMap<String,Bitmap>(96,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Bitmap>e){return size()>160;}});
 public void set(List<StreamItem>a){labelMode=false;x.clear();labels.clear();x.addAll(a);notifyDataSetChanged();}
 public void setLabels(List<String>a){labelMode=true;x.clear();labels.clear();labels.addAll(a);notifyDataSetChanged();}
 public int getCount(){return labelMode?labels.size():x.size();} public StreamItem getItem(int p){return labelMode?null:x.get(p);} public long getItemId(int p){return p;}
 public View getView(int p,View v,ViewGroup g){
  final float d=g.getResources().getDisplayMetrics().density;
  if(v==null)v=LayoutInflater.from(g.getContext()).inflate(R.layout.item_catalog_stream,g,false);
  TextView n=v.findViewById(R.id.stream_name),m=v.findViewById(R.id.stream_meta);ImageView logo=v.findViewById(R.id.stream_logo);LinearLayout row=(LinearLayout)v;
  logo.setImageDrawable(null);logo.setVisibility(labelMode?View.GONE:View.VISIBLE);
  if(labelMode){row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.getLayoutParams().height=(int)(64*d);n.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);n.setText(labels.get(p));m.setText("");}
  else{
   StreamItem i=x.get(p);boolean poster=i.type==CatalogType.MOVIES||i.type==CatalogType.SERIES;
   row.setOrientation(poster?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);
   row.getLayoutParams().height=(int)((poster?300:72)*d);
   LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)logo.getLayoutParams();
   lp.width=poster?LinearLayout.LayoutParams.MATCH_PARENT:(int)(52*d);lp.height=(int)((poster?225:52)*d);if(poster){lp.setMargins((int)(7*d),(int)(7*d),(int)(7*d),(int)(4*d));}
   logo.setLayoutParams(lp);logo.setScaleType(poster?ImageView.ScaleType.CENTER_CROP:ImageView.ScaleType.CENTER_INSIDE);
   n.setGravity(poster?Gravity.CENTER:Gravity.START);n.setText(i.name);n.setMaxLines(poster?2:1);n.setTextSize(poster?15:17);
   m.setGravity(poster?Gravity.CENTER:Gravity.START);m.setText(i.type==CatalogType.MOVIES?"MOVIE":i.type==CatalogType.SERIES?"SERIES":"LIVE");
   if(i.logo!=null&&!i.logo.trim().isEmpty())load(logo,i.logo);else logo.setImageResource(R.drawable.ic_lazytv_launcher);
  }
  v.setFocusable(false);return v;
 }
 private void load(ImageView view,String url){view.setTag(url);Bitmap hit=cache.get(url);if(hit!=null){view.setImageBitmap(hit);return;}images.execute(()->{java.net.HttpURLConnection c=null;try{c=(java.net.HttpURLConnection)new URL(url).openConnection();c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","Mozilla/5.0 LazyTV-PRO");c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8");c.setConnectTimeout(6000);c.setReadTimeout(8000);Bitmap b=BitmapFactory.decodeStream(c.getInputStream());if(b!=null){cache.put(url,b);view.post(()->{if(url.equals(view.getTag()))view.setImageBitmap(b);});}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}});}
 public void close(){images.shutdownNow();}
}