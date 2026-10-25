package de.lazytv.pro.live;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import de.lazytv.pro.catalog.ProviderPlaybackPolicy;
import java.util.function.BooleanSupplier;

/** Hand provider refusals to the session resolver without replaying the rejected request. */
@UnstableApi
final class FreshLinkLoadErrorPolicy extends DefaultLoadErrorHandlingPolicy {
  private final BooleanSupplier freshLinkOwner;

  FreshLinkLoadErrorPolicy(BooleanSupplier freshLinkOwner) {
    this.freshLinkOwner = freshLinkOwner;
  }

  @Override
  public long getRetryDelayMsFor(LoadErrorInfo failure) {
    if (freshLinkOwner.getAsBoolean() && ProviderPlaybackPolicy.status(failure.exception) == 458)
      return C.TIME_UNSET;
    return super.getRetryDelayMsFor(failure);
  }
}
