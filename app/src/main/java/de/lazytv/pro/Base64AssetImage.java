package de.lazytv.pro;
import android.content.*;import android.graphics.*;import android.util.*;import android.widget.*;import java.io.*;
public final class Base64AssetImage{
 private Base64AssetImage(){}
 public static void load(Context c,ImageView v,String asset){try{InputStream in=c.getAssets().open(asset);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[]b=new byte[4096];int n;while((n=in.read(b))>0)out.write(b,0,n);in.close();byte[]raw=Base64.decode(out.toString("UTF-8").trim(),Base64.DEFAULT);v.setImageBitmap(BitmapFactory.decodeByteArray(raw,0,raw.length));}catch(Exception ignored){}}
}