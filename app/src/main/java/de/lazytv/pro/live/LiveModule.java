package de.lazytv.pro.live;

import android.app.Activity;
import android.content.*;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.*;
import java.util.*;

/** Provider contract; screens render facts and never own another provider's auth/navigation. */
interface LiveModule {
  LiveSources.Source source();

  Playlist playlist();

  String identity();

  int layout();

  default boolean premium() {
    return false;
  }

  default boolean managed() {
    return false;
  }

  default List<CountryGroup> groups(List<Category> c) {
    return CountryResolver.groups(c);
  }

  void openCatalog(Activity activity, String type);

  static void error(Activity activity, String message) {
    android.widget.LinearLayout root = new android.widget.LinearLayout(activity);
    root.setOrientation(android.widget.LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    android.widget.TextView text = new android.widget.TextView(activity);
    text.setText(message);
    text.setTextSize(20);
    root.addView(text);
    android.widget.Button choose = new android.widget.Button(activity);
    choose.setText("Zgjidh playlistën");
    root.addView(choose);
    choose.setOnClickListener(
        v ->
            activity.startActivity(
                new Intent(activity, de.lazytv.pro.playlist.PlaylistManagerActivity.class)));
    activity.setContentView(root);
    choose.requestFocus();
  }

  static Playlist require(Activity a, PlaylistType... types) throws CatalogException {
    Playlist p = new PlaylistStorage(a).get(a.getIntent().getStringExtra("playlist_id"));
    if (p != null) for (PlaylistType type : types) if (p.getType() == type) return p;
    throw new CatalogException("Zgjidh një playlist të përshtatshme për këtë player.");
  }

  static void catalog(Activity a, Playlist p, String type) {
    Intent i = new Intent(a, CatalogActivity.class);
    i.putExtra("playlist_id", p.getId());
    i.putExtra(CatalogActivity.EXTRA_TYPE, type);
    a.startActivity(i);
  }
}
