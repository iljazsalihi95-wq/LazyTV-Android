package de.lazytv.pro.recording;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import de.lazytv.pro.catalog.ResolvedStream;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** User-selected SAF destination, bounded network IO, no storage permission or background wakelock. */
public final class RecordingController {
 public static final int REQUEST=7521;
 private static final String UNSUPPORTED="Ky transmetim nuk mbështet regjistrimin.";
 private final android.widget.TextView badge;
 private final Activity activity;private final java.util.function.Consumer<String> status;
 private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler ui=new Handler(Looper.getMainLooper());
 private volatile boolean recording,closed,busy;private volatile HttpURLConnection connection;private ResolvedStream pending;
 public RecordingController(Activity activity,java.util.function.Consumer<String> status){this.activity=activity;this.status=status;badge=new android.widget.TextView(activity);badge.setText("● REC");badge.setTextColor(android.graphics.Color.WHITE);badge.setBackgroundColor(0xffb00020);badge.setPadding(16,8,16,8);badge.setFocusable(false);attachBadge();}
 public void attachBadge(){android.view.ViewGroup root=activity.findViewById(android.R.id.content);if(root==null)return;android.view.ViewParent old=badge.getParent();if(old!=root){if(old instanceof android.view.ViewGroup)((android.view.ViewGroup)old).removeView(badge);android.widget.FrameLayout.LayoutParams params=new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.TOP|android.view.Gravity.END);root.addView(badge,params);}badge.setVisibility(recording?android.view.View.VISIBLE:android.view.View.GONE);badge.bringToFront();}
 public void toggle(ResolvedStream stream){
  if(recording){stop();return;}if(pending!=null||closed||busy)return;
  if(stream==null||!RecordingPolicy.candidate(stream.url,stream.metadata)){message(UNSUPPORTED);return;}
  pending=stream;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("video/mp2t").putExtra(Intent.EXTRA_TITLE,RecordingPolicy.filename(stream.title,System.currentTimeMillis()));
  try{activity.startActivityForResult(intent,REQUEST);}catch(android.content.ActivityNotFoundException unavailable){pending=null;
   new android.app.AlertDialog.Builder(activity).setTitle("Regjistro në pajisje")
    .setMessage("Zgjedhësi i skedarëve nuk është i disponueshëm. Ta ruaj regjistrimin në dosjen Movies të LazyTV?")
    .setPositiveButton("Regjistro",(d,w)->{File dir=activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES);if(dir==null)dir=new File(activity.getFilesDir(),"recordings");if(!dir.exists()&&!dir.mkdirs()){message("Dosja nuk mund të krijohet");return;}start(stream,Uri.fromFile(new File(dir,RecordingPolicy.filename(stream.title,System.currentTimeMillis()))));})
    .setNegativeButton("Anulo",null).show();
  }
 }
 public boolean result(int request,int result,Intent data){
  if(request!=REQUEST)return false;ResolvedStream stream=pending;pending=null;
  if(result!=Activity.RESULT_OK||data==null||data.getData()==null||stream==null||closed)return true;
  start(stream,data.getData());return true;
 }
 private void start(ResolvedStream stream,Uri target){
  if(closed||busy)return;recording=true;busy=true;attachBadge();status.accept("● STOP RECORDING");worker.execute(()->copy(stream,target));
 }
 private HttpURLConnection open(ResolvedStream stream)throws IOException{
  URL initial=new URL(stream.url),url=initial;
  for(int redirect=0;redirect<6;redirect++){
   if(closed||!recording)throw new InterruptedIOException();
   HttpURLConnection cn=(HttpURLConnection)url.openConnection();connection=cn;
   cn.setInstanceFollowRedirects(false);cn.setConnectTimeout(10000);cn.setReadTimeout(5000);
   boolean sameOrigin=initial.getProtocol().equalsIgnoreCase(url.getProtocol())&&initial.getAuthority().equalsIgnoreCase(url.getAuthority());
   for(java.util.Map.Entry<String,String> h:stream.headers.entrySet())if(h.getKey()!=null&&h.getValue()!=null&&!h.getValue().contains("\r")&&!h.getValue().contains("\n")){
    boolean secret=h.getKey().equalsIgnoreCase("Cookie")||h.getKey().equalsIgnoreCase("Authorization");
    if(sameOrigin||!secret)cn.setRequestProperty(h.getKey(),h.getValue());
   }
   int status=cn.getResponseCode();
   if(status==200)return cn;
   String location=cn.getHeaderField("Location");cn.disconnect();
   if((status==301||status==302||status==303||status==307||status==308)&&location!=null){
    url=new URL(url,location);if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException();
   }else throw new IOException();
  }
  throw new IOException("Redirect limit");
 }
 private void copy(ResolvedStream stream,Uri target){
  String message="Regjistrimi u ruajt.";long bytes=0;
  try{
   HttpURLConnection cn=open(stream);
   String type=cn.getContentType();if(type!=null&&(type.contains("mpegurl")||type.contains("dash")||type.contains("text/")||type.contains("json")))throw new IOException();
   try(InputStream input=new BufferedInputStream(cn.getInputStream());OutputStream output="file".equals(target.getScheme())?new FileOutputStream(new File(target.getPath())):activity.getContentResolver().openOutputStream(target,"w")){
    if(output==null)throw new IOException();byte[] packet=new byte[188];
    while(recording&&!closed){int offset=0;while(offset<188&&recording&&!closed){int n=input.read(packet,offset,188-offset);if(n<0)break;offset+=n;}
     if(offset!=188)break;if(!RecordingPolicy.clearPacket(packet))throw new IOException();output.write(packet);bytes+=188;
    }
    output.flush();
   }
   if(bytes==0)message=UNSUPPORTED;
  }catch(Exception failure){message=recording?UNSUPPORTED:"Regjistrimi u ndal.";}
  finally{HttpURLConnection cn=connection;connection=null;if(cn!=null)cn.disconnect();recording=false;busy=false;}
  final String outcome=message;ui.post(()->{if(!closed){attachBadge();status.accept("● RECORD");message(outcome);}});
 }
 public void stop(){recording=false;HttpURLConnection cn=connection;if(cn!=null)cn.disconnect();badge.setVisibility(android.view.View.GONE);if(!closed)status.accept("● RECORD");}
 public void close(){closed=true;pending=null;stop();if(badge.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)badge.getParent()).removeView(badge);worker.shutdownNow();ui.removeCallbacksAndMessages(null);}
 private void message(String text){Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
}
