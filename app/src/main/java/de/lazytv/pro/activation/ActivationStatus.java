package de.lazytv.pro.activation;
public enum ActivationStatus { TRIAL, ACTIVE, INACTIVE, EXPIRED, BLOCKED;
 public static ActivationStatus parse(String value){if(value==null)return null;try{return valueOf(value.trim().toUpperCase(java.util.Locale.US));}catch(Exception e){return null;}}
}