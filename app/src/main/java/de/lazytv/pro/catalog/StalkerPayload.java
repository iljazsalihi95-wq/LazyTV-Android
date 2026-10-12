package de.lazytv.pro.catalog;
import org.json.*;import java.util.*;
/** Native portal arrays may be wrapped or provider-ID-keyed dictionaries. */
final class StalkerPayload {
 static JSONArray rows(String body)throws CatalogException {
  try{return rows(new JSONTokener(body.trim()).nextValue(),0);}catch(Exception error){throw new CatalogException("Stalker catalog përgjigje e pavlefshme");}
 }
 private static JSONArray rows(Object node,int depth)throws JSONException {
  if(node instanceof JSONArray)return (JSONArray)node;
  if(!(node instanceof JSONObject)||depth>5)return new JSONArray();JSONObject object=(JSONObject)node;
  for(String key:new String[]{"js","data","items","results","channels","genres","categories"}){
   Object child=object.opt(key);if(child instanceof JSONObject||child instanceof JSONArray)return rows(child,depth+1);
  }
  JSONArray result=new JSONArray();Iterator<String> keys=object.keys();
  while(keys.hasNext()) {String key=keys.next();Object value=object.opt(key);
   if(!key.matches("[0-9]+"))continue;
   JSONObject record;
   if(value instanceof JSONObject)record=new JSONObject(value.toString());
   else if(value instanceof String)record=new JSONObject().put("title",value);
   else continue;
   if(StalkerLiveParser.first(record,"id","ch_id","channel_id","category_id").isEmpty())record.put("id",key);
   result.put(record);
  }return result;
 }
 static int total(String body) {
  try{Object node=new JSONTokener(body.trim()).nextValue();for(int n=0;n<5&&node instanceof JSONObject;n++){
   JSONObject object=(JSONObject)node;int count=object.optInt("total_items",object.optInt("total",0));if(count>0)return count;
   Object next=object.opt("js");if(next==null)next=object.opt("data");node=next;
  }}catch(Exception ignored){}return 0;
 }
}
