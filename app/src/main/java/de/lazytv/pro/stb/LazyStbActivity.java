package de.lazytv.pro.stb;

import android.app.*;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated STB module. Profiles, portal session, Live, Movies and Series stay isolated from LazyTV playlist players. */
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
    @Override protected void onCreate(Bundle state){super.onCreate(state);store=new StbProfileStore(this);build();}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(1);StbUi.insets(root,dp(14));root.setBackgroundColor(Color.rgb(3,15,28));
        TextView title=new TextView(this);title.setText("LAZY STB");title.setTextSize(26);title.setTextColor(Color.WHITE);root.addView(title);
        TextView subtitle=new TextView(this);subtitle.setText("Dedicated Portal Player • Live TV • Movies • Series");subtitle.setTextSize(14);subtitle.setTextColor(0xff8fc9e8);root.addView(subtitle);
        Button add=StbUi.button(this,"＋  Add Profile");root.addView(add);add.setOnClickListener(v->edit(null));
        status=new TextView(this);status.setTextColor(Color.WHITE);root.addView(status);
        ScrollView scroll=new ScrollView(this);profiles=new LinearLayout(this);profiles.setOrientation(1);scroll.addView(profiles);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);refresh();add.requestFocus();
    }
    private void refresh(){profiles.removeAllViews();for(StbProfileStore.Profile p:store.all()){
        LinearLayout row=new LinearLayout(this);row.setOrientation(1);row.setPadding(0,dp(8),0,dp(8));TextView name=new TextView(this);name.setText("▣  "+p.name+"\n    STB Portal Profile");name.setFocusable(true);name.setClickable(true);name.setBackground(StbUi.focus());name.setPadding(dp(12),dp(12),dp(12),dp(12));name.setOnClickListener(v->connect(p));name.setTextSize(18);name.setTextColor(Color.WHITE);row.addView(name);
        LinearLayout actions=new LinearLayout(this);for(String a:new String[]{"▶ CONNECT","✎ EDIT","✕ DELETE"}){Button b=StbUi.button(this,a);actions.addView(b,new LinearLayout.LayoutParams(0,dp(54),1));b.setOnClickListener(v->{if(a.contains("CONNECT"))connect(p);else if(a.contains("EDIT"))edit(p);else new AlertDialog.Builder(this).setTitle("Delete Profile?").setMessage(p.name).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{store.delete(p);refresh();}).show();});}row.addView(actions);profiles.addView(row);
    }status.setText(store.all().isEmpty()?"Add Profile • Portal URL + MAC Address":"Choose a profile to open the dedicated STB player");}
    private void edit(StbProfileStore.Profile p){
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(20,12,20,12);
        EditText name=field(form,"Profile Name",p==null?"":p.name),portal=field(form,"Portal URL",p==null?"":p.portal),mac=field(form,"MAC Address",p==null?"":p.mac);
        portal.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);mac.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("LAZY STB • Profile").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String n=name.getText().toString().trim(),u=portal.getText().toString().trim(),m=mac.getText().toString().trim().toUpperCase(Locale.ROOT);if(n.isEmpty()){name.setError("Profile Name");return;}try{StbProtocol.endpoints(u);}catch(Exception e){portal.setError("Portal URL nuk është valid");return;}if(!m.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}")){mac.setError("MAC Address nuk është valid");return;}store.put(new StbProfileStore.Profile(p==null?UUID.randomUUID().toString():p.id,n,u,m));dialog.dismiss();refresh();}));dialog.show();
    }
    private EditText field(LinearLayout parent,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);parent.addView(e);return e;}
    private void connect(StbProfileStore.Profile p){
        if(connectionTask!=null)connectionTask.cancel(true);if(pending!=null)pending.close();long ticket=++generation;status.setText("Connecting • handshake • profile • portal…");
        connectionTask=worker.submit(()->{StbPortalClient connection=null;try{connection=new StbPortalClient(p.portal,p.mac);pending=connection;connection.connect();StbPortalClient ready=connection;runOnUiThread(()->{if(ticket!=generation||isFinishing()||isDestroyed()){ready.close();return;}if(client!=null)client.close();client=ready;pending=null;selected=p;showHub();});}catch(Exception e){if(connection!=null)connection.close();runOnUiThread(()->{if(ticket==generation&&!isFinishing())status.setText(StbErrors.message(e));});}});
    }
    private void closeContent(){if(browser!=null){browser.close();browser=null;}if(library!=null){library.close();library=null;}}
    private Button hubButton(LinearLayout menu,String icon,String label,String detail,Runnable action){Button b=StbUi.button(this,icon+"  "+label+"\n"+detail);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(18),0,dp(12),0);b.setTextSize(16);b.setAllCaps(false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(76));lp.setMargins(0,dp(8),0,0);menu.addView(b,lp);b.setOnClickListener(v->action.run());return b;}
    private void showHub(){generation++;closeContent();LinearLayout hub=new LinearLayout(this);hub.setOrientation(1);hub.setBackgroundColor(0xff030f1c);StbUi.insets(hub,dp(16));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);android.widget.ImageView logo=new android.widget.ImageView(this);logo.setImageResource(de.lazytv.pro.R.drawable.ic_lazy_stb);header.addView(logo,new LinearLayout.LayoutParams(dp(72),dp(72)));LinearLayout profileBox=new LinearLayout(this);profileBox.setOrientation(1);TextView title=new TextView(this);title.setText("LAZY STB • "+selected.name);title.setTextColor(Color.WHITE);title.setTextSize(23);profileBox.addView(title);TextView portalState=new TextView(this);portalState.setText("● Portal connected • dedicated STB session");portalState.setTextColor(0xff73e6a4);portalState.setTextSize(13);profileBox.addView(portalState);header.addView(profileBox,new LinearLayout.LayoutParams(0,-2,1));hub.addView(header);
        status=new TextView(this);status.setTextColor(0xffa8cbe0);status.setText("Provider content stays inside this STB module");hub.addView(status);
        ScrollView scroll=new ScrollView(this);LinearLayout menu=new LinearLayout(this);menu.setOrientation(1);scroll.addView(menu);hub.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        hubButton(menu,"▣","PROFILE","Portal / MAC / profile assets",this::returnToProfiles);
        Button live=hubButton(menu,"▸","LIVE TV","Countries • Categories • Channels • Preview • EPG",this::loadLive);
        hubButton(menu,"▤","MOVIES","Provider video club • Posters • Details",()->openLibrary(false));
        hubButton(menu,"▦","SERIES","Provider series • Seasons • Episodes",()->openLibrary(true));
        live.requestFocus();setContentView(hub);
    }
    private void openLibrary(boolean series){closeContent();library=new StbLibraryBrowser(this,selected,client,series,this::showHub,this::returnToProfiles);setContentView(library.root);}
    private void loadLive(){
        long ticket=++generation;status.setText("Loading LIVE • categories / channels…");StbPortalClient portal=client;
        worker.execute(()->{try{
            List<StbProtocol.Genre> genres=portal.genres();Map<String,StbProtocol.Genre> groups=new HashMap<>();for(StbProtocol.Genre g:genres)groups.put(g.id,g);
            runOnUiThread(()->{if(ticket!=generation||isFinishing()||isDestroyed())return;
                closeContent();browser=new StbBrowser(this,selected,portal,genres,Collections.emptyList(),Collections.emptyMap(),this::showHub);setContentView(browser.root);
            });
            portal.channels(channels->{
                Map<String,String> codes=new HashMap<>();for(StbProtocol.Channel c:channels)codes.put(c.id,StbDisplay.country(c,groups.get(c.genreId)));
                runOnUiThread(()->{if(ticket!=generation||isFinishing()||isDestroyed())return;
                    if(browser==null){closeContent();browser=new StbBrowser(this,selected,portal,genres,channels,codes,this::showHub);setContentView(browser.root);}
                    else browser.updateChannels(channels,codes);
                    if(channels.isEmpty())Toast.makeText(this,"Portali nuk ktheu kanale LIVE.",Toast.LENGTH_LONG).show();
                });
            });
        }catch(Exception error){runOnUiThread(()->{if(ticket==generation&&!isFinishing()){
            if(browser==null)status.setText(StbErrors.message(error));else Toast.makeText(this,StbErrors.message(error),Toast.LENGTH_LONG).show();
        }});}});
    }
    private void returnToProfiles(){generation++;closeContent();if(client!=null){client.close();client=null;}selected=null;build();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(browser!=null&&browser.key(event))return true;if(library!=null&&library.key(event))return true;return super.dispatchKeyEvent(event);}
    @Override public void onBackPressed(){if(browser!=null){browser.back();return;}if(library!=null){library.back();return;}if(client!=null){returnToProfiles();return;}super.onBackPressed();}
    @Override protected void onStart(){super.onStart();if(browser!=null)browser.resume();if(library!=null)library.resume();}
    @Override protected void onActivityResult(int request,int result,android.content.Intent data){super.onActivityResult(request,result,data);if(browser!=null)browser.recordingResult(request,result,data);else if(library!=null)library.recordingResult(request,result,data);}
    @Override protected void onStop(){if(browser!=null)browser.pause();if(library!=null)library.pause();super.onStop();}
    @Override protected void onDestroy(){generation++;worker.shutdownNow();closeContent();if(pending!=null)pending.close();if(client!=null)client.close();super.onDestroy();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
