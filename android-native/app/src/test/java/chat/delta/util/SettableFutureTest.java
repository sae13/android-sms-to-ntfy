package chat.delta.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;

public class SettableFutureTest {
  @Test
  public void timedGetWaitsForResult() throws Exception {
    SettableFuture<String> future = new SettableFuture<>();
    Thread producer = new Thread(() -> {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      future.set("ready");
    });
    producer.start();

    assertEquals("ready", future.get(2, TimeUnit.SECONDS));
    producer.join();
  }

  @Test
  public void timedGetDoesNotTimeoutImmediately() throws Exception {
    SettableFuture<String> future = new SettableFuture<>();
    long started = System.nanoTime();
    try {
      future.get(150, TimeUnit.MILLISECONDS);
    } catch (TimeoutException expected) {
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      assertTrue("timed get returned too early: " + elapsedMillis, elapsedMillis >= 100);
      return;
    }
    throw new AssertionError("expected TimeoutException");
  }
}
