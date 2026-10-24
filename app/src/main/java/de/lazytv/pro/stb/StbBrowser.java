package de.lazytv.pro.stb;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import java.util.concurrent.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Dedicated STB Live browser/player. Playback stays inside the STB portal session. */
final class StbBrowser {
    final LinearLayout root;
    private de.lazytv.pro.recording.RecordingController recorder;
    private final Activity activity; private final Runnable exit;
    private final LinearLayout browser,controls; private LinearLayout landscapeVideoColumn,landscapeBody;
    private final TextView heading,info; private final FrameLayout preview;
    private final ListView countryView,categoryView,channelView;
    private final LinearLayout compactFilters;private final Spinner countryPicker,categoryPicker;
    private final List<StbProtocol.Channel> all,visible=new ArrayList<>();
    private final Map<String,StbProtocol.Genre> genres=new LinkedHashMap<>();
    private final List<String> countries=new ArrayList<>(),categoryIds=new ArrayList<>();
    private final Map<String,String> countryCodes;
    private String country="ALL",category="ALL";
    private final LazyStbPlaybackSession session; private final StbPortalClient portal;
    private final StbFavorites favorites; private final StbLogos logos; private Button favoriteButton;
    private final TextView epgText; private final ProgressBar epgProgress;
    private final Handler ui=new Handler(Looper.getMainLooper()); private final ExecutorService epgWorker=Executors.newSingleThreadExecutor();
    private List<StbEpg.Program> programs=Collections.emptyList(); private final Map<String,String> nowTitles=new HashMap<>(); private BaseAdapter channelAdapter;
    private final int[] browserPadding=new int[4];
    private int savedSystemUi,savedWindowFlags,savedVisibleBars,savedBarsBehavior;
    private android.graphics.drawable.Drawable previewBackground;
    private boolean backKeyDown;
    private boolean favoritesOnly,closed,fullscreen,controlsShown; private long lastEpgLoad; private boolean landscape; private final float previewWeight=0.48f,browserWeight=0.52f;
    private final Runnable hideControls=this::hidePlayerControls;
    private void hidePlayerControls(){if(!closed&&fullscreen){controlsShown=false;((View)controls.getParent()).setVisibility(View.GONE);info.setVisibility(View.GONE);epgText.setVisibility(View.GONE);epgProgress.setVisibility(View.GONE);preview.requestFocus();}}
    private final Runnable epgTick=new Runnable(){public void run(){if(closed)return;renderEpg();if(session.current()!=null&&System.currentTimeMillis()-lastEpgLoad>60000)loadEpg();ui.postDelayed(this,15000);}};

    StbBrowser(Activity activity,StbProfileStore.Profile profile,StbPortalClient portal,List<StbProtocol.Genre> groups,List<StbProtocol.Channel> channels,Map<String,String> codes,Runnable exit){
        this.countryCodes=new HashMap<>(codes);this.activity=activity;this.exit=exit;this.portal=portal;this.favorites=new StbFavorites(activity,profile);this.logos=new StbLogos(profile.portal);this.all=new ArrayList<>(channels);
        android.util.DisplayMetrics dm=activity.getResources().getDisplayMetrics(); landscape=dm.widthPixels>dm.heightPixels;
        for(StbProtocol.Genre g:groups)genres.put(g.id,g);
        root=new LinearLayout(activity);root.setOrientation(1);int insetPadding=dp(landscape?5:8);root.setPadding(insetPadding,insetPadding,insetPadding,insetPadding);
        Arrays.fill(browserPadding,insetPadding);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            browserPadding[0]=dp(landscape?5:8)+insets.getSystemWindowInsetLeft();
            browserPadding[1]=dp(landscape?5:8)+insets.getSystemWindowInsetTop();
            browserPadding[2]=dp(landscape?5:8)+insets.getSystemWindowInsetRight();
            browserPadding[3]=dp(landscape?5:8)+insets.getSystemWindowInsetBottom();
            applyBrowserPadding();return insets;
        });root.requestApplyInsets();root.setBackgroundColor(0xff030f1c);
        heading=text("LAZY STB • "+profile.name+" • LIVE TV",landscape?15:17);LinearLayout top=new LinearLayout(activity);top.addView(heading,new LinearLayout.LayoutParams(0,dp(landscape?40:48),1));Button portalMenu=StbUi.button(activity,"⌂  STB MENU");top.addView(portalMenu,new LinearLayout.LayoutParams(dp(128),dp(landscape?40:48)));portalMenu.setOnClickListener(v->exit.run());root.addView(top);
        preview=new FrameLayout(activity);preview.setFocusable(true);preview.setClickable(true);preview.setBackground(StbUi.focus());preview.setPadding(dp(2),dp(2),dp(2),dp(2));root.addView(preview,new LinearLayout.LayoutParams(-1,0,previewWeight));
        info=text("Select a channel",landscape?14:16);info.setMaxLines(landscape?1:2);root.addView(info,new LinearLayout.LayoutParams(-1,dp(landscape?32:44)));
        epgText=text("NOW / NEXT • EPG unavailable",landscape?11:12);epgText.setMaxLines(2);root.addView(epgText,new LinearLayout.LayoutParams(-1,dp(landscape?42:48)));
        epgProgress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);epgProgress.setMax(100);root.addView(epgProgress,new LinearLayout.LayoutParams(-1,dp(3)));
        controls=new LinearLayout(activity);HorizontalScrollView actions=new HorizontalScrollView(activity);actions.setHorizontalScrollBarEnabled(false);actions.addView(controls);root.addView(actions,new LinearLayout.LayoutParams(-1,dp(landscape?44:50)));
        browser=new LinearLayout(activity);root.addView(browser,new LinearLayout.LayoutParams(-1,0,browserWeight));
        // Keep the video column and its SurfaceView attached across every rotation.
        root.removeView(preview);root.removeView(info);root.removeView(epgText);root.removeView(epgProgress);root.removeView(actions);root.removeView(browser);
        landscapeBody=new LinearLayout(activity);root.addView(landscapeBody,new LinearLayout.LayoutParams(-1,0,1));
        landscapeVideoColumn=new LinearLayout(activity);landscapeVideoColumn.setOrientation(LinearLayout.VERTICAL);
        landscapeBody.addView(landscapeVideoColumn);landscapeBody.addView(browser);
        landscapeVideoColumn.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        landscapeVideoColumn.addView(info);landscapeVideoColumn.addView(epgText);landscapeVideoColumn.addView(epgProgress);landscapeVideoColumn.addView(actions);
        compactFilters=new LinearLayout(activity);compactFilters.setOrientation(LinearLayout.HORIZONTAL);
        browser.addView(compactFilters);
        countryPicker=new Spinner(activity);categoryPicker=new Spinner(activity);
        countryPicker.setContentDescription("Country");categoryPicker.setContentDescription("Category");
        compactFilters.addView(countryPicker,new LinearLayout.LayoutParams(0,-1,0.42f));
        compactFilters.addView(categoryPicker,new LinearLayout.LayoutParams(0,-1,0.58f));
        countryView=column("COUNTRIES",landscape?0.20f:0.16f);categoryView=column("CATEGORIES",landscape?0.27f:0.24f);channelView=column("CHANNELS",landscape?0.53f:0.60f);
        session=new LazyStbPlaybackSession(activity,preview,portal,new LazyStbPlaybackSession.Events(){public void loading(){StbProtocol.Channel c=session.current();info.setText((c==null?"":c.name)+" • Loading…");}public void ready(){StbProtocol.Channel c=session.current();if(c!=null)info.setText("▶  "+c.name);}public void error(String message){StbProtocol.Channel c=session.current();info.setText((c==null?"":c.name)+" • "+message);if(fullscreen)setFullscreen(false);}});
        addControl("◀  CH−",()->adjacent(-1));favoriteButton=addControl("☆  FAV",()->{StbProtocol.Channel c=session.current();if(c!=null){favorites.toggle(c.id);updateFavorite();if(favoritesOnly)loadChannels();}});addControl("CH+  ▶",()->adjacent(1));addControl("▤  EPG",()->new AlertDialog.Builder(activity).setTitle("LAZY STB • NOW / NEXT").setMessage(epgText.getText()).setPositiveButton("OK",null).show());addControl("⚙  PLAYER",this::chooseEngine);addControl("☷  LIST",()->{setFullscreen(false);focusCurrentChannel();});addControl("⛶  FULL",()->{if(session.current()!=null)setFullscreen(true);});
        addControl("Play/Pause",()->{if(!session.toggleLivePause())Toast.makeText(activity,"Ky transmetim nuk mbështet pauzën / timeshift.",Toast.LENGTH_SHORT).show();});
        Button record=addControl("● RECORD",()->recorder.toggle(session.recordingStream()));recorder=new de.lazytv.pro.recording.RecordingController(activity,record::setText);
        preview.setOnClickListener(v->{if(session.current()!=null)setFullscreen(!fullscreen);});
        countries.add("ALL");countries.add("FAV");TreeSet<String> found=new TreeSet<>();for(StbProtocol.Channel c:all)found.add(countryCodes.getOrDefault(c.id,"OTHER"));countries.addAll(found);
        countryView.setAdapter(labels(countries));countryPicker.setAdapter(pickerLabels(countries));
        countryPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position>=0&&position<countries.size()&&!country.equals(countries.get(position)))selectCountry(position);}
            public void onNothingSelected(AdapterView<?> p){}
        });
        categoryPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> p,View v,int position,long id){if(position>=0&&position<categoryIds.size()&&!category.equals(categoryIds.get(position))){category=categoryIds.get(position);loadChannels();}}
            public void onNothingSelected(AdapterView<?> p){}
        });
        countryView.setOnItemClickListener((p,v,pos,id)->selectCountry(pos));categoryView.setOnItemClickListener((p,v,pos,id)->{category=categoryIds.get(pos);categoryPicker.setSelection(pos);loadChannels();channelView.requestFocus();});channelView.setOnItemClickListener((p,v,pos,id)->{if(!closed&&pos>=0&&pos<visible.size())play(visible.get(pos));});selectCountry(0);countryView.requestFocus();applyLiveLayout();
        root.addOnLayoutChangeListener((v,l,t,r,bt,ol,ot,or,ob)->{
            int width=r-l,height=bt-t;
            if(closed||width<=0||height<=0)return;
            boolean wide=width>height;
            if(wide!=landscape){
                landscape=wide;applyLiveLayout();root.requestApplyInsets();
            }
        });ui.post(epgTick);
    }
    void updateChannels(List<StbProtocol.Channel> channels, Map<String,String> codes) {
        if(closed)return;
        all.clear();all.addAll(channels);countryCodes.putAll(codes);
        String selected=country,selectedCategory=category;
        countries.clear();countries.add("ALL");countries.add("FAV");
        TreeSet<String> found=new TreeSet<>();for(StbProtocol.Channel c:all)found.add(countryCodes.getOrDefault(c.id,"OTHER"));countries.addAll(found);
        countryView.setAdapter(labels(countries));countryPicker.setAdapter(pickerLabels(countries));
        int index=Math.max(0,countries.indexOf(selected));selectCountry(index);
        if(categoryIds.contains(selectedCategory)){category=selectedCategory;categoryPicker.setSelection(categoryIds.indexOf(category));loadChannels();}
    }
    private ListView column(String label,float weight){LinearLayout col=new LinearLayout(activity);col.setOrientation(1);col.setPadding(dp(3),dp(2),dp(3),dp(2));browser.addView(col,new LinearLayout.LayoutParams(0,-1,weight));TextView title=text(label,landscape?11:12);title.setTextColor(0xff65d9ff);col.addView(title,new LinearLayout.LayoutParams(-1,dp(landscape?23:27)));ListView list=new ListView(activity);list.setId(View.generateViewId());list.setItemsCanFocus(false);list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);list.setDividerHeight(dp(2));list.setSelector(StbUi.focus());col.addView(list,new LinearLayout.LayoutParams(-1,0,1));return list;}
    private TextView text(String value,int size){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private ArrayAdapter<String> labels(List<String> values){return new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,new ArrayList<>(values)){@Override public View getView(int pos,View old,ViewGroup parent){TextView t=(TextView)super.getView(pos,old,parent);t.setTextColor(Color.WHITE);t.setTextSize(landscape?13:15);t.setPadding(dp(8),dp(2),dp(4),dp(2));t.setMinHeight(dp(landscape?34:44));return t;}};}
    private ArrayAdapter<String> pickerLabels(List<String> values){
        return new ArrayAdapter<String>(activity,android.R.layout.simple_spinner_item,new ArrayList<>(values)){
            private View label(int position,View old,ViewGroup parent){
                TextView t=old instanceof TextView?(TextView)old:text("",13);
                t.setText(getItem(position));t.setTextColor(Color.WHITE);t.setTextSize(13);
                t.setPadding(dp(6),dp(3),dp(4),dp(3));t.setMinHeight(dp(36));t.setMaxLines(2);t.setBackgroundColor(0xff102b40);
                return t;
            }
            @Override public View getView(int position,View old,ViewGroup parent){return label(position,old,parent);}
            @Override public View getDropDownView(int position,View old,ViewGroup parent){return label(position,old,parent);}
        };
    }
    private Button addControl(String title,Runnable click){Button b=StbUi.button(activity,title);b.setAllCaps(false);controls.addView(b,new LinearLayout.LayoutParams(dp(landscape?92:102),-1));b.setOnClickListener(v->click.run());return b;}
    private void selectCountry(int index){country=countries.get(index);favoritesOnly=country.equals("FAV");category="ALL";categoryIds.clear();categoryIds.add("ALL");LinkedHashSet<String> ids=new LinkedHashSet<>();for(StbProtocol.Channel c:all)if(matchesCountry(c))ids.add(c.genreId);categoryIds.addAll(ids);List<String> names=new ArrayList<>();names.add("ALL");for(String id:ids){StbProtocol.Genre g=genres.get(id);names.add(g==null?"OTHER":StbDisplay.abbreviation(g.title));}categoryView.setAdapter(labels(names));countryPicker.setSelection(index);categoryPicker.setAdapter(pickerLabels(names));categoryPicker.setSelection(0);loadChannels();}
    private boolean matchesCountry(StbProtocol.Channel c){return (favoritesOnly&&favorites.contains(c.id))||country.equals("ALL")||country.equals(countryCodes.getOrDefault(c.id,"OTHER"));}
    private void loadChannels(){visible.clear();for(StbProtocol.Channel c:all)if(matchesCountry(c)&&(category.equals("ALL")||category.equals(c.genreId)))visible.add(c);channelAdapter=new BaseAdapter(){public int getCount(){return visible.size();}public Object getItem(int pos){return visible.get(pos);}public long getItemId(int pos){return pos;}public View getView(int pos,View old,ViewGroup parent){LinearLayout row;if(old instanceof LinearLayout)row=(LinearLayout)old;else{row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);ImageView logo=new ImageView(activity);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);row.addView(logo,new LinearLayout.LayoutParams(dp(landscape?36:44),dp(landscape?36:44)));TextView name=text("",landscape?13:15);name.setPadding(dp(6),dp(2),dp(3),dp(2));name.setMaxLines(2);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.setMinimumHeight(dp(landscape?40:48));}StbProtocol.Channel c=visible.get(pos);String now=nowTitles.get(c.id);((TextView)row.getChildAt(1)).setText(c.name+(now==null?"":"\nNOW • "+now));logos.load(c.logo,(ImageView)row.getChildAt(0));return row;}};channelView.setAdapter(channelAdapter);}
    boolean recordingResult(int request,int result,android.content.Intent data){return recorder!=null&&recorder.result(request,result,data);}
    private void play(StbProtocol.Channel c){if(recorder!=null)recorder.stop();if(closed)return;StbProtocol.Channel current=session.current();if(current!=null&&current.id.equals(c.id)){preview.requestFocus();if(!fullscreen)setFullscreen(true);return;}programs=Collections.emptyList();session.select(c);renderEpg();updateFavorite();loadEpg();preview.requestFocus();}
    private void adjacent(int delta){List<StbProtocol.Channel> list=visible;StbProtocol.Channel c=session.current();int next=StbChannelSelection.adjacent(list,c==null?"":c.id,delta);if(next>=0){channelView.setSelection(next);play(list.get(next));}}
    private void focusCurrentChannel(){
        StbProtocol.Channel current=session.current();
        if(current!=null)for(int i=0;i<visible.size();i++)if(current.id.equals(visible.get(i).id)){
            channelView.setItemChecked(i,true);channelView.setSelection(i);break;
        }
        channelView.requestFocus();
    }
    private void chooseEngine(){String[] engines={"Auto • Media3 → VLC → IJK → Android","Media3 / ExoPlayer • HLS / DASH / TS","VLC","IJK / FFmpeg","Android MediaPlayer"};new AlertDialog.Builder(activity).setTitle("LAZY STB • Player").setSingleChoiceItems(engines,session.engine().ordinal(),(d,w)->{session.setEngine(StbEnginePolicy.Engine.values()[w]);loadEpg();d.dismiss();}).setNegativeButton("Cancel",null).show();}
    private void applyBrowserPadding(){
        root.setPadding(fullscreen?0:browserPadding[0],fullscreen?0:browserPadding[1],fullscreen?0:browserPadding[2],fullscreen?0:browserPadding[3]);
    }
    /** Only window/view state changes here: the attached player and portal session stay untouched. */
    private void fullscreenWindow(boolean value){
        Window window=activity.getWindow();View decor=window.getDecorView();
        if(value){
            savedSystemUi=decor.getSystemUiVisibility();savedWindowFlags=window.getAttributes().flags;
            previewBackground=preview.getBackground();
            if(Build.VERSION.SDK_INT>=30){
                WindowInsets insets=decor.getRootWindowInsets();savedVisibleBars=((savedSystemUi&View.SYSTEM_UI_FLAG_FULLSCREEN)==0&&(savedWindowFlags&WindowManager.LayoutParams.FLAG_FULLSCREEN)==0?WindowInsets.Type.statusBars():0)|((savedSystemUi&View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)==0?WindowInsets.Type.navigationBars():0);
                if(insets!=null){
                    savedVisibleBars=0;
                    if(insets.isVisible(WindowInsets.Type.statusBars()))savedVisibleBars|=WindowInsets.Type.statusBars();
                    if(insets.isVisible(WindowInsets.Type.navigationBars()))savedVisibleBars|=WindowInsets.Type.navigationBars();
                }
                WindowInsetsController controller=window.getInsetsController();
                if(controller!=null){savedBarsBehavior=controller.getSystemBarsBehavior();controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);controller.hide(WindowInsets.Type.systemBars());}
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(savedSystemUi|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            preview.setBackgroundColor(Color.BLACK);preview.setPadding(0,0,0,0);
        }else{
            window.setFlags(savedWindowFlags,WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(savedSystemUi);
            if(Build.VERSION.SDK_INT>=30){
                WindowInsetsController controller=window.getInsetsController();
                if(controller!=null){controller.setSystemBarsBehavior(savedBarsBehavior);controller.hide(WindowInsets.Type.systemBars());if(savedVisibleBars!=0)controller.show(savedVisibleBars);}
            }
            preview.setBackground(previewBackground);preview.setPadding(dp(2),dp(2),dp(2),dp(2));
        }
        applyBrowserPadding();root.requestApplyInsets();
    }
    /** Resize existing containers only. Never rebuild the browser, player or portal session. */
    private void applyLiveLayout(){
        View focused=root.findFocus();
        landscapeBody.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        int browserIndex=landscape?0:1;
        if(landscapeBody.indexOfChild(browser)!=browserIndex){
            // Move only the list panel. The video column and its surfaces never detach.
            landscapeBody.removeView(browser);
            landscapeBody.addView(browser,browserIndex);
        }
        LinearLayout.LayoutParams videoParams=landscape
            ?new LinearLayout.LayoutParams(0,-1,fullscreen?1f:0.70f)
            :new LinearLayout.LayoutParams(-1,0,fullscreen?1f:previewWeight);
        videoParams.leftMargin=landscape&&!fullscreen?dp(6):0;
        landscapeVideoColumn.setLayoutParams(videoParams);
        browser.setLayoutParams(landscape
            ?new LinearLayout.LayoutParams(0,-1,0.30f)
            :new LinearLayout.LayoutParams(-1,0,browserWeight));
        preview.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));
        info.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(landscape?28:44)));
        info.setTextSize(landscape?14:16);info.setMaxLines(landscape?1:2);
        epgText.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(landscape?38:48)));
        epgText.setTextSize(landscape?11:12);
        epgProgress.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(3)));
        ((View)controls.getParent()).setLayoutParams(new LinearLayout.LayoutParams(-1,dp(landscape?40:50)));
        for(int i=0;i<controls.getChildCount();i++){
            Button button=(Button)controls.getChildAt(i);
            button.setLayoutParams(new LinearLayout.LayoutParams(dp(landscape?92:102),-1));
            button.setTextSize(landscape?12:14);
        }
        ViewGroup top=(ViewGroup)heading.getParent();
        LinearLayout.LayoutParams titleParams=(LinearLayout.LayoutParams)heading.getLayoutParams();
        titleParams.height=dp(landscape?34:48);heading.setLayoutParams(titleParams);
        heading.setTextSize(landscape?15:17);
        View menu=top.getChildAt(1);menu.setLayoutParams(new LinearLayout.LayoutParams(dp(128),dp(landscape?34:48)));
        // Wide screens use compact country/category selectors above a readable channel list.
        browser.setOrientation(landscape?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        compactFilters.setVisibility(landscape?View.VISIBLE:View.GONE);
        compactFilters.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(42)));
        float[] weights={0.16f,0.24f,0.60f};
        for(int i=1;i<browser.getChildCount();i++){
            LinearLayout column=(LinearLayout)browser.getChildAt(i);
            column.setVisibility(landscape&&i<3?View.GONE:View.VISIBLE);
            column.setLayoutParams(landscape
                ?new LinearLayout.LayoutParams(-1,0,1)
                :new LinearLayout.LayoutParams(0,-1,weights[i-1]));
            TextView label=(TextView)column.getChildAt(0);label.setTextSize(landscape?11:12);
            label.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(landscape?23:27)));
        }
        if(countryView.getAdapter() instanceof BaseAdapter)((BaseAdapter)countryView.getAdapter()).notifyDataSetChanged();
        if(categoryView.getAdapter() instanceof BaseAdapter)((BaseAdapter)categoryView.getAdapter()).notifyDataSetChanged();
        if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();
        if(focused!=null&&focused.isShown())focused.requestFocus();else if(!fullscreen)channelView.requestFocus();
        root.requestLayout();
    }
    private void setFullscreen(boolean value){
        if(closed||fullscreen==value)return;fullscreen=value;fullscreenWindow(value);controlsShown=false;
        ((View)heading.getParent()).setVisibility(value?View.GONE:View.VISIBLE);
        browser.setVisibility(value?View.GONE:View.VISIBLE);
        info.setVisibility(value?View.GONE:View.VISIBLE);epgText.setVisibility(value?View.GONE:View.VISIBLE);
        epgProgress.setVisibility(value?View.GONE:View.VISIBLE);((View)controls.getParent()).setVisibility(value?View.GONE:View.VISIBLE);
        applyLiveLayout();preview.requestFocus();
    }
    boolean back(){if(fullscreen){setFullscreen(false);focusCurrentChannel();return true;}if(preview.hasFocus()&&session.current()!=null){focusCurrentChannel();return true;}exit.run();return true;}
    boolean key(KeyEvent event){int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_BACK){
            if(event.getAction()==KeyEvent.ACTION_UP){backKeyDown=false;return true;}
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(!backKeyDown&&event.getRepeatCount()==0){backKeyDown=true;back();}return true;}
            return true;
        }
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(code==KeyEvent.KEYCODE_CHANNEL_UP||code==KeyEvent.KEYCODE_MEDIA_NEXT){adjacent(1);return true;}if(code==KeyEvent.KEYCODE_CHANNEL_DOWN||code==KeyEvent.KEYCODE_MEDIA_PREVIOUS){adjacent(-1);return true;}if(!fullscreen&&preview.hasFocus()){if(code==KeyEvent.KEYCODE_DPAD_LEFT){adjacent(-1);return true;}if(code==KeyEvent.KEYCODE_DPAD_RIGHT){adjacent(1);return true;}}if(fullscreen){if(controlsShown){ui.removeCallbacks(hideControls);ui.postDelayed(hideControls,6000);}
        if(!controlsShown&&code==KeyEvent.KEYCODE_DPAD_LEFT){adjacent(-1);return true;}if(!controlsShown&&code==KeyEvent.KEYCODE_DPAD_RIGHT){adjacent(1);return true;}if(code==KeyEvent.KEYCODE_MENU){setFullscreen(false);focusCurrentChannel();return true;}if(!controlsShown&&(code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_DPAD_DOWN||code==KeyEvent.KEYCODE_DPAD_UP)){controlsShown=true;ui.removeCallbacks(hideControls);ui.postDelayed(hideControls,6000);((View)controls.getParent()).setVisibility(View.VISIBLE);info.setVisibility(View.VISIBLE);epgText.setVisibility(View.VISIBLE);epgProgress.setVisibility(View.VISIBLE);controls.getChildAt(0).requestFocus();return true;}}return false;}
    void pause(){if(recorder!=null)recorder.stop();backKeyDown=false;session.pause();}void resume(){session.resume();}void close(){if(recorder!=null)recorder.close();if(fullscreen)setFullscreen(false);closed=true;ui.removeCallbacksAndMessages(null);epgWorker.shutdownNow();logos.close();session.close();}
    private void updateFavorite(){StbProtocol.Channel c=session.current();favoriteButton.setText(c!=null&&favorites.contains(c.id)?"★  FAV":"☆  FAV");favoriteButton.setContentDescription("Favorite");}
    private void loadEpg(){StbProtocol.Channel c=session.current();if(c==null||closed||epgWorker.isShutdown())return;long ticket=session.ticket();lastEpgLoad=System.currentTimeMillis();epgWorker.execute(()->{List<StbEpg.Program> loaded;try{loaded=StbEpg.parse(portal.epg(c.id),c.id);}catch(Exception e){loaded=Collections.emptyList();}List<StbEpg.Program> result=loaded;ui.post(()->{if(closed||!session.accepts(ticket))return;programs=result;renderEpg();});});}
    private void renderEpg(){StbProtocol.Channel c=session.current();long now=System.currentTimeMillis();StbEpg.Program current=StbEpg.current(programs,now),next=StbEpg.next(programs,now);String value=current==null?"NOW / NEXT • EPG unavailable":"NOW  "+current.time()+" • "+current.title+" • "+current.progress(now)+"%";if(next!=null)value+="\nNEXT "+next.time()+" • "+next.title;epgText.setText(value);epgProgress.setProgress(current==null?0:current.progress(now));if(c!=null){if(current==null)nowTitles.remove(c.id);else nowTitles.put(c.id,current.time()+" • "+current.title);if(channelAdapter!=null)channelAdapter.notifyDataSetChanged();}}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
