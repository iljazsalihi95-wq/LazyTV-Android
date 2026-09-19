package de.lazytv.pro.activation;
public final class ActivationResult {public final ActivationStatus status;public final long expiresAtMs,serverTimeMs;public final String message;public ActivationResult(ActivationStatus s,long e,long t,String m){status=s;expiresAtMs=e;serverTimeMs=t;message=m==null?"":m;}}
