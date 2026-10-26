package de.lazytv.pro.live;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.Looper;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.catalog.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {25, 28, 30}, application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class BackgroundResourceTest {
  private Activity activity;
  private LivePlaybackSession session;
  private Player player;
  private PlayerView view;
  private final StreamItem item =
      new StreamItem(
          "fixture", "Fixture", "genre", "", "http://127.0.0.1:1/fixture.ts", "", CatalogType.LIVE);

  @Before
  public void create() {
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    view = new PlayerView(activity);
    session =
        new LivePlaybackSession(
            activity,
            view,
            null,
            new LivePlaybackSession.Listener() {
              public void onBuffering(boolean value) {}

              public void onReady() {}

              public void onFatalPlaybackError(String message) {}
            });
    player = view.getPlayer();
  }

  @After
  public void close() {
    session.release();
    activity.finish();
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  private ResolvedStream stream(String url) {
    return new ResolvedStream(url, "ts", "Fixture", "", "", null, null);
  }

  private void pump() throws Exception {
    Thread.sleep(10);
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(10, TimeUnit.MILLISECONDS);
  }

  @Test
  public void leavingPlayerCancelsActualNetworkLoad() throws Exception {
    try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
      CountDownLatch opened = new CountDownLatch(1), cancelled = new CountDownLatch(1);
      ExecutorService worker = java.util.concurrent.Executors.newSingleThreadExecutor();
      Future<?> serving =
          worker.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  socket.setSoTimeout(8000);
                  BufferedReader reader =
                      new BufferedReader(new InputStreamReader(socket.getInputStream()));
                  String line;
                  while ((line = reader.readLine()) != null && !line.isEmpty()) {}
                  socket
                      .getOutputStream()
                      .write(
                          "HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: 1048576\r\n\r\n"
                              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                  socket.getOutputStream().flush();
                  opened.countDown();
                  if (reader.read() == -1) cancelled.countDown();
                } catch (IOException failure) {
                  if (opened.getCount() == 0 && !(failure instanceof SocketTimeoutException))
                    cancelled.countDown();
                }
              });
      try {
        session.play(item, stream("http://127.0.0.1:" + server.getLocalPort() + "/fixture.ts"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (opened.getCount() != 0 && System.nanoTime() < deadline) pump();
        assertEquals(0, opened.getCount());
        session.pauseForBackground();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (cancelled.getCount() != 0 && System.nanoTime() < deadline) pump();
        assertEquals(
            "Background player must cancel its loader, not only mute playback",
            0,
            cancelled.getCount());
        assertEquals(Player.STATE_IDLE, player.getPlaybackState());
      } finally {
        session.release();
        serving.cancel(true);
        worker.shutdownNow();
      }
    }
  }

  private boolean screenHeldAwake() {
    return (activity.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
  }

  @Test
  public void bufferingAndFullscreenKeepWindowAwakeUntilBackgroundOrRelease() {
    assertFalse(screenHeldAwake());
    session.play(item, stream(item.streamUrl));
    assertEquals(Player.STATE_BUFFERING, view.getPlayer().getPlaybackState());
    assertTrue("Buffering must not allow the TV to sleep", screenHeldAwake());
    PlayerView fullscreen = new PlayerView(activity);
    session.attach(fullscreen);
    assertTrue("Detaching preview must not clear the window's keep-awake flag", screenHeldAwake());
    session.attach(view);
    assertTrue(screenHeldAwake());
    session.pauseForBackground();
    assertFalse("HOME must restore normal standby", screenHeldAwake());
    session.resumeFromBackground();
    assertTrue(screenHeldAwake());
    session.release();
    assertFalse(screenHeldAwake());
  }

  @Test
  public void lateResolvedChannelCannotPrepareInBackground() {
    session.pauseForBackground();
    session.play(item, stream(item.streamUrl));
    assertNull("Stopped view must not retain decoder/player", view.getPlayer());
    session.resumeFromBackground();
    assertNotSame(player, view.getPlayer());
    assertEquals(Player.STATE_BUFFERING, view.getPlayer().getPlaybackState());
    assertTrue(view.getPlayer().getPlayWhenReady());
  }

  @Test
  public void duplicateChannelCallbackAfterStopCannotDereferenceReleasedPlayer() {
    ResolvedStream resolved = stream(item.streamUrl);
    session.play(item, resolved);
    session.pauseForBackground();
    session.play(item, resolved);
    assertNull(view.getPlayer());
    session.resumeFromBackground();
    assertNotNull(view.getPlayer());
    assertNotSame(player, view.getPlayer());
  }

  @Test
  public void userPausedStateStaysPausedAcrossBackground() {
    session.play(item, stream(item.streamUrl));
    player.pause();
    session.pauseForBackground();
    session.pauseForBackground();
    assertNull(view.getPlayer());
    session.resumeFromBackground();
    assertFalse(view.getPlayer().getPlayWhenReady());
  }

  @Test
  public void backgroundReleasesBothViewsAndResumeCreatesExactlyOnePlayer() {
    session.play(item, stream(item.streamUrl));
    PlayerView fullscreen = new PlayerView(activity);
    session.attach(fullscreen);
    assertNull(view.getPlayer());
    assertSame(player, fullscreen.getPlayer());
    session.pauseForBackground();
    assertNull(view.getPlayer());
    assertNull(fullscreen.getPlayer());
    session.resumeFromBackground();
    Player resumed = fullscreen.getPlayer();
    assertNotNull(resumed);
    assertNotSame(player, resumed);
    session.resumeFromBackground();
    assertSame(resumed, fullscreen.getPlayer());
    session.attach(view);
    assertSame(resumed, view.getPlayer());
    assertNull(fullscreen.getPlayer());
  }
}
