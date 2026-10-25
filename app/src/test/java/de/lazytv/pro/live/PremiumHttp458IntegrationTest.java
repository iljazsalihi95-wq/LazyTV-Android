package de.lazytv.pro.live;

import static org.junit.Assert.*;

import android.app.Activity;
import android.os.Looper;
import androidx.media3.ui.PlayerView;
import de.lazytv.pro.catalog.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

/** Real Media3 HTTP load -> 458 -> fresh portal session and create_link. No real credentials. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = android.app.Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class PremiumHttp458IntegrationTest {
  @Test
  public void premiumDoesNotReplayRefusedUrl() throws Exception {
    recover(false);
  }

  @Test
  public void managedServerTenUsesItsOwnFreshSession() throws Exception {
    recover(true);
  }

  private void recover(boolean managed) throws Exception {
    try (Portal portal = new Portal()) {
      Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
      ServerTenPlaybackResolver.OwnerSession owner = new ServerTenPlaybackResolver.OwnerSession();
      LiveSources.Source source =
          managed
              ? new LiveSources.Managed(activity, "SERVER_10")
              : new LiveSources.Source() {
                public Catalog load() {
                  return new Catalog();
                }

                public ResolvedStream resolve(StreamItem item, boolean refresh) throws Exception {
                  return owner.resolve(item, refresh);
                }
              };
      StreamItem item =
          new StreamItem(
              "fixture-channel",
              "Fixture",
              "genre",
              "",
              portal.base + "/play?mac=00%3A11%3A22%3A33%3A44%3A55&stream=7&play_token=expired",
              "",
              CatalogType.LIVE);
      AtomicInteger refreshes = new AtomicInteger(), fatal = new AtomicInteger();
      PlayerView view = new PlayerView(activity);
      LivePlaybackSession session =
          new LivePlaybackSession(
              activity,
              view,
              channel -> {
                refreshes.incrementAndGet();
                return source.resolve(channel, true);
              },
              new LivePlaybackSession.Listener() {
                public void onBuffering(boolean value) {}

                public void onReady() {}

                public void onFatalPlaybackError(String message) {
                  fatal.incrementAndGet();
                }
              });
      try {
        session.freshLinkRefusals(true);
        Object player = view.getPlayer();
        ResolvedStream first = source.resolve(item, false);
        assertFalse(first.url.contains("expired"));
        session.play(item, first);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (portal.freshHits.get() == 0 && fatal.get() == 0 && System.nanoTime() < deadline) {
          Thread.sleep(15);
          Shadows.shadowOf(Looper.getMainLooper()).idleFor(20, TimeUnit.MILLISECONDS);
        }
        assertEquals("Media3 must never retry the rejected request", 1, portal.oldHits.get());
        assertEquals(1, portal.freshHits.get());
        assertEquals(2, portal.handshakes.get());
        assertEquals(2, portal.links.get());
        assertEquals(1, refreshes.get());
        assertEquals(0, fatal.get());
        assertTrue(portal.freshCookie.contains("session=fixture2"));
        assertSame(player, view.getPlayer());
      } finally {
        session.release();
        source.close();
        owner.clear();
        activity.finish();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
      }
    }
  }

  private static final class Portal implements AutoCloseable {
    final ServerSocket listener = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
    final String base = "http://127.0.0.1:" + listener.getLocalPort();
    final AtomicInteger oldHits = new AtomicInteger(),
        freshHits = new AtomicInteger(),
        handshakes = new AtomicInteger(),
        links = new AtomicInteger();
    final ExecutorService workers = java.util.concurrent.Executors.newCachedThreadPool();
    final CountDownLatch stop = new CountDownLatch(1);
    final List<Socket> clients = Collections.synchronizedList(new ArrayList<>());
    volatile String freshCookie = "";

    Portal() throws IOException {
      workers.execute(
          () -> {
            while (!listener.isClosed()) {
              try {
                Socket client = listener.accept();
                clients.add(client);
                workers.execute(() -> serve(client));
              } catch (IOException failure) {
                if (!listener.isClosed()) throw new UncheckedIOException(failure);
              }
            }
          });
    }

    private void serve(Socket client) {
      try (Socket socket = client) {
        BufferedReader input =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String request = input.readLine(), cookie = "", line;
        while ((line = input.readLine()) != null && !line.isEmpty())
          if (line.toLowerCase(Locale.ROOT).startsWith("cookie:"))
            cookie = line.substring(7).trim();
        if (request == null) return;
        if (request.contains("/stream1")) {
          oldHits.incrementAndGet();
          reply(socket, 458, "", "");
          return;
        }
        if (request.contains("/stream2")) {
          freshCookie = cookie;
          freshHits.incrementAndGet();
          // Keep a valid fresh response open; this test checks transport/session, not decoding.
          socket
              .getOutputStream()
              .write(
                  ("HTTP/1.1 200 OK\r\nContent-Type: video/mp2t\r\nContent-Length: 1048576\r\n\r\n")
                      .getBytes(StandardCharsets.UTF_8));
          socket.getOutputStream().flush();
          stop.await();
          return;
        }
        if (request.contains("action=handshake")) {
          int count = handshakes.incrementAndGet();
          reply(
              socket,
              200,
              "{\"js\":{\"token\":\"fixture" + count + "\"}}",
              "Set-Cookie: session=fixture" + count + "; Path=/\r\n");
          return;
        }
        if (request.contains("action=create_link")) {
          int count = links.incrementAndGet();
          reply(
              socket,
              200,
              "{\"js\":{\"cmd\":\"ffmpeg "
                  + base
                  + "/stream"
                  + count
                  + ".ts?play_token=fresh"
                  + count
                  + "\"}}",
              "");
          return;
        }
        if (request.contains("action=get_profile")) reply(socket, 200, "{\"js\":{\"id\":1}}", "");
        else if (request.contains("action=get_main_info"))
          reply(socket, 200, "{\"js\":{\"status\":\"active\"}}", "");
        else reply(socket, 200, "{}", "");
      } catch (IOException failure) {
        if (stop.getCount() != 0) throw new UncheckedIOException(failure);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
    }

    private void reply(Socket socket, int status, String body, String headers) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      OutputStream output = socket.getOutputStream();
      output.write(
          ("HTTP/1.1 "
                  + status
                  + " Fixture\r\nConnection: close\r\nContent-Type: application/json\r\n"
                  + headers
                  + "Content-Length: "
                  + bytes.length
                  + "\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8));
      output.write(bytes);
      output.flush();
    }

    public void close() throws IOException {
      stop.countDown();
      listener.close();
      synchronized (clients) {
        for (Socket socket : clients) socket.close();
      }
      workers.shutdownNow();
    }
  }
}
