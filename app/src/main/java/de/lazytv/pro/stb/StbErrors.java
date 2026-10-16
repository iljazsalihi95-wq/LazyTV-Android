package de.lazytv.pro.stb;
import java.net.*;
import javax.net.ssl.SSLException;
/** Allowlisted UI diagnostics; never echoes a provider URL, MAC, token or exception message. */
final class StbErrors {
 private StbErrors(){}
 static String message(Throwable error){for(Throwable e=error;e!=null;e=e.getCause()){
  if(e instanceof StbPortalClient.HttpFailure)return "Portali ktheu HTTP "+((StbPortalClient.HttpFailure)e).status;
  if(e instanceof UnknownHostException)return "DNS: host-i i portalit nuk u gjet";
  if(e instanceof SocketTimeoutException)return "Portali nuk u përgjigj brenda afatit";
  if(e instanceof SSLException)return "Lidhja TLS/certifikata e portalit dështoi";
  if(e instanceof ConnectException)return "Lidhja me portalin u refuzua";
 }return "Lidhja/sesioni STB dështoi. Kontrollo portalin/MAC dhe provo përsëri.";}
}
