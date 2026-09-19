package de.lazytv.pro.catalog;import android.view.*;import android.widget.*;import java.util.*;import de.lazytv.pro.R;
public class CatalogAdapter extends BaseAdapter{
 private final List<StreamItem>x=new ArrayList<>();private final List<String>labels=new ArrayList<>();private boolean labelMode;
 public void set(List<StreamItem>a){labelMode=false;x.clear();labels.clear();x.addAll(a);notifyDataSetChanged();}
 public void setLabels(List<String>a){labelMode=true;x.clear();labels.clear();labels.addAll(a);notifyDataSetChanged();}
 public int getCount(){return labelMode?labels.size():x.size();}public StreamItem getItem(int p){return labelMode?null:x.get(p);}public long getItemId(int p){return p;}
 public View getView(int p,View v,ViewGroup g){if(v==null)v=LayoutInflater.from(g.getContext()).inflate(R.layout.item_catalog_stream,g,false);TextView n=v.findViewById(R.id.stream_name),m=v.findViewById(R.id.stream_meta);if(labelMode){n.setText(labels.get(p));m.setText("");}else{StreamItem i=x.get(p);n.setText(i.name);m.setText(i.tvgId==null||i.tvgId.isEmpty()?i.type.name():i.type.name()+" • "+i.tvgId);}v.setFocusable(false);return v;}
}