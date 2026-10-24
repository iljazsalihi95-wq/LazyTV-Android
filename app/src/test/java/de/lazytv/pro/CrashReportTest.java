package de.lazytv.pro;
import org.junit.Test;import static org.junit.Assert.*;
public class CrashReportTest {
 @Test public void causeChainKeepsLocationsWithoutCredentialMessages(){RuntimeException error=new RuntimeException("password=private",new NullPointerException("https://private/?token=secret"));String report=CrashReport.describe("Premium:create",error);assertTrue(report.contains("NullPointerException"));assertTrue(report.contains("CrashReportTest.java"));assertFalse(report.contains("password="));assertFalse(report.contains("token="));assertFalse(report.contains("https://"));}
}
