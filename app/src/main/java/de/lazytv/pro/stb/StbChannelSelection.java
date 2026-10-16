package de.lazytv.pro.stb;
import java.util.List;
/** Stable identity based next/previous selection, independent of a ListView's focus index. */
public final class StbChannelSelection {
 private StbChannelSelection(){}
 public static int adjacent(List<StbProtocol.Channel> channels,String activeId,int delta){if(channels.isEmpty())return -1;int at=-1;for(int i=0;i<channels.size();i++)if(channels.get(i).id.equals(activeId)){at=i;break;}if(at<0)return delta<0?channels.size()-1:0;return Math.floorMod(at+delta,channels.size());}
}
