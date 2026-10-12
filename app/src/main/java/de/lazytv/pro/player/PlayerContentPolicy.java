package de.lazytv.pro.player;
import de.lazytv.pro.catalog.*;
/** A series is a navigation container; only an episode has a playback identity. */
public final class PlayerContentPolicy {
 private PlayerContentPolicy(){}
 public static boolean playable(StreamItem item){return item!=null&&(item.type!=CatalogType.SERIES||item instanceof Episode);}
}
