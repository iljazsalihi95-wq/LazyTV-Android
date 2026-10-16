package de.lazytv.pro.stb;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import java.util.concurrent.*;
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
    private final Map<String,String> countryCodes;
    private String country="ALL",category="ALL";
    private final LazyStbPlaybackSession session;
    private final StbPortalClient portal;
    private final StbFavorites favorites;
    private final StbLogos logos;
    private Button favoriteButton;
    private final TextView epgText;
    private final ProgressBar epgProgress;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService epgWorker=Executors.newSingleThreadExecutor();
    private List<StbEpg.Program> programs=Collections.emptyList();
    private final Map<String,String> nowTitles=new HashMap<>();
    private BaseAdapter channelAdapter;
    private boolean favoritesOnly,closed;
    private long lastEpgLoad;
    private final Runnable epgTick=new Runnable(){public void run(){if(closed)return;renderEpg();if(session.current()!=null&&System.currentTimeMillis()-lastEpgLoad>60000)loadEpg();ui.postDelayed(this,15000);}};
    private boolean fullscreen,previewFocus,controlsShown;
    StbBrowser(Activity activity,StbProfileStore.Profile profile,StbPortalClient portal,List<StbProtocol.Genre> groups,List<StbProtocol.Channel> channels,Map<String,String> codes,Runnable exit){
        this.countryCodes=new HashMap<>(codes);this.activity=activity;this.exit=exit;this.portal=portal;this.favorites=new StbFavorites(activity,profile);this.logos=new StbLogos(profile.portal);this.all=new ArrayList<>(channels);
        for(StbProtocol.Genre g:groups)genres.put(g.id,g);
        root=new LinearLayout(activity);root.setOrientation(1);StbUi.insets(root,dp(8));root.setBackgroundColor(0xff030f1c);
        heading=text("LAZY STB • "+profile.name,17);LinearLayout top=new LinearLayout(activity);top.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));Button portalMenu=StbUi.button(activity,"Portal menu");top.addView(portalMenu,new LinearLayout.LayoutParams(dp(114),dp(48)));portalMenu.setOnClickListener(v->exit.run());root.addView(top);
        preview=new FrameLayout(activity);preview.setFocusable(true);preview.setClickable(true);preview.setBackground(StbUi.focus());preview.setPadding(dp(3),dp(3),dp(3),dp(3));root.addView(preview,new LinearLayout.LayoutParams(-1,0,0.48f));
        info=text("Zgjidh kanal",16);info.setMaxLines(2);root.addView(info,new LinearLayout.LayoutParams(-1,dp(44)));
        epgText=text("EPG nuk disponohet",12);epgText.setMaxLines(2);root.addView(epgText,new LinearLayout.LayoutParams(-1,dp(38)));
        epgProgress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);epgProgress.setMax(100);root.addView(epgProgress,new LinearLayout.LayoutParams(-1,dp(3)));
        controls=new LinearLayout(activity);HorizontalScrollView actions=new HorizontalScrollView(activity);actions.setHorizontalScrollBarEnabled(false);actions.addView(controls);root.addView(actions,new LinearLayout.LayoutParams(-1,dp(48)));
        browser=new LinearLayout(activity);root.addView(browser,new LinearLayout.LayoutParams(-1,0,0.52f));
        countryView=column("COUNTRIES",0.16f);categoryView=column("CATEGORIES",0.24f);channelView=column("CHANNELS",0.60f);
        session=new LazyStbPlaybackSession(activity,preview,portal,new LazyStbPlaybackSession.Events(){
            public void loading(){StbProtocol.Channel c=session.current();info.setText((c==null?"":c.name)+" • Loading…");}
            public void ready(){StbProtocol.Channel c=session.current();if(c!=null)info.setText(c.name);}
            public void error(String message){StbProtocol.Channel c=session.current();info.setText((c==null?"":c.name)+" • "+message);if(fullscreen)setFullscreen(false);}
        });
        addControl("CH−",()->adjacent(-1));favoriteButton=addControl("☆",()->{StbProtocol.Channel c=session.current();if(c!=null){favorites.toggle(c.id);updateFavorite();if(favoritesOnly)loadChannels();}});addControl("CH+",()->adjacent(1));addControl("EPG",()->new AlertDialog.Builder(activity).setTitle("LAZY STB • EPG").setMessage(epgText.getText()).setPositiveButton("OK",null).show());addControl("Player",this::chooseEngine);addControl("List",()->{setFullscreen(false);previewFocus=false;channelView.requestFocus();});addControl("Full",()->{if(session.current()!=null)setFullscreen(true);});
        preview.setOnClickListener(v->{if(session.current()!=null)setFullscreen(!fullscreen);});
        preview.setOnFocusChangeListener((v,focused)->{if(focused)previewFocus=true;});
        countries.add("ALL");countries.add("FAV");TreeSet<String> found=new TreeSet<>();for(StbProtocol.Channel c:all)found.add(countryCodes.getOrDefault(c.id,"OTHER"));countries.addAll(found);
        countryView.setAdapter(labels(countries));countryView.setOnItemClickListener((p,v,pos,id)->selectCountry(pos));
        categoryView.setOnItemClickListener((p,v,pos,id)->{category=categoryIds.get(pos);loadChannels();channelView.requestFocus();});
        channelView.setOnItemClickListener((p,v,pos,id)->{if(!closed&&pos>=0&&pos<visible.size())play(visible.get(pos));});
        selectCountry(0);countryView.requestFocus();ui.post(epgTick);
    }
    private ListView column(String label,float weight){LinearLayout col=new LinearLayout(activity);col.setOrientation(1);col.setPadding(dp(3),dp(3),dp(3),dp(3));browser.addView(col,new LinearLayout.LayoutParams(0,-1,weight));TextView title=text(label,12);title.setTextColor(0xff65d9ff);col.addView(title,new LinearLayout.LayoutParams(-1,dp(27)));ListView list=new ListView(activity);list.setId(View.generateViewId());list.setItemsCanFocus(false);list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);list.setDividerHeight(dp(3));list.setSelector(StbUi.focus());col.addView(list,new LinearLayout.LayoutParams(-1,0,1));return list;}
    private TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private ArrayAdapter<String> labels(List<String> values){return new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,new ArrayList<>(values)){
        @Override public View getView(int pos,View old,android.view.ViewGroup parent){TextView t=(TextView)super.getView(pos,old,parent);t.setTextColor(Color.WHITE);t.setTextSize(15);t.setPadding(dp(8),dp(4),dp(4),dp(4));t.setMinHeight(dp(44));return t;}
    };}
    private Button addControl(String title,Runnable click){Button b=StbUi.button(activity,title);controls.addView(b,new LinearLayout.LayoutParams(dp(78),-1));b.setOnClickListener(v->click.run());return b;}
    private void selectCountry(int index){country=countries.get(index);favoritesOnly=country.equals("FAV");category="ALL";categoryIds.clear();categoryIds.add("ALL");LinkedHashSet<String> ids=new LinkedHashSet<>();for(StbProtocol.Channel c:all)if(matchesCountry(c))ids.add(c.genreId);categoryIds.addAll(ids);List<String> labels=new ArrayList<>();labels.add("ALL");for(String id:ids){StbProtocol.Genre g=genres.get(id);labels.add(g==null?"OTHER":StbDisplay.abbreviation(g.title));}categoryView.setAdapter(labels(labels));loadChannels();}
    private boolean matchesCountry(StbProtocol.Channel c){return (favoritesOnly&&favorites.contains(c.id))||country.equals("ALL")||country.equals(countryCodes.getOrDefault(c.id,"OTHER"));}
    private void loadChannels(){visible.clear();for(StbProtocol.Channel c:all)if(matchesCountry(c)&&(category.equals("ALL")||category.equals(c.genreId)))visible.add(c);channelAdapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int pos){return visible.get(pos);}public long getItemId(int pos){return pos;}
        public View getView(int pos,View old,ViewGroup parent){LinearLayout row;if(old instanceof LinearLayout)row=(LinearLayout)old;else{row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(activity);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(logo,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView name=text("",15);name.setPadding(dp(6),dp(4),dp(3),dp(4));row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.setMinimumHeight(dp(48));}StbProtocol.Channel c=visible.get(pos);String now=nowTitles.get(c.id);((TextView)row.getChildAt(1)).setText(c.name+(now==null?"":"\n"+now));logos.load(c.logo,(ImageView)row.getChildAt(0));return row;}};channelView.setAdapter(channelAdapter);}
    private void play(StbProtocol.Channel c){if(closed)return;programs=Collections.emptyList();session.select(c);updateFavorite();loadEpg();previewFocus=true;preview.requestFocus();}
    private void adjacent(int delta){List<StbProtocol.Channel> list=visible.isEmpty()?all:visible;StbProtocol.Channel c=session.current();int next=StbChannelSelection.adjacent(list,c==null?"":c.id,delta);if(next>=0)play(list.get(next));}
    private void chooseEngine(){String[] engines={"Auto • Media3 → VLC → IJK → Android","Media3 / ExoPlayer • HLS / DASH / TS","VLC","IJK / FFmpeg","Android MediaPlayer"};new AlertDialog.Builder(activity).setTitle("LAZY STB • Player").setSingleChoiceItems(engines,session.engine().ordinal(),(d,w)->{session.setEngine(StbEnginePolicy.Engine.values()[w]);loadEpg();d.dismiss();}).setNegativeButton("Cancel",null).show();}
    private void setFullscreen(boolean value){fullscreen=value;controlsShown=false;((View)heading.getParent()).setVisibility(value?View.GONE:View.VISIBLE);browser.setVisibility(value?View.GONE:View.VISIBLE);info.setVisibility(value?View.GONE:View.VISIBLE);epgText.setVisibility(value?View.GONE:View.VISIBLE);epgProgress.setVisibility(value?View.GONE:View.VISIBLE);((View)controls.getParent()).setVisibility(value?View.GONE:View.VISIBLE);preview.setLayoutParams(new LinearLayout.LayoutParams(-1,0,value?1:0.48f));preview.requestFocus();previewFocus=true;}
    boolean back(){if(fullscreen){setFullscreen(false);return true;}if(preview.hasFocus()&&session.current()!=null){previewFocus=false;channelView.requestFocus();return true;}exit.run();return true;}
    boolean key(KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;int code=event.getKeyCode();if(code==KeyEvent.KEYCODE_BACK)return back();if(code==KeyEvent.KEYCODE_CHANNEL_UP||code==KeyEvent.KEYCODE_MEDIA_NEXT){adjacent(1);return true;}if(code==KeyEvent.KEYCODE_CHANNEL_DOWN||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS){adjacent(-1);return true;}if(fullscreen){if(code==KeyEvent.KEYCODE_MENU){setFullscreen(false);previewFocus=false;channelView.requestFocus();return true;}if(!controlsShown&&(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_UP)){controlsShown=true;((View)controls.getParent()).setVisibility(View.VISIBLE);info.setVisibility(View.VISIBLE);epgText.setVisibility(View.VISIBLE);epgProgress.setVisibility(View.VISIBLE);controls.getChildAt(0).requestFocus();return true;}}return false;}
    void pause(){session.pause();}void resume(){session.resume();}void close(){closed=true;ui.removeCallbacksAndMessages(null);epgWorker.shutdownNow();logos.close();session.close();}
    private void updateFavorite(){StbProtocol.Channel c=session.current();favoriteButton.setText(c!=null&&favorites.contains(c.id)?"★":"☆");favoriteButton.setContentDescription("Favorite");}
    private void loadEpg(){StbProtocol.Channel c=session.current();if(c==null||closed||epgWorker.isShutdown())return;long ticket=session.ticket();lastEpgLoad=System.currentTimeMillis();epgWorker.execute(()->{List<StbEpg.Program> loaded;try{loaded=StbEpg.parse(portal.epg(c.id),c.id);}catch(Exception e){loaded=Collections.emptyList();}List<StbEpg.Program> result=loaded;ui.post(()->{if(closed||!session.accepts(ticket))return;programs=result;renderEpg();});});}
    private void renderEpg(){StbProtocol.Channel c=session.current();long now=System.currentTimeMillis();StbEpg.Program current=StbEpg.current(programs,now),next=StbEpg.next(programs,now);String value=current==null?"EPG nuk disponohet":"TANI: "+current.time()+" • "+current.title;if(next!=null)value+="\nPAS: "+next.time()+" • "+next.title;epgText.setText(value);epgProgress.setProgress(current==null?0:current.progress(now));if(c!=null){if(current==null)nowTitles.remove(c.id);else nowTitles.put(c.id,current.time()+" • "+current.title);if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();}}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
