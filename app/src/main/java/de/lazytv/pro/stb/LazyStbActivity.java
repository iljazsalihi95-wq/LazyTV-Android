package de.lazytv.pro.stb;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated profile entry. No LazyTV playlist, catalog or playback classes. */
public final class LazyStbActivity extends Activity {
    private StbProfileStore store;
    private LinearLayout root,profiles;
    private TextView status;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile long generation;
    private StbPortalClient client;
    private StbBrowser browser;
    private StbLibraryBrowser library;
    private StbProfileStore.Profile selected;
    private volatile StbPortalClient pending;
    private Future<?> connectionTask;
    @Override protected void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);store=new StbProfileStore(this);build();}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(1);StbUi.insets(root,dp(14));root.setBackgroundColor(Color.rgb(3,15,28));
        TextView title=new TextView(this);title.setText("LAZY STB");title.setTextSize(26);title.setTextColor(Color.WHITE);root.addView(title);
        Button add=StbUi.button(this,"Add Profile");root.addView(add);add.setOnClickListener(v->edit(null));
        status=new TextView(this);status.setTextColor(Color.WHITE);root.addView(status);
        ScrollView scroll=new ScrollView(this);profiles=new LinearLayout(this);profiles.setOrientation(1);scroll.addView(profiles);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);refresh();add.requestFocus();
    }
    private void refresh(){profiles.removeAllViews();for(StbProfileStore.Profile p:store.all()){
        LinearLayout row=new LinearLayout(this);row.setOrientation(1);TextView name=new TextView(this);name.setText("▶ "+p.name);name.setFocusable(true);name.setClickable(true);name.setBackground(StbUi.focus());name.setPadding(dp(12),dp(12),dp(12),dp(12));name.setOnClickListener(v->connect(p));name.setTextSize(20);name.setTextColor(Color.WHITE);row.addView(name);
        LinearLayout actions=new LinearLayout(this);for(String a:new String[]{"CONNECT","Edit Profile","Delete Profile"}){Button b=StbUi.button(this,a);actions.addView(b,new LinearLayout.LayoutParams(0,dp(56),1));b.setOnClickListener(v->{if(a.equals("CONNECT"))connect(p);else if(a.startsWith("Edit"))edit(p);else new AlertDialog.Builder(this).setTitle("Delete Profile?").setMessage(p.name).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{store.delete(p);refresh();}).show();});}row.addView(actions);profiles.addView(row);
    }status.setText(store.all().isEmpty()?"Add Profile • Portal URL + MAC Address":"Zgjidh profilin dhe CONNECT");}
    private void edit(StbProfileStore.Profile p){
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(20,12,20,12);
        EditText name=field(form,"Profile Name",p==null?"":p.name),portal=field(form,"Portal URL",p==null?"":p.portal),mac=field(form,"MAC Address",p==null?"":p.mac);
        portal.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);mac.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("LAZY STB • Profile").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String n=name.getText().toString().trim(),u=portal.getText().toString().trim(),m=mac.getText().toString().trim().toUpperCase(Locale.ROOT);if(n.isEmpty()){name.setError("Profile Name");return;}try{StbProtocol.endpoints(u);}catch(Exception e){portal.setError("Portal URL nuk është valid");return;}if(!m.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}")){mac.setError("MAC Address nuk është valid");return;}store.put(new StbProfileStore.Profile(p==null?UUID.randomUUID().toString():p.id,n,u,m));dialog.dismiss();refresh();}));dialog.show();
    }
    private EditText field(LinearLayout parent,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);parent.addView(e);return e;}
    private void connect(StbProfileStore.Profile p){
        if(connectionTask!=null)connectionTask.cancel(true);if(pending!=null)pending.close();long ticket=++generation;status.setText("Connecting • handshake • profile…");
        connectionTask=worker.submit(()->{StbPortalClient connection=null;try{connection=new StbPortalClient(p.portal,p.mac);pending=connection;connection.connect();StbPortalClient ready=connection;runOnUiThread(()->{if(ticket!=generation||isFinishing()||isDestroyed()){ready.close();return;}if(client!=null)client.close();client=ready;pending=null;selected=p;showHub();});}catch(Exception e){if(connection!=null)connection.close();runOnUiThread(()->{if(ticket==generation&&!isFinishing())status.setText(StbErrors.message(e));});}});
    }
    private void closeContent(){if(browser!=null){browser.close();browser=null;}if(library!=null){library.close();library=null;}}
    private void showHub(){generation++;closeContent();LinearLayout hub=new LinearLayout(this);hub.setOrientation(1);hub.setBackgroundColor(0xff030f1c);StbUi.insets(hub,dp(16));
        android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(de.lazytv.pro.R.drawable.ic_lazy_stb);hub.addView(logo,new LinearLayout.LayoutParams(-1,dp(72)));
        TextView title=new TextView(this);title.setText("LAZY STB • "+selected.name);title.setTextColor(Color.WHITE);title.setTextSize(24);hub.addView(title);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("Portali u lidh • zgjidh rubrikën");hub.addView(status);
        ScrollView scroll=new ScrollView(this);LinearLayout menu=new LinearLayout(this);menu.setOrientation(1);scroll.addView(menu);hub.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        String[] names={"Profile • Add / Edit / Delete","LIVE TV","MOVIES","SERIES"};for(int i=0;i<names.length;i++){final int section=i;Button b=StbUi.button(this,names[i]);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(64));lp.setMargins(0,dp(8),0,0);menu.addView(b,lp);b.setOnClickListener(v->{if(section==0)returnToProfiles();else if(section==1)loadLive();else{closeContent();library=new StbLibraryBrowser(this,selected,client,section==3,this::showHub,this::returnToProfiles);setContentView(library.root);}});if(i==1)b.requestFocus();}setContentView(hub);
    }
    private void loadLive(){long ticket=++generation;status.setText("Loading LIVE • categories / channels…");StbPortalClient portal=client;worker.execute(()->{try{List<StbProtocol.Genre> genres=portal.genres();List<StbProtocol.Channel> channels=portal.channels();Map<String,StbProtocol.Genre> groups=new HashMap<>();for(StbProtocol.Genre g:genres)groups.put(g.id,g);Map<String,String> codes=new HashMap<>();for(StbProtocol.Channel c:channels)codes.put(c.id,StbDisplay.country(c,groups.get(c.genreId)));runOnUiThread(()->{if(ticket!=generation||isFinishing()||isDestroyed())return;closeContent();browser=new StbBrowser(this,selected,portal,genres,channels,codes,this::showHub);setContentView(browser.root);});}catch(Exception error){runOnUiThread(()->{if(ticket==generation&&!isFinishing())status.setText(StbErrors.message(error));});}});}
    private void returnToProfiles(){generation++;closeContent();if(client!=null){client.close();client=null;}selected=null;build();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(browser!=null&&browser.key(event))return true;if(library!=null&&library.key(event))return true;return super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(browser!=null){browser.back();return;}if(library!=null){library.back();return;}if(client!=null){returnToProfiles();return;}super.onBackPressed();}
    @Override protected void onStart(){super.onStart();if(browser!=null)browser.resume();if(library!=null)library.resume();}
    @Override protected void onStop(){if(browser!=null)browser.pause();if(library!=null)library.pause();super.onStop();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();closeContent();if(pending!=null)pending.close();if(client!=null)client.close();super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
