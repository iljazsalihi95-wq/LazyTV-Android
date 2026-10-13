package de.lazytv.pro.live;
import org.junit.Test;import static org.junit.Assert.*;import de.lazytv.pro.catalog.*;
public class LiveErrorSummaryTest {
 @Test public void preservesDnsAndTimeoutStageWithoutPrivateMessage(){assertTrue(LiveErrorSummary.connection(new CatalogException("https://private.invalid?token=secret",new java.net.UnknownHostException("private"))).contains("DNS"));assertTrue(LiveErrorSummary.connection(new java.net.SocketTimeoutException("password=secret")).contains("afatit"));assertFalse(LiveErrorSummary.connection(new RuntimeException("password=secret")).contains("secret"));}
 @Test public void showsRealHttpRefusal(){assertTrue(LiveErrorSummary.connection(new ProviderHttpException(403)).contains("403"));}
}
