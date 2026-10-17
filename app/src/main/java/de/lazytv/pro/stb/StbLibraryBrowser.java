package de.lazytv.pro.stb;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native STB video club: provider categories -> movies/series -> seasons -> episodes -> files. */
final class StbLibraryBrowser {
    final LinearLayout root;
    private final Activity activity;
    private final StbPortalClient portal;
    private final boolean series;
    private final Runnable exit,profiles;
    private final ExecutorService worker=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private final Handler main=new Handler(Looper.getMainLooper());
    private final StbGeneration generation=new StbGeneration();
    private final TextView status,playingTitle,positionLabel;
    private final StbVodProgress progress;
    private final SeekBar timeline;
    private final View pageControls;
    private final LinearLayout timeControls;
    private boolean dragging,ended;
    private int ticks;
    private final Runnable positionTick=new Runnable(){public void run(){if(closed)return;if(playing){updatePosition();if(++ticks%5==0)saveProgress();}main.postDelayed(this,1000);}};
    private final LinearLayout lists,playControls;
    private final FrameLayout frame;
    private final ListView categoryList,itemsList;
    private final Spinner countryList;
    private final LazyStbPlaybackSession playback;
    private final List<StbProtocol.Genre> categories=new ArrayList<>(),filteredCategories=new ArrayList<>();
    private final Map<String,String> countryCodes=new HashMap<>();
    private final List<StbProtocol.Channel> items=new ArrayList<>();
    private final Deque<Query> history=new ArrayDeque<>();
    private String type;
    private Query query=new Query("*","0","0","0",1);
    private boolean closed,playing,hasNext;
    private static final class Query {
        final String category,movie,season,episode;final int page;
        Query(String category,String movie,String season,String episode,int page){this.category=category;this.movie=movie;this.season=season;this.episode=episode;this.page=page;}
        boolean root(){return movie.equals("0");}
        Query page(int p){return new Query(category,movie,season,episode,p);}
    }
    StbLibraryBrowser(Activity activity,StbProfileStore.Profile profile,StbPortalClient portal,boolean series,Runnable exit,Runnable profiles){
        this.activity=activity;this.portal=portal;progress=new StbVodProgress(activity,profile);this.series=series;this.exit=exit;this.profiles=profiles;type=series?"series":"vod";
        root=new LinearLayout(activity);root.setOrientation(1);root.setBackgroundColor(0xff030f1c);StbUi.insets(root,dp(10));
        root.addView(label("LAZY STB • "+(series?"SERIES":"MOVIES")+" • "+profile.name,20));
        LinearLayout navigation=buttons(root);button(navigation,"Portal menu",exit);button(navigation,"Profile",profiles);button(navigation,"Back",this::back);button(navigation,"Refresh",()->load(query,false));
        status=label("Loading categories…",14);root.addView(status);
        lists=new LinearLayout(activity);root.addView(lists,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=new LinearLayout(activity);left.setOrientation(1);lists.addView(left,new LinearLayout.LayoutParams(0,-1,0.32f));
        left.addView(label("COUNTRY",12));countryList=new Spinner(activity);left.addView(countryList,new LinearLayout.LayoutParams(-1,dp(48)));left.addView(label("CATEGORIES",12));categoryList=new ListView(activity);categoryList.setSelector(StbUi.focus());left.addView(categoryList,new LinearLayout.LayoutParams(-1,0,1));
        itemsList=new ListView(activity);itemsList.setSelector(StbUi.focus());lists.addView(itemsList,new LinearLayout.LayoutParams(0,-1,0.68f));
        frame=new FrameLayout(activity);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));frame.setVisibility(View.GONE);
        playingTitle=label("",16);root.addView(playingTitle);playingTitle.setVisibility(View.GONE);
        timeControls=new LinearLayout(activity);timeControls.setOrientation(1);root.addView(timeControls);timeControls.setVisibility(View.GONE);
        positionLabel=label("0:00:00 / 0:00:00",14);timeControls.addView(positionLabel);
        timeline=new SeekBar(activity);timeline.setMax(1000);timeline.setKeyProgressIncrement(10);timeline.setContentDescription("Movie / episode position");timeControls.addView(timeline,new LinearLayout.LayoutParams(-1,dp(40)));
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){dragging=true;}public void onStopTrackingTouch(SeekBar bar){dragging=false;seekFraction(bar.getProgress());}public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&!dragging)seekFraction(value);}});
        playControls=buttons(root);button(playControls,"−30 sec",()->seekBy(-30000));button(playControls,"+30 sec",()->seekBy(30000));button(playControls,"Pause",()->playbackPause());button(playControls,"Play",()->playbackResume());button(playControls,"Player",this::engine);button(playControls,"Back",this::back);((View)playControls.getParent()).setVisibility(View.GONE);
        LinearLayout pages=buttons(root);pageControls=(View)pages.getParent();button(pages,"Previous page",()->{if(query.page>1)load(query.page(query.page-1),false);});button(pages,"Next page",()->{if(hasNext)load(query.page(query.page+1),false);});
        playback=new LazyStbPlaybackSession(activity,frame,portal,new LazyStbPlaybackSession.Events(){public void loading(){if(!closed)playingTitle.setText("Loading…");}public void ready(){if(!closed&&playback.current()!=null)playingTitle.setText(playback.current().name);}public void error(String safe){if(!closed)playingTitle.setText(safe);}public void ended(){if(!closed){ended=true;progress.clear(playback.current());playingTitle.setText("Playback përfundoi");}}});
        categoryList.setOnItemClickListener((p,v,pos,id)->{if(pos<0||pos>=filteredCategories.size()||closed)return;history.clear();load(new Query(filteredCategories.get(pos).id,"0","0","0",1),false);});
        itemsList.setOnItemClickListener((p,v,pos,id)->{if(!closed&&pos>=0&&pos<items.size())open(items.get(pos));});
        main.post(positionTick);loadCategories();
    }
    private void playbackPause(){if(!closed){saveProgress();playback.pause();}}private void playbackResume(){if(!closed){if(ended){playback.seek(0);ended=false;}playback.resume();}}
    private LinearLayout buttons(LinearLayout parent){HorizontalScrollView scroll=new HorizontalScrollView(activity);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(activity);scroll.addView(row);parent.addView(scroll,new LinearLayout.LayoutParams(-1,dp(50)));return row;}
    private void button(LinearLayout row,String text,Runnable action){Button b=StbUi.button(activity,text);row.addView(b,new LinearLayout.LayoutParams(dp(108),-1));b.setOnClickListener(v->{if(!closed)action.run();});}
    private TextView label(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(dp(6),dp(4),dp(6),dp(4));return v;}
    private ArrayAdapter<String> adapter(List<String> values){return new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,values){public View getView(int p,View old,ViewGroup parent){TextView t=(TextView)super.getView(p,old,parent);t.setTextColor(Color.WHITE);t.setTextSize(16);t.setMinHeight(dp(48));return t;}public View getDropDownView(int p,View old,ViewGroup parent){TextView t=(TextView)super.getDropDownView(p,old,parent);t.setTextColor(Color.WHITE);t.setBackgroundColor(0xff123048);return t;}};}
    private void loadCategories(){long ticket=generation.next();worker.execute(()->{try{List<StbProtocol.Genre> groups;try{groups=portal.categories(type);}catch(StbPortalClient.HttpFailure e){if(!series||(e.status!=404&&e.status!=405))throw e;type="vod";groups=portal.categories(type);}if(series&&groups.isEmpty()&&type.equals("series")){type="vod";groups=portal.categories(type);}Map<String,StbProtocol.Genre> unique=new LinkedHashMap<>();unique.put("*",new StbProtocol.Genre(new JSONObject().put("id","*").put("title","ALL")));for(StbProtocol.Genre g:groups)unique.put(g.id,g);Map<String,String> codes=new HashMap<>();StbProtocol.Channel blank=new StbProtocol.Channel(new JSONObject());for(StbProtocol.Genre g:unique.values())codes.put(g.id,StbDisplay.country(blank,g));List<StbProtocol.Genre> ready=new ArrayList<>(unique.values());main.post(()->{if(!generation.accepts(ticket)||closed)return;categories.clear();categories.addAll(ready);countryCodes.putAll(codes);List<String> countries=new ArrayList<>();countries.add("ALL");countries.addAll(new TreeSet<>(codes.values()));countryList.setAdapter(adapter(countries));countryList.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){filterCategories(countries.get(pos));}});filterCategories("ALL");load(query,false);});}catch(Exception error){failure(ticket,error);}});}
    private void filterCategories(String country){filteredCategories.clear();List<String> labels=new ArrayList<>();for(StbProtocol.Genre g:categories)if(country.equals("ALL")||country.equals(countryCodes.get(g.id))){filteredCategories.add(g);labels.add(g.title);}categoryList.setAdapter(adapter(labels));}
    private void load(Query next,boolean push){if(closed)return;if(push)history.push(query);query=next;long ticket=generation.next();status.setText("Loading • page "+next.page);worker.execute(()->{try{StbProtocol.Page page=portal.libraryPage(type,next.category,next.movie,next.season,next.episode,next.page);List<StbProtocol.Channel> rows=new ArrayList<>();for(StbProtocol.Channel item:page.items){if(next.root()&&type.equals("vod")&&(series!=(item.kind==StbProtocol.Kind.SERIES)))continue;rows.add(item);}main.post(()->{if(closed||!generation.accepts(ticket))return;items.clear();items.addAll(rows);hasNext=next.page<500&&page.hasNext(next.page);showItems();status.setText("Page "+next.page+" • "+rows.size()+" hyrje"+(hasNext?" • Next page":"")+(rows.isEmpty()?" • Nuk ka përmbajtje në këtë faqe":""));});}catch(Exception error){failure(ticket,error);}});}
    private void failure(long ticket,Exception error){main.post(()->{if(!closed&&generation.accepts(ticket))status.setText(StbErrors.message(error));});}
    private void showItems(){List<String> labels=new ArrayList<>();for(StbProtocol.Channel item:items)labels.add(item.name+(item.kind==StbProtocol.Kind.SERIES?"  › SERIES":item.kind==StbProtocol.Kind.SEASON?"  › SEASON":item.kind==StbProtocol.Kind.EPISODE&&!item.playable()?"  › EPISODE":""));itemsList.setAdapter(adapter(labels));}
    private void open(StbProtocol.Channel item){
        if(item.kind==StbProtocol.Kind.SERIES){load(new Query(query.category,item.id,"0","0",1),true);return;}
        if(item.kind==StbProtocol.Kind.SEASON){if(item.episodeNumbers.length()>0&&!item.cmd.isEmpty()){List<StbProtocol.Channel> episodes=new ArrayList<>();for(int i=0;i<item.episodeNumbers.length();i++)episodes.add(item.numberedEpisode(item.episodeNumbers.optString(i)));history.push(query);items.clear();items.addAll(episodes);showItems();status.setText("Episodes");return;}load(new Query(query.category,query.movie,item.id,"0",1),true);return;}
        if(!item.playable()){if(query.root())load(new Query(query.category,item.id,"0","0",1),true);else if(item.kind==StbProtocol.Kind.EPISODE)load(new Query(query.category,query.movie,query.season,item.id,1),true);else status.setText("Provider-i nuk ktheu skedar/command për këtë hyrje");return;}
        long resume=progress.get(item);
        AlertDialog.Builder detail=new AlertDialog.Builder(activity).setTitle(item.name).setMessage(item.plot.isEmpty()?"Play":item.plot).setNegativeButton("Cancel",null).setPositiveButton("Play from start",(d,w)->start(item,0));
        if(resume>0)detail.setNeutralButton("Resume "+StbVodProgress.time(resume),(d,w)->start(item,resume));
        detail.show();
    }
    private void start(StbProtocol.Channel item,long position){if(closed)return;saveProgress();playing=true;ended=false;ticks=0;lists.setVisibility(View.GONE);pageControls.setVisibility(View.GONE);frame.setVisibility(View.VISIBLE);playingTitle.setVisibility(View.VISIBLE);timeControls.setVisibility(View.VISIBLE);((View)playControls.getParent()).setVisibility(View.VISIBLE);if(position==0)progress.clear(item);playback.select(item,position);playback.resume();}
    private void saveProgress(){if(playing&&!ended)progress.save(playback.current(),playback.position(),playback.duration());}
    private void updatePosition(){long duration=playback.duration(),position=playback.position();positionLabel.setText(StbVodProgress.time(position)+" / "+StbVodProgress.time(duration));timeline.setEnabled(duration>0);if(!dragging)timeline.setProgress(duration>0?(int)Math.min(1000,position*1000/duration):0);}
    private void seekFraction(int value){if(!playing||closed)return;if(playback.seek(playback.duration()*value/1000))ended=false;updatePosition();}
    private void seekBy(long delta){if(!playing||closed)return;if(playback.seek(playback.position()+delta))ended=false;updatePosition();}
    private void engine(){new AlertDialog.Builder(activity).setTitle("STB Player").setSingleChoiceItems(new String[]{"Auto","Media3 / ExoPlayer","VLC","IJK / FFmpeg","Android MediaPlayer"},playback.engine().ordinal(),(d,w)->{playback.setEngine(StbEnginePolicy.Engine.values()[w]);d.dismiss();}).setNegativeButton("Cancel",null).show();}
    void back(){if(playing){saveProgress();playing=false;playback.pause();timeControls.setVisibility(View.GONE);pageControls.setVisibility(View.VISIBLE);frame.setVisibility(View.GONE);playingTitle.setVisibility(View.GONE);((View)playControls.getParent()).setVisibility(View.GONE);lists.setVisibility(View.VISIBLE);return;}if(!history.isEmpty()){load(history.pop(),false);return;}exit.run();}
    boolean key(KeyEvent event){if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getKeyCode()==KeyEvent.KEYCODE_BACK){back();return true;}return false;}
    void pause(){saveProgress();playback.pause();}void resume(){if(playing)playback.resume();}
    void close(){saveProgress();closed=true;generation.close();worker.shutdownNow();main.removeCallbacksAndMessages(null);playback.close();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
