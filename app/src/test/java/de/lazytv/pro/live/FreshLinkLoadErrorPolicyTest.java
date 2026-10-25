package de.lazytv.pro.live;

import static org.junit.Assert.*;

import androidx.media3.common.C;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo;
import de.lazytv.pro.catalog.ProviderHttpException;
import java.io.IOException;
import org.junit.Test;

public class FreshLinkLoadErrorPolicyTest {
  private LoadErrorInfo failure(int status) {
    return new LoadErrorInfo(null, null, new IOException(new ProviderHttpException(status)), 1);
  }

  @Test
  public void premium458NeverRetriesRejectedRequest() {
    assertEquals(
        C.TIME_UNSET, new FreshLinkLoadErrorPolicy(() -> true).getRetryDelayMsFor(failure(458)));
  }

  @Test
  public void unrelatedProvidersKeepDefaultPolicy() {
    assertEquals(
        new DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(failure(458)),
        new FreshLinkLoadErrorPolicy(() -> false).getRetryDelayMsFor(failure(458)));
  }

  @Test
  public void otherStatusesKeepDefaultPolicy() {
    for (int status : new int[] {401, 403, 429, 500, 503})
      assertEquals(
          new DefaultLoadErrorHandlingPolicy().getRetryDelayMsFor(failure(status)),
          new FreshLinkLoadErrorPolicy(() -> true).getRetryDelayMsFor(failure(status)));
  }
}
