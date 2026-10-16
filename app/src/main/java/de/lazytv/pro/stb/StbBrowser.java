package de.lazytv.pro.stb;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Original STB browser. All views and playback belong to this module alone. */
final class StbBrowser {
    final LinearLayout root;
    private final Activity activity;
    private final Runnable exit;
    private final LinearLayout browser,controls;
    private final TextView heading,info;
    private final FrameLayout preview;
    private final ListView countryView,categoryView,channelView;
    private final List<StbProtocol.Channel> all,visible=new ArrayList<>();
    private final Map<String,StbProtocol.Genre> genres=new LinkedHashMap<>();
    private final List<String> countries=new ArrayList<>(),categoryIds=new ArrayList<>();
    private String country="ALL",category="ALL";
    private final LazyStbPlaybackSession session;
    private boolean fullscreen,previewFocus,controlsShown;
    StbBrowser(Activity activity,StbProfileStore.Profile profile,StbPortalClient portal,List<StbProtocol.Genre> groups,List<StbProtocol.Channel> channels,Runnable exit){
        this.activity=activity;this.exit=exit;this.all=new ArrayList<>(channels);
        for(StbProtocol.Genre g:groups)genres.put(g.id,g);
        root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(dp(8),dp(6),dp(8),dp(6));root.setBackgroundColor(0xff030f1c);
        heading=text("LAZY STB • "+profile.name,17);root.addView(heading,new LinearLayout.LayoutParams(-1,dp(26)));
        preview=new FrameLayout(activity);preview.setFocusable(true);preview.setClickable(true);preview.setBackground(StbUi.focus());preview.setPadding(dp(3),dp(3),dp(3),dp(3));root.addView(preview,new LinearLayout.LayoutParams(-1,0,0.48f));
        info=text("Zgjidh kanal",16);info.setMaxLines(2);root.addView(info,new LinearLayout.LayoutParams(-1,dp(44)));
        controls=new LinearLayout(activity);root.addView(controls,new LinearLayout.LayoutParams(-1,dp(44)));
        browser=new LinearLayout(activity);root.addView(browser,new LinearLayout.LayoutParams(-1,0,0.52f));
        countryView=column("COUNTRIES",0.16f);categoryView=column("CATEGORIES",0.24f);channelView=column("CHANNELS",0.60f);
        session=new LazyStbPlaybackSession(activity,preview,portal,new LazyStbPlaybackSession.Events(){
            public void loading(){StbProtocol.Channel c=session.current();info.setText((c==null?"":c.name)+" • Loading…");}
            public void ready(){StbProtocol.Channel c=session.current();if(c!=null)info.setText(c.name);}
            public void error(String message){StbProtocol.Channel c=session.current();info.setText((c==null?"":c.name)+" • "+message);if(fullscreen)setFullscreen(false);}
        });
        addControl("CH−",()->adjacent(-1));addControl("CH+",()->adjacent(1));addControl("Player",this::chooseEngine);addControl("List",()->{setFullscreen(false);previewFocus=false;channelView.requestFocus();});addControl("Fullscreen",()->{if(session.current()!=null)setFullscreen(true);});
        preview.setOnClickListener(v->{if(session.current()!=null)setFullscreen(!fullscreen);});
        preview.setOnFocusChangeListener((v,focused)->{if(focused)previewFocus=true;});
        countries.add("ALL");TreeSet<String> found=new TreeSet<>();for(StbProtocol.Channel c:all)found.add(StbDisplay.country(c,genres.get(c.genreId)));countries.addAll(found);
        countryView.setAdapter(labels(countries));countryView.setOnItemClickListener((p,v,pos,id)->selectCountry(pos));
        categoryView.setOnItemClickListener((p,v,pos,id)->{category=categoryIds.get(pos);loadChannels();channelView.requestFocus();});
        channelView.setOnItemClickListener((p,v,pos,id)->play(visible.get(pos)));
        selectCountry(0);countryView.requestFocus();
    }
    private ListView column(String label,float weight){LinearLayout col=new LinearLayout(activity);col.setOrientation(1);col.setPadding(dp(3),dp(3),dp(3),dp(3));browser.addView(col,new LinearLayout.LayoutParams(0,-1,weight));TextView title=text(label,12);title.setTextColor(0xff65d9ff);col.addView(title,new LinearLayout.LayoutParams(-1,dp(27)));ListView list=new ListView(activity);list.setId(View.generateViewId());list.setItemsCanFocus(false);list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);list.setDividerHeight(dp(3));list.setSelector(StbUi.focus());col.addView(list,new LinearLayout.LayoutParams(-1,0,1));return list;}
    private TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private ArrayAdapter<String> labels(List<String> values){return new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,new ArrayList<>(values)){
        @Override public View getView(int pos,View old,android.view.ViewGroup parent){TextView t=(TextView)super.getView(pos,old,parent);t.setTextColor(Color.WHITE);t.setTextSize(15);t.setPadding(dp(8),dp(4),dp(4),dp(4));t.setMinHeight(dp(44));return t;}
    };}
    private void addControl(String title,Runnable click){Button b=StbUi.button(activity,title);controls.addView(b,new LinearLayout.LayoutParams(0,-1,1));b.setOnClickListener(v->click.run());}
    private void selectCountry(int index){country=countries.get(index);category="ALL";categoryIds.clear();categoryIds.add("ALL");LinkedHashSet<String> ids=new LinkedHashSet<>();for(StbProtocol.Channel c:all)if(matchesCountry(c))ids.add(c.genreId);categoryIds.addAll(ids);List<String> labels=new ArrayList<>();labels.add("ALL");for(String id:ids){StbProtocol.Genre g=genres.get(id);labels.add(g==null?"OTHER":StbDisplay.abbreviation(g.title));}categoryView.setAdapter(labels(labels));loadChannels();}
    private boolean matchesCountry(StbProtocol.Channel c){return country.equals("ALL")||country.equals(StbDisplay.country(c,genres.get(c.genreId)));}
    private void loadChannels(){visible.clear();for(StbProtocol.Channel c:all)if(matchesCountry(c)&&(category.equals("ALL")||category.equals(c.genreId)))visible.add(c);List<String> names=new ArrayList<>();for(StbProtocol.Channel c:visible)names.add(c.name);channelView.setAdapter(labels(names));}
    private void play(StbProtocol.Channel c){session.select(c);previewFocus=true;preview.requestFocus();}
    private void adjacent(int delta){List<StbProtocol.Channel> list=visible.isEmpty()?all:visible;StbProtocol.Channel c=session.current();int next=StbChannelSelection.adjacent(list,c==null?"":c.id,delta);if(next>=0)play(list.get(next));}
    private void chooseEngine(){String[] engines={"Auto • Media3 → VLC","Media3 / ExoPlayer • HLS / DASH / TS","VLC"};new AlertDialog.Builder(activity).setTitle("LAZY STB • Player").setSingleChoiceItems(engines,session.engine().ordinal(),(d,w)->{session.setEngine(LazyStbPlaybackSession.Engine.values()[w]);d.dismiss();}).setNegativeButton("Cancel",null).show();}
    private void setFullscreen(boolean value){fullscreen=value;controlsShown=false;heading.setVisibility(value?View.GONE:View.VISIBLE);browser.setVisibility(value?View.GONE:View.VISIBLE);info.setVisibility(value?View.GONE:View.VISIBLE);controls.setVisibility(value?View.GONE:View.VISIBLE);preview.setLayoutParams(new LinearLayout.LayoutParams(-1,0,value?1:0.48f));preview.requestFocus();previewFocus=true;}
    boolean back(){if(fullscreen){setFullscreen(false);return true;}if(previewFocus&&session.current()!=null){previewFocus=false;channelView.requestFocus();return true;}exit.run();return true;}
    boolean key(KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;int code=event.getKeyCode();if(code==KeyEvent.KEYCODE_BACK)return back();if(code==KeyEvent.KEYCODE_CHANNEL_UP||code==KeyEvent.KEYCODE_MEDIA_NEXT){adjacent(1);return true;}if(code==KeyEvent.KEYCODE_CHANNEL_DOWN||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS){adjacent(-1);return true;}if(fullscreen){if(code==KeyEvent.KEYCODE_MENU){setFullscreen(false);previewFocus=false;channelView.requestFocus();return true;}if(!controlsShown&&(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_UP)){controlsShown=true;controls.setVisibility(View.VISIBLE);info.setVisibility(View.VISIBLE);controls.getChildAt(0).requestFocus();return true;}}return false;}
    void pause(){session.pause();}void resume(){session.resume();}void close(){session.close();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
