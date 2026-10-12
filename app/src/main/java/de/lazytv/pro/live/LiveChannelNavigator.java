package de.lazytv.pro.live;
import java.util.List;import de.lazytv.pro.catalog.StreamItem;
final class LiveChannelNavigator {
 static StreamItem adjacent(List<StreamItem> channels,String currentId,int direction){
  if(channels==null||channels.isEmpty())return null;int index=-1;
  for(int n=0;n<channels.size();n++)if(channels.get(n).id.equals(currentId)){index=n;break;}
  if(index<0)return channels.get(direction<0?channels.size()-1:0);
  return channels.get((index+(direction<0?-1:1)+channels.size())%channels.size());
 }
}
