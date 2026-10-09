package de.lazytv.pro.player;

import androidx.media3.common.util.UnstableApi;

/** Dedicated series-episode playback entry point. Episodes are resolved before launch and
 * never treat the parent Series object as a playable stream. */
@UnstableApi
public final class EpisodePlayerActivity extends PlayerActivity {
}
