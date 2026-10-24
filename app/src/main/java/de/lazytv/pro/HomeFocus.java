package de.lazytv.pro;

import android.view.View;
import android.view.ViewParent;

/** A posted focus request may outlive a tile during navigation or Activity recreation. */
final class HomeFocus {
  static void request(View tile) {
    ViewParent parent = tile.getParent();
    if (tile.isAttachedToWindow() && parent != null && tile.hasFocus())
      parent.requestChildFocus(tile, tile);
  }
}
