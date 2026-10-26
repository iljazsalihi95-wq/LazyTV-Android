package de.lazytv.pro.live;

import android.app.*;
import android.app.UiModeManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.R;
import de.lazytv.pro.catalog.*;
import de.lazytv.pro.playlist.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Native catalog view composed by independent provider Activities. No provider session is static.
 */
@UnstableApi
final class NativeLiveScreen extends android.view.ContextThemeWrapper {
  private final Activity owner;
  private volatile boolean stopped, closed;
  private final LiveModule module;
  private ReferenceFullscreenHost fullscreenHost;

  NativeLiveScreen(Activity owner, LiveModule module) {
    super(owner, owner.getTheme());
    this.owner = owner;
    this.module = module;
  }

  private Intent getIntent() {
    return owner.getIntent();
  }

  private void setContentView(int layout) {
    owner.setContentView(layout);
  }

  private <T extends View> T findViewById(int id) {
    return owner.findViewById(id);
  }

  private Window getWindow() {
    return owner.getWindow();
  }

  private boolean isFinishing() {
    return owner.isFinishing();
  }

  private boolean isDestroyed() {
    return owner.isDestroyed();
  }

  private void runOnUiThread(Runnable r) {
    owner.runOnUiThread(() -> { if (!stopped && !closed) r.run(); });
  }

  private void setRequestedOrientation(int orientation) {
    owner.setRequestedOrientation(orientation);
  }

  private de.lazytv.pro.recording.RecordingController recorder;
  private Button recordButton;
  private ExecutorService epgWorker = Executors.newFixedThreadPool(2);
  private final Handler mainUi = new Handler(Looper.getMainLooper());
  private final Map<String, EpgSchedule> schedules = new HashMap<>();
  private EpgSchedule activeSchedule = EpgSchedule.empty();
  private boolean epgLoading;
  private long lastEpgRequest;
  private LinearLayout fullControls;
  private TextView fullNow, fullNext;
  private ProgressBar fullProgress;
  private Button fullPrevious, fullFavorite, pauseButton, epgButton, ccButton;
  private final Set<Integer> consumedKeys = new HashSet<>();
  private long viewGeneration, catalogGeneration;
  private final Runnable hideControls = this::hidePlayerControls;

  private void hidePlayerControls() {
    if (fullControls != null) {
      displayState.hideControls();
      fullControls.setVisibility(View.GONE);
      if (fullscreenHost != null) fullscreenHost.activeView().requestFocus();
    }
  }

  private final Runnable epgTick =
      new Runnable() {
        public void run() {
          if (stopped || closed || isFinishing() || isDestroyed()) return;
          if (current != null) {
            renderEpg();
            if (!epgLoading && System.currentTimeMillis() - lastEpgRequest >= 60000)
              loadEpg(current, playbackGeneration);
          }
          mainUi.postDelayed(this, 1000);
        }
      };
  private ExecutorService worker = Executors.newSingleThreadExecutor();
  private ExecutorService imageWorker =
      Executors.pool(2, 32);
  private final CatalogEngine engine = new CatalogEngine();
  private Playlist p;
  private boolean builtIn, serverTen, managedBackend, managed24Mode;
  private LiveSources.Source liveSource;
  private Catalog catalog;
  private LivePlaybackSession playbackSession;
  private PlayerView playerView;
  private Spinner countrySpinner;
  private ListView categoriesView, channelsView, overlayCategories, overlayChannels;
  private TextView title, epg, status;
  private ImageView loading;
  private View idle;
  private EditText search;
  private View root, leftPanel, channelPanel, info, overlay, topBar, bottomBar, contentRow;
  private FavoritesStore favorites;
  private final Map<String, List<StreamItem>> liveItems = new HashMap<>();
  private final Map<String, Integer> liveCounts = new HashMap<>();
  private List<CountryGroup> countries = new ArrayList<>();
  private List<Category> categories = new ArrayList<>();
  private List<StreamItem> channels = new ArrayList<>(), shown = new ArrayList<>();
  private StreamItem current, pendingSelection;
  private CountryGroup country;
  private Category category;
  private final LiveDisplayState displayState = new LiveDisplayState();
  private boolean fullscreen, overlayOpen;
  private int currentIndex = -1;
  private volatile long playbackGeneration = 0L;
  private String resolvedHost = "n/a";

  void create() {
    applyDeviceOrientation();
    setContentView(module.layout());
    serverTen = module.premium();
    managedBackend = module.managed();
    builtIn = module.playlist() == null;
    p = module.playlist();
    liveSource = module.source();
    recorder =
        new de.lazytv.pro.recording.RecordingController(
            owner,
            text -> {
              if (recordButton != null) recordButton.setText(text);
            });
    favorites = new FavoritesStore(this);
    bind();
    initPlayer();
    playbackSession.freshLinkRefusals(liveSource.requiresFreshLinkOn458());
    playbackSession.provider(
        module.premium()
            ? "PREMIUM"
            : module.managed() ? "MANAGED" : p == null ? "TRIAL" : p.getType().name());
    load();
    mainUi.post(epgTick);
  }

  private void applyDeviceOrientation() {
    UiModeManager ui = (UiModeManager) getSystemService(UI_MODE_SERVICE);
    boolean tv =
        (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION)
            || getPackageManager().hasSystemFeature("android.software.leanback");
    setRequestedOrientation(
        tv ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
  }

  private void bind() {
    viewGeneration++;
    root = findViewById(R.id.live_root);
    topBar = findViewById(R.id.live_top_bar);
    bottomBar = findViewById(R.id.live_bottom_bar);
    contentRow = findViewById(R.id.live_content_row);
    leftPanel = findViewById(R.id.live_left_panel);
    channelPanel = findViewById(R.id.live_channel_panel);
    info = findViewById(R.id.live_info);
    overlay = findViewById(R.id.live_overlay);
    playerView = findViewById(R.id.live_player);
    countrySpinner = findViewById(R.id.live_country_spinner);
    categoriesView = findViewById(R.id.live_categories);
    channelsView = findViewById(R.id.live_channels);
    overlayCategories = findViewById(R.id.overlay_categories);
    overlayChannels = findViewById(R.id.overlay_channels);
    findViewById(R.id.live_refresh).setOnClickListener(v -> load());
    title = findViewById(R.id.live_title);
    epg = findViewById(R.id.live_epg);
    status = findViewById(R.id.live_status);
    search = findViewById(R.id.live_search);
    loading = findViewById(R.id.live_loading_logo);
    idle = findViewById(R.id.live_idle);
    loading.setImageResource(R.drawable.lazytv_official_logo);
    ((ImageView) findViewById(R.id.live_idle_logo))
        .setImageResource(R.drawable.lazytv_official_logo);
    ((ImageView) findViewById(R.id.live_brand_logo))
        .setImageResource(R.drawable.lazytv_official_logo);
    countrySpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
            selectCountry(pos);
          }

          public void onNothingSelected(AdapterView<?> a) {}
        });
    categoriesView.setOnItemClickListener((a, v, pos, id) -> selectCategory(pos, false));
    channelsView.setOnItemClickListener((a, v, pos, id) -> activateChannel(pos, false));
    overlayCategories.setOnItemClickListener((a, v, pos, id) -> selectCategory(pos, true));
    overlayChannels.setOnItemClickListener((a, v, pos, id) -> activateChannel(pos, true));
    findViewById(R.id.live_prev).setOnClickListener(v -> playAdjacent(-1));
    findViewById(R.id.live_next).setOnClickListener(v -> playAdjacent(1));
    findViewById(R.id.live_favorite).setOnClickListener(v -> toggleFavorite());
    View live24 = findViewById(R.id.live_nav_24);
    live24.setVisibility(managedBackend ? View.VISIBLE : View.GONE);
    live24.setOnClickListener(
        v -> {
          if (!managed24Mode) {
            managed24Mode = true;
            load();
          } else countrySpinner.requestFocus();
        });
    findViewById(R.id.live_nav_live)
        .setOnClickListener(
            v -> {
              if (managedBackend && managed24Mode) {
                managed24Mode = false;
                load();
              } else countrySpinner.requestFocus();
            });
    findViewById(R.id.live_nav_movies).setOnClickListener(v -> openCatalogType("MOVIES"));
    findViewById(R.id.live_nav_series).setOnClickListener(v -> openCatalogType("SERIES"));
    findViewById(R.id.live_nav_islam)
        .setOnClickListener(
            v -> startActivity(new Intent(this, de.lazytv.pro.cms.IslamicHubActivity.class)));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int d) {}

          public void afterTextChanged(Editable e) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            filter(s.toString());
          }
        });
    playerView.setUseController(false);
    playerView.setClickable(true);
    playerView.setFocusable(true);
    View playerPanel = findViewById(R.id.live_player_panel);
    View.OnClickListener previewClick =
        v -> {
          if (fullscreen) toggleControls();
          else setFullscreen(true);
        };
    playerView.setOnClickListener(previewClick);
    playerPanel.setClickable(true);
    playerPanel.setOnClickListener(previewClick);
    playerPanel.setOnTouchListener(
        (v, e) -> {
          if (e.getAction() == android.view.MotionEvent.ACTION_UP) {
            if (!fullscreen) setFullscreen(true);
            else toggleControls();
            return true;
          }
          return true;
        });
    epg.setOnClickListener(v -> showEpg());
    fullscreenHost = new ReferenceFullscreenHost(owner, playerView);
    buildControls();
    fullscreenHost.fullscreenView().setOnClickListener(v -> toggleControls());
    if (playbackSession != null) playbackSession.attach(playerView);
  }

  private void initPlayer() {
    LivePlaybackSession.RetryResolver resolver = item -> liveSource.resolve(item, true);
    playbackSession =
        new LivePlaybackSession(
            this,
            playerView,
            resolver,
            new LivePlaybackSession.Listener() {
              public void onBuffering(boolean buffering) {
                loading.setVisibility(buffering ? View.VISIBLE : View.GONE);
                fullscreenHost.buffering(buffering);
              }

              public void onCapabilitiesChanged() { updateCapabilities(); }

              public void onReady() {
                loading.setVisibility(View.GONE);
                fullscreenHost.buffering(false);
                fullscreenHost.error(null);
              }

              public void onFatalPlaybackError(String message) {
                fullscreenHost.buffering(false);
                loading.setVisibility(View.GONE);
                String detail =
                    message
                        + "\n"
                        + (playbackSession == null ? "" : playbackSession.diagnosticSummary());
                fullscreenHost.error(detail);
                android.util.Log.e("LazyTV-LiveRequest", detail);
                Toast.makeText(NativeLiveScreen.this, message, Toast.LENGTH_LONG).show();
              }
            });
  }

  private boolean isStalkerPlaylist() {
    return !serverTen
        && !managedBackend
        && !builtIn
        && p != null
        && p.getType() == PlaylistType.STALKER_PORTAL;
  }

  private String stalkerError(Exception e) {
    int code = ProviderPlaybackPolicy.status(e);
    if (code > 0) return "Stalker: " + ProviderPlaybackPolicy.message(code);
    String message = e.getMessage();
    if (message != null && message.toLowerCase(Locale.ROOT).contains("unauthorized"))
      return "Stalker: session-i u refuzua edhe pas rifreskimit.";
    return "Stalker: nuk u mor URL e vlefshme për kanalin.";
  }

  private String sourceId() {
    return module.identity();
  }

  private void load() {
    final long catalogTicket = ++catalogGeneration;
    final boolean wanted24 = managed24Mode;
    final String keptCategory = category == null ? null : category.id,
        keptChannel = current == null ? null : current.id;
    loading.setVisibility(View.VISIBLE);
    status.setText(serverTen ? "Duke ngarkuar Server 10…" : "Duke ngarkuar playlistën…");
    worker.execute(
        () -> {
          try {
            Catalog c = liveSource.load();
            Map<String, List<StreamItem>> items = new HashMap<>();
            Map<String, Integer> counts = new HashMap<>();
            List<Category> liveCats = new ArrayList<>();
            for (Category z : c.categories(CatalogType.LIVE)) {
              List<StreamItem> li = new ArrayList<>();
              for (StreamItem item : c.items(CatalogType.LIVE, z.id))
                if (!managedBackend || wanted24 == "24_7".equals(item.meta("managed_tab")))
                  li.add(item);
              if (!li.isEmpty()) {
                liveCats.add(z);
                items.put(z.id, li);
                counts.put(z.id, li.size());
              }
            }
            List<CountryGroup> grouped = module.groups(liveCats);
            runOnUiThread(
                () -> {
                  if (catalogTicket != catalogGeneration || isFinishing() || isDestroyed()) return;
                  loading.setVisibility(View.GONE);
                  catalog = c;
                  liveItems.clear();
                  liveItems.putAll(items);
                  liveCounts.clear();
                  liveCounts.putAll(counts);
                  countries = new ArrayList<>(grouped);
                  country = null;
                  category = null;
                  categories = new ArrayList<>();
                  channels = Collections.emptyList();
                  shown = new ArrayList<>();
                  categoriesView.setAdapter(adapter(Collections.emptyList()));
                  overlayCategories.setAdapter(adapter(Collections.emptyList()));
                  channelsView.setAdapter(channelAdapter());
                  overlayChannels.setAdapter(channelAdapter());
                  status.setText(countries.isEmpty() ? "Nuk ka kanale aktive në këtë seksion" : "");
                  status.setVisibility(countries.isEmpty() ? View.VISIBLE : View.GONE);
                  countrySpinner.setAdapter(spinnerAdapter(countryLabels()));
                  if (!countries.isEmpty()) {
                    String
                        wantedCategory =
                            keptCategory == null
                                ? getIntent().getStringExtra("open_category_id")
                                : keptCategory,
                        wantedStream =
                            keptChannel == null
                                ? getIntent().getStringExtra("open_stream_id")
                                : keptChannel;
                    int cp = 0;
                    for (int n = 0; n < countries.size(); n++) {
                      for (Category z : countries.get(n).categories)
                        if (z.id.equals(wantedCategory)) {
                          cp = n;
                          break;
                        }
                    }
                    countrySpinner.setSelection(cp);
                    selectCountry(cp);
                    if (wantedCategory != null) {
                      for (int n = 0; n < categories.size(); n++)
                        if (categories.get(n).id.equals(wantedCategory)) {
                          selectCategory(n, false);
                          break;
                        }
                      if (wantedStream != null) {
                        for (int n = 0; n < shown.size(); n++)
                          if (shown.get(n).id.equals(wantedStream)) {
                            if (current == null) activateChannel(n, false);
                            else {
                              currentIndex = n;
                              channelsView.setSelection(n);
                            }
                            break;
                          }
                      }
                    } else countrySpinner.requestFocus();
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (catalogTicket != catalogGeneration || isFinishing() || isDestroyed()) return;
                  loading.setVisibility(View.GONE);
                  String msg =
                      serverTen
                          ? "Serveri Premium nuk mund të ngarkohet për momentin."
                          : (e.getMessage() == null
                              ? "Katalogu nuk mund të ngarkohet."
                              : e.getMessage());
                  status.setText(msg);
                  Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                });
          }
        });
  }

  private List<String> countryLabels() {
    List<String> x = new ArrayList<>();
    for (CountryGroup g : countries) {
      int n = 0;
      for (Category z : g.categories) n += liveCounts.getOrDefault(z.id, 0);
      x.add(g.label + "  (" + n + ")");
    }
    return x;
  }

  private List<String> categoryLabels() {
    List<String> x = new ArrayList<>();
    for (Category c : categories)
      x.add(CountryResolver.categoryLabel(c.name) + "  (" + liveCounts.getOrDefault(c.id, 0) + ")");
    return x;
  }

  private void selectCountry(int pos) {
    if (pos < 0 || pos >= countries.size()) return;
    if (country != null
        && countries.get(pos).key.equals(country.key)
        && categoriesView.getAdapter() != null) return;
    country = countries.get(pos);
    categories = new ArrayList<>(country.categories);
    List<String> x = categoryLabels();
    categoriesView.setAdapter(adapter(x));
    overlayCategories.setAdapter(adapter(x));
    channels = Collections.emptyList();
    shown = new ArrayList<>();
    channelsView.setAdapter(channelAdapter());
    overlayChannels.setAdapter(channelAdapter());
    if (!x.isEmpty()) {
      categoriesView.setSelection(0);
      selectCategory(0, false);
    }
  }

  private void selectCategory(int pos, boolean fromOverlay) {
    if (pos < 0 || pos >= categories.size()) return;
    category = categories.get(pos);
    channels = liveItems.getOrDefault(category.id, Collections.emptyList());
    filter("");
    if (fromOverlay) {
      overlayChannels.requestFocus();
      if (!shown.isEmpty()) overlayChannels.setSelection(0);
    } else {
      channelsView.requestFocus();
      if (!shown.isEmpty()) channelsView.setSelection(0);
    }
  }

  private int currentShownIndex() {
    if (current == null) return -1;
    for (int n = 0; n < shown.size(); n++) {
      StreamItem i = shown.get(n);
      if (i != null && current.id.equals(i.id)) return n;
    }
    return -1;
  }

  private void syncCurrentChannelPosition() {
    int n = currentShownIndex();
    if (n < 0) return;
    currentIndex = n;
    channelsView.setSelection(n);
    overlayChannels.setSelection(n);
  }

  private void filter(String q) {
    q = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    shown = new ArrayList<>();
    for (StreamItem i : channels)
      if (q.isEmpty() || (i.name != null && i.name.toLowerCase(Locale.ROOT).contains(q)))
        shown.add(i);
    channelsView.setAdapter(channelAdapter());
    overlayChannels.setAdapter(channelAdapter());
    syncCurrentChannelPosition();
  }

  private void activateChannel(int pos, boolean fromOverlay) {
    if (pos < 0 || pos >= shown.size()) return;
    StreamItem item = shown.get(pos);
    if (pendingSelection != null && pendingSelection.id.equals(item.id)) return;
    if (pendingSelection == null
        && current != null
        && current.id.equals(item.id)
        && playbackSession != null
        && playbackSession.isActive()) {
      syncCurrentChannelPosition();
      if (fromOverlay) {
        hideOverlay();
        if (!fullscreen) setFullscreen(true);
      } else if (!fullscreen) setFullscreen(true);
      return;
    }
    play(pos, fromOverlay);
  }

  private void play(int pos, boolean fromOverlay) {
    if (pos < 0 || pos >= shown.size()) return;
    playChannel(shown.get(pos), fromOverlay);
  }

  private void playChannel(StreamItem item, boolean fromOverlay) {
    if (recorder != null) recorder.stop();
    final long generation = ++playbackGeneration;
    pendingSelection = item;
    playbackSession.beginSelection();
    fullscreenHost.error(null);
    loading.setVisibility(View.VISIBLE);
    worker.execute(
        () -> {
          if (generation != playbackGeneration || Thread.currentThread().isInterrupted()) return;
          try {
            ResolvedStream r = liveSource.resolve(item, false);
            runOnUiThread(
                () -> {
                  if (generation != playbackGeneration || isFinishing() || isDestroyed()) return;
                  pendingSelection = null;
                  playResolved(item, r);
                  if (fromOverlay) hideOverlay();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (generation != playbackGeneration || isFinishing() || isDestroyed()) return;
                  pendingSelection = null;
                  loading.setVisibility(View.GONE);
                  String failure =
                      isStalkerPlaylist() ? stalkerError(e) : LiveErrorSummary.connection(e);
                  int code = ProviderPlaybackPolicy.status(e);
                  String detail =
                      "RESOLVE / CREATE_LINK: " + failure + (code > 0 ? " • HTTP " + code : "");
                  fullscreenHost.error(detail);
                  android.util.Log.e("LazyTV-LiveRequest", detail);
                  Toast.makeText(this, failure, Toast.LENGTH_LONG).show();
                });
          }
        });
  }

  private void playResolved(StreamItem item, ResolvedStream r) {
    idle.setVisibility(View.GONE);
    current = item;
    currentIndex = shown.indexOf(item);
    try {
      resolvedHost = new java.net.URL(r.url).getHost();
    } catch (Exception ignored) {
      resolvedHost = "n/a";
    }
    playbackSession.play(item, r);
    title.setText(item.name);
    activeSchedule = schedules.getOrDefault(sourceId() + "|" + item.id, EpgSchedule.empty());
    epgLoading = false;
    lastEpgRequest = 0;
    renderEpg();
    loadEpg(item, playbackGeneration);
    ((Button) findViewById(R.id.live_favorite))
        .setText(favorites.contains(sourceId(), item) ? "★ Favorite" : "☆ Favorite");
    syncCurrentChannelPosition();
    channelsView.invalidateViews();
    overlayChannels.invalidateViews();
  }

  private ArrayAdapter<String> spinnerAdapter(List<String> x) {
    return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, x) {
      public View getView(int p, View v, android.view.ViewGroup g) {
        TextView t = (TextView) super.getView(p, v, g);
        t.setTextColor(android.graphics.Color.WHITE);
        t.setTextSize(15);
        t.setSingleLine(true);
        t.setPadding(10, 8, 8, 8);
        return t;
      }

      public View getDropDownView(int p, View v, android.view.ViewGroup g) {
        TextView t = (TextView) super.getDropDownView(p, v, g);
        t.setTextColor(android.graphics.Color.WHITE);
        t.setBackgroundResource(R.drawable.bg_live_row);
        t.setTextSize(16);
        t.setPadding(16, 14, 16, 14);
        return t;
      }
    };
  }

  private ArrayAdapter<String> adapter(List<String> x) {
    return new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, x) {
      public View getView(int p, View v, android.view.ViewGroup g) {
        TextView t = (TextView) super.getView(p, v, g);
        t.setTextColor(android.graphics.Color.WHITE);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setPadding(10, 12, 8, 12);
        t.setFocusable(false);
        return t;
      }
    };
  }

  private android.widget.BaseAdapter channelAdapter() {
    return new android.widget.BaseAdapter() {
      public int getCount() {
        return shown.size();
      }

      public Object getItem(int n) {
        return shown.get(n);
      }

      public long getItemId(int n) {
        return n;
      }

      public View getView(int n, View v, android.view.ViewGroup parent) {
        LinearLayout row =
            v instanceof LinearLayout ? (LinearLayout) v : new LinearLayout(NativeLiveScreen.this);
        row.removeAllViews();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(8, 6, 8, 6);
        row.setMinimumHeight((int) (52 * getResources().getDisplayMetrics().density));
        row.setBackgroundResource(R.drawable.bg_live_row);
        ImageView logo = new ImageView(NativeLiveScreen.this);
        int sz = (int) (38 * getResources().getDisplayMetrics().density);
        row.addView(logo, new LinearLayout.LayoutParams(sz, sz));
        TextView t = new TextView(NativeLiveScreen.this);
        StreamItem i = shown.get(n);
        t.setText(i.name == null ? "" : i.name);
        t.setTextColor(android.graphics.Color.WHITE);
        t.setTextSize(14);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp =
            new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = 8;
        LinearLayout textColumn = new LinearLayout(NativeLiveScreen.this);
        textColumn.setOrientation(android.widget.LinearLayout.VERTICAL);
        textColumn.addView(t);
        EpgSchedule schedule = schedules.get(sourceId() + "|" + i.id);
        EpgSchedule.Program program =
            schedule == null ? null : schedule.active(System.currentTimeMillis());
        if (program != null) {
          TextView programText = new TextView(NativeLiveScreen.this);
          programText.setText(program.label());
          programText.setTextColor(android.graphics.Color.rgb(170, 198, 219));
          programText.setTextSize(11);
          programText.setSingleLine(true);
          programText.setEllipsize(android.text.TextUtils.TruncateAt.END);
          textColumn.addView(programText);
          if (program.timed()) {
            ProgressBar progress =
                new ProgressBar(
                    NativeLiveScreen.this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(program.progress(System.currentTimeMillis()));
            textColumn.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));
          }
        }
        row.addView(textColumn, tp);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (i.logo != null && !i.logo.trim().isEmpty()) loadLogo(logo, i.logo);
        else {
          String fallback = logoFallback(i);
          if (fallback != null) loadLogo(logo, fallback);
          else logo.setImageResource(R.drawable.lazytv_official_logo);
        }
        row.setActivated(current != null && current.id.equals(i.id));
        return row;
      }
    };
  }

  private String logoFallback(StreamItem i) {
    if (i == null || i.tvgId == null || i.tvgId.trim().isEmpty()) return null;
    String id = i.tvgId.trim();
    return "https://logo.dvbdata.com/" + java.net.URLEncoder.encode(id) + ".png";
  }

  private void loadLogo(ImageView view, String url) {
    final long epoch = viewGeneration;
    view.setTag(url);
    view.setImageResource(R.drawable.lazytv_official_logo);
    if (imageWorker.isShutdown()) return;
    imageWorker.execute(
        () -> {
          try {
            android.graphics.Bitmap bitmap = ChannelLogos.load(url);
            if (bitmap != null)
              runOnUiThread(
                  () -> {
                    if (epoch == viewGeneration && !isDestroyed() && url.equals(view.getTag()))
                      view.setImageBitmap(bitmap);
                  });
          } catch (Exception unavailable) {
            /* Optional artwork keeps the local placeholder. */
          }
        });
  }

  private void buildControls() {
    FrameLayout frame = fullscreenHost.container();
    fullControls = new LinearLayout(this);
    fullControls.setOrientation(android.widget.LinearLayout.VERTICAL);
    fullControls.setPadding(dp(10), dp(7), dp(10), dp(8));
    fullControls.setBackgroundColor(android.graphics.Color.argb(225, 4, 15, 28));
    fullNow = new TextView(this);
    fullNow.setTextColor(android.graphics.Color.WHITE);
    fullNow.setTextSize(14);
    fullNow.setMaxLines(2);
    fullControls.addView(fullNow);
    fullProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    fullProgress.setMax(100);
    fullControls.addView(fullProgress, new LinearLayout.LayoutParams(-1, dp(4)));
    fullNext = new TextView(this);
    fullNext.setTextColor(android.graphics.Color.rgb(170, 198, 219));
    fullNext.setTextSize(12);
    fullNext.setMaxLines(2);
    fullControls.addView(fullNext);
    LinearLayout actions = new LinearLayout(this);
    actions.setOrientation(android.widget.LinearLayout.HORIZONTAL);
    fullControls.addView(actions);
    Button[] buttons = new Button[9];
    String[] labels = {
      "CH−", "CH+", "☆ Favorite", "EPG", "Lista", "Back", "Play/Pause", "● RECORD", "CC"
    };
    for (int n = 0; n < buttons.length; n++) {
      Button button = new Button(this);
      button.setId(View.generateViewId());
      button.setText(labels[n]);
      button.setAllCaps(false);
      button.setTextColor(android.graphics.Color.WHITE);
      button.setTextSize(isLandscapeLayout() ? 13 : 10);
      button.setBackgroundResource(R.drawable.bg_player_button);
      button.setPadding(dp(3), 0, dp(3), 0);
      button.setFocusable(true);
      LinearLayout.LayoutParams params =
          new LinearLayout.LayoutParams(0, dp(46), n == 2 ? 1.6f : 1);
      params.setMargins(dp(2), dp(4), dp(2), 0);
      actions.addView(button, params);
      buttons[n] = button;
    }
    for (int n = 0; n < buttons.length; n++) {
      buttons[n].setNextFocusLeftId(buttons[(n + buttons.length - 1) % buttons.length].getId());
      buttons[n].setNextFocusRightId(buttons[(n + 1) % buttons.length].getId());
      buttons[n].setNextFocusUpId(fullscreenHost.fullscreenView().getId());
    }
    fullscreenHost.fullscreenView().setNextFocusDownId(buttons[0].getId());
    fullPrevious = buttons[0];
    fullFavorite = buttons[2];
    buttons[0].setOnClickListener(v -> playAdjacent(-1));
    buttons[1].setOnClickListener(v -> playAdjacent(1));
    buttons[2].setOnClickListener(v -> toggleFavorite());
    buttons[3].setOnClickListener(v -> showEpg());
    buttons[4].setOnClickListener(v -> showOverlay());
    buttons[5].setOnClickListener(
        v -> {
          if (!back()) owner.onBackPressed();
        });
    buttons[6].setOnClickListener(
        v -> {
          if (playbackSession == null || !playbackSession.togglePause())
            Toast.makeText(
                    this, "Ky transmetim nuk mbështet pauzën / timeshift.", Toast.LENGTH_SHORT)
                .show();
        });
    pauseButton = buttons[6];
    epgButton = buttons[3];
    ccButton = buttons[8];
    ccButton.setOnClickListener(v -> { if (playbackSession != null) playbackSession.chooseSubtitles(owner); });
    recordButton = buttons[7];
    recordButton.setOnClickListener(
        v -> recorder.toggle(playbackSession == null ? null : playbackSession.recordingStream()));
    FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    frame.addView(fullControls, layout);
    fullControls.setVisibility(View.GONE);
    updateCapabilities();
  }

  private void updateCapabilities() {
    if (pauseButton == null) return;
    pauseButton.setVisibility(playbackSession != null && playbackSession.canSeek() ? View.VISIBLE : View.GONE);
    epgButton.setVisibility(activeSchedule.current != null || activeSchedule.next != null ? View.VISIBLE : View.GONE);
    ccButton.setVisibility(playbackSession != null && playbackSession.hasSubtitles() ? View.VISIBLE : View.GONE);
    recordButton.setVisibility(View.GONE); // Recording is not an advertised LIVE capability until a TS probe succeeds.
  }

  private void toggleFavorite() {
    if (current == null) return;
    boolean favorite = favorites.toggle(sourceId(), current);
    ((Button) findViewById(R.id.live_favorite)).setText(favorite ? "★ Favorite" : "☆ Favorite");
    if (fullFavorite != null) fullFavorite.setText(favorite ? "★ Favorite" : "☆ Favorite");
  }

  private void loadEpg(StreamItem item, long generation) {
    if (item == null || epgLoading) return;
    epgLoading = true;
    lastEpgRequest = System.currentTimeMillis();
    final String provider = sourceId();
    final Playlist selectedPlaylist = p;
    epgWorker.execute(
        () -> {
          EpgSchedule result;
          try {
            result = engine.loadLiveSchedule(selectedPlaylist, item);
          } catch (Exception unavailable) {
            result = EpgSchedule.empty();
          }
          final EpgSchedule schedule = result;
          runOnUiThread(
              () -> {
                if (generation != playbackGeneration
                    || isFinishing()
                    || isDestroyed()
                    || current == null
                    || !item.id.equals(current.id)
                    || !provider.equals(sourceId())) return;
                epgLoading = false;
                activeSchedule = schedule;
                schedules.put(provider + "|" + item.id, schedule);
                renderEpg();
                channelsView.invalidateViews();
              });
        });
  }

  private void renderEpg() {
    updateCapabilities();
    long now = System.currentTimeMillis();
    EpgSchedule.Program program = activeSchedule.active(now), next = activeSchedule.upcoming(now);
    String currentText = program == null ? "EPG nuk disponohet" : "TANI: " + program.label(),
        nextText = next == null ? "" : "MË PAS: " + next.label();
    epg.setText(currentText + (nextText.isEmpty() ? "" : "\n" + nextText));
    if (fullNow != null) {
      fullNow.setText((current == null ? "" : current.name + " • ") + currentText);
      fullNext.setText(nextText);
      fullProgress.setVisibility(program != null && program.timed() ? View.VISIBLE : View.GONE);
      if (program != null) fullProgress.setProgress(program.progress(now));
      if (current != null)
        fullFavorite.setText(favorites.contains(sourceId(), current) ? "★ Favorite" : "☆ Favorite");
    }
  }

  private void showEpg() {
    long now = System.currentTimeMillis();
    EpgSchedule.Program program = activeSchedule.active(now), next = activeSchedule.upcoming(now);
    String text =
        program == null
            ? "EPG nuk disponohet"
            : "TANI\n"
                + program.label()
                + (program.description.isEmpty() ? "" : "\n" + program.description);
    if (next != null)
      text +=
          "\n\nMË PAS\n"
              + next.label()
              + (next.description.isEmpty() ? "" : "\n" + next.description);
    new AlertDialog.Builder(this)
        .setTitle(current == null ? "EPG" : current.name)
        .setMessage(text)
        .setPositiveButton("Mbyll", null)
        .show();
  }

  private boolean isLandscapeLayout() {
    return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  private void setFullscreen(boolean on) {
    displayState.fullscreen(on);
    renderFullscreen(on);
  }

  private void renderFullscreen(boolean on) {
    fullscreen = on;
    fullscreenHost.show(on, playbackSession);
    if (recorder != null) recorder.attachBadge();
    if (fullControls != null)
      fullControls.setVisibility(on && displayState.controls() ? View.VISIBLE : View.GONE);
    if (!on) syncCurrentChannelPosition();
  }

  private int dp(int v) {
    return (int) (v * getResources().getDisplayMetrics().density + .5f);
  }

  private void showOverlay() {
    if (!displayState.openBrowser()) return;
    renderFullscreen(false);
    overlay.setVisibility(View.GONE);
    syncCurrentChannelPosition();
    channelsView.requestFocus();
  }

  private void hideOverlay() {
    overlayOpen = false;
    overlay.setVisibility(View.GONE);
    fullscreenHost.activeView().requestFocus();
  }

  private void toggleControls() {
    mainUi.removeCallbacks(hideControls);
    mainUi.postDelayed(hideControls, 6000);
    if (displayState.controls()) {
      displayState.hideControls();
      fullControls.setVisibility(View.GONE);
      fullscreenHost.activeView().requestFocus();
    } else if (displayState.showControls()) {
      renderEpg();
      fullControls.setVisibility(View.VISIBLE);
      fullControls.bringToFront();
      fullPrevious.requestFocus();
    }
  }

  private void toggleOverlay() {
    if (displayState.closeBrowser()) {
      renderFullscreen(true);
      return;
    }
    if (fullscreen) showOverlay();
  }

  private void playAdjacent(int delta) {
    List<StreamItem> context = shown;
    if (current != null && currentShownIndex() < 0)
      context = liveItems.getOrDefault(current.categoryId, shown);
    StreamItem next =
        LiveChannelNavigator.adjacent(context, current == null ? null : current.id, delta);
    if (next != null) playChannel(next, false);
  }

  private void openCatalogType(String type) {
    module.openCatalog(owner, type);
  }

  boolean key(android.view.KeyEvent event) {
    int code = event.getKeyCode();
    if (event.getAction() == KeyEvent.ACTION_UP) return consumedKeys.remove(code);
    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
    if (consumedKeys.contains(code)) return true;
    boolean handled = keyDown(event);
    if (handled) consumedKeys.add(code);
    return handled;
  }

  private boolean keyDown(android.view.KeyEvent event) {
    if (playerView == null) return false;
    if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
    int key = event.getKeyCode();
    if (fullscreen
        && (key == android.view.KeyEvent.KEYCODE_DPAD_CENTER
            || key == android.view.KeyEvent.KEYCODE_ENTER
            || key == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
        && !displayState.controls()) {
      toggleControls();
      return true;
    }
    if (key == android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
      if (!playbackSession.togglePause())
        Toast.makeText(this, "Ky transmetim nuk mbështet pauzën / timeshift.", Toast.LENGTH_SHORT)
            .show();
      return true;
    }
    if (key == android.view.KeyEvent.KEYCODE_MEDIA_NEXT) {
      playAdjacent(1);
      return true;
    }
    if (key == android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
      playAdjacent(-1);
      return true;
    }
    if (fullscreen
        && !displayState.controls()
        && (key == KeyEvent.KEYCODE_DPAD_LEFT
            || key == KeyEvent.KEYCODE_DPAD_RIGHT
            || key == KeyEvent.KEYCODE_DPAD_UP
            || key == KeyEvent.KEYCODE_DPAD_DOWN)) {
      toggleControls();
      return true;
    }
    if (fullscreen && key == android.view.KeyEvent.KEYCODE_MENU) {
      showOverlay();
      return true;
    }
    if (!fullscreen
        && (key == android.view.KeyEvent.KEYCODE_DPAD_CENTER
            || key == android.view.KeyEvent.KEYCODE_ENTER
            || key == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
        && playerView.hasFocus()
        && current != null) {
      setFullscreen(true);
      return true;
    }
    return false;
  }

  boolean back() {
    if (displayState.closeBrowser()) {
      renderFullscreen(true);
      return true;
    }
    if (displayState.hideControls()) {
      fullControls.setVisibility(View.GONE);
      fullscreenHost.activeView().requestFocus();
      return true;
    }
    if (fullscreen) {
      setFullscreen(false);
      return true;
    }
    return false;
  }

  void configuration(Configuration configuration) {
    if (playerView == null) return;
    String selectedCountry = country == null ? "" : country.key,
        selectedCategory = category == null ? "" : category.id;
    String query = search.getText().toString(), program = epg.getText().toString();
    int channelPosition = channelsView.getFirstVisiblePosition();
    fullscreenHost.close();
    setContentView(module.layout());
    bind();
    if (recorder != null) recorder.attachBadge();
    countrySpinner.setAdapter(spinnerAdapter(countryLabels()));
    int countryIndex = 0;
    for (int n = 0; n < countries.size(); n++)
      if (countries.get(n).key.equals(selectedCountry)) {
        countryIndex = n;
        break;
      }
    countrySpinner.setSelection(countryIndex);
    selectCountry(countryIndex);
    for (int n = 0; n < categories.size(); n++)
      if (categories.get(n).id.equals(selectedCategory)) {
        selectCategory(n, false);
        break;
      }
    search.setText(query);
    channelsView.setSelection(channelPosition);
    if (current != null) {
      title.setText(current.name);
      epg.setText(program);
      idle.setVisibility(View.GONE);
      ((Button) findViewById(R.id.live_favorite))
          .setText(favorites.contains(sourceId(), current) ? "★ Favorite" : "☆ Favorite");
    }
    loading.setVisibility(
        playbackSession != null && playbackSession.isBuffering() ? View.VISIBLE : View.GONE);
    renderFullscreen(displayState.mode() == LiveDisplayState.Mode.FULLSCREEN);
    fullscreenHost.buffering(playbackSession != null && playbackSession.isBuffering());
    if (displayState.mode() == LiveDisplayState.Mode.BROWSER) channelsView.requestFocus();
  }

  void start() {
    if (closed) return;
    boolean restarting = stopped;
    stopped = false;
    if (worker.isShutdown()) worker = Executors.newSingleThreadExecutor();
    if (epgWorker.isShutdown()) epgWorker = Executors.newFixedThreadPool(2);
    if (imageWorker.isShutdown()) imageWorker = Executors.pool(2, 32);
    mainUi.removeCallbacks(epgTick);
    mainUi.post(epgTick);
    if (restarting && catalog == null) load();
    if (restarting && pendingSelection != null) playChannel(pendingSelection, false);
    if (playerView != null) playerView.onResume();
    if (playbackSession != null) playbackSession.resumeFromBackground();
  }

  void result(int request, int result, Intent data) {
    if (recorder != null) recorder.result(request, result, data);
  }

  void pause() {
    if (playbackSession != null) playbackSession.pauseForBackground();
  }

  void stop() {
    if (stopped) return;
    stopped = true;
    consumedKeys.clear();
    catalogGeneration++;
    playbackGeneration++;
    viewGeneration++;
    epgLoading = false;
    mainUi.removeCallbacksAndMessages(null);
    worker.shutdownNow();
    epgWorker.shutdownNow();
    imageWorker.shutdownNow();
    if (recorder != null) recorder.stop();
    if (playbackSession != null) playbackSession.pauseForBackground();
    if (playerView != null) playerView.onPause();
  }

  void close() {
    closed = true;
    stop();
    if (recorder != null) recorder.close();
    android.util.Log.i("LazyTV-Lifecycle", getClass().getSimpleName() + " destroy");
    catalogGeneration++;
    playbackGeneration++;
    viewGeneration++;
    mainUi.removeCallbacksAndMessages(null);
    epgWorker.shutdownNow();
    if (playbackSession != null) playbackSession.release();
    if (fullscreenHost != null) fullscreenHost.close();
    if (liveSource != null) liveSource.close();
    worker.shutdownNow();
    imageWorker.shutdownNow();
  }
}
