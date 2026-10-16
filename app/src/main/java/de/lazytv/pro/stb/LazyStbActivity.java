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
    @Override protected void onCreate(Bundle state){super.onCreate(state);store=new StbProfileStore(this);build();}
    private void build(){
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,16,24,16);root.setBackgroundColor(Color.rgb(3,15,28));
        TextView title=new TextView(this);title.setText("LAZY STB");title.setTextSize(26);title.setTextColor(Color.WHITE);root.addView(title);
        Button add=StbUi.button(this,"Add Profile");root.addView(add);add.setOnClickListener(v->edit(null));
        status=new TextView(this);status.setTextColor(Color.WHITE);root.addView(status);
        ScrollView scroll=new ScrollView(this);profiles=new LinearLayout(this);profiles.setOrientation(1);scroll.addView(profiles);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);refresh();add.requestFocus();
    }
    private void refresh(){profiles.removeAllViews();for(StbProfileStore.Profile p:store.all()){
        LinearLayout row=new LinearLayout(this);row.setOrientation(1);TextView name=new TextView(this);name.setText(p.name);name.setTextSize(20);name.setTextColor(Color.WHITE);row.addView(name);
        LinearLayout actions=new LinearLayout(this);for(String a:new String[]{"CONNECT","Edit Profile","Delete Profile"}){Button b=StbUi.button(this,a);actions.addView(b,new LinearLayout.LayoutParams(0,60,1));b.setOnClickListener(v->{if(a.equals("CONNECT"))connect(p);else if(a.startsWith("Edit"))edit(p);else new AlertDialog.Builder(this).setTitle("Delete Profile?").setMessage(p.name).setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{store.delete(p);refresh();}).show();});}row.addView(actions);profiles.addView(row);
    }status.setText(store.all().isEmpty()?"Add Profile • Portal URL + MAC Address":"Zgjidh profilin dhe CONNECT");}
    private void edit(StbProfileStore.Profile p){
        LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(20,12,20,12);
        EditText name=field(form,"Profile Name",p==null?"":p.name),portal=field(form,"Portal URL",p==null?"":p.portal),mac=field(form,"MAC Address",p==null?"":p.mac);
        portal.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);mac.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("LAZY STB • Profile").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String n=name.getText().toString().trim(),u=portal.getText().toString().trim(),m=mac.getText().toString().trim().toUpperCase(Locale.ROOT);if(n.isEmpty()){name.setError("Profile Name");return;}try{StbProtocol.endpoints(u);}catch(Exception e){portal.setError("Portal URL nuk është valid");return;}if(!m.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}")){mac.setError("MAC Address nuk është valid");return;}store.put(new StbProfileStore.Profile(p==null?UUID.randomUUID().toString():p.id,n,u,m));dialog.dismiss();refresh();}));dialog.show();
    }
    private EditText field(LinearLayout parent,String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);parent.addView(e);return e;}
    private void connect(StbProfileStore.Profile p){
        long ticket=++generation;status.setText("Handshake • profile • categories • channels…");
        worker.execute(()->{StbPortalClient connection=null;try{connection=new StbPortalClient(p.portal,p.mac);List<StbProtocol.Genre> genres=connection.genres();List<StbProtocol.Channel> channels=connection.channels();StbPortalClient ready=connection;runOnUiThread(()->{if(ticket!=generation||isFinishing()){ready.close();return;}if(client!=null)client.close();client=ready;status.setText("Portali u lidh • "+genres.size()+" kategori • "+channels.size()+" kanale");});}catch(Exception e){if(connection!=null)connection.close();runOnUiThread(()->{if(ticket==generation&&!isFinishing())status.setText("Lidhja STB dështoi. Kontrollo portalin/MAC dhe provo përsëri.");});}});
    }
    @Override protected void onDestroy(){generation++;worker.shutdownNow();if(client!=null)client.close();super.onDestroy();}
}
