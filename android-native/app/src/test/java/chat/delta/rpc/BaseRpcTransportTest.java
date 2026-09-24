package chat.delta.rpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class BaseRpcTransportTest {
  private static final TypeReference<String> STRING_RESULT = new TypeReference<String>() {};

  @Test
  public void timesOutAndRemovesPendingRequest() throws Exception {
    TestTransport transport = new TestTransport(75);
    long started = System.nanoTime();

    try {
      transport.call("never-responds");
      fail("expected RpcException");
    } catch (RpcException expected) {
      long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
      assertEquals("Delta Chat RPC timed out", expected.getMessage());
      assertTrue("call did not wait for its timeout: " + elapsedMillis, elapsedMillis >= 50);
      assertEquals(0, transport.pendingRequestCount());
    }
  }

  @Test
  public void malformedResponseFailsPendingCallAndNextCallCanSucceed() throws Exception {
    TestTransport transport = new TestTransport(2_000);

    AtomicReference<RpcException> failure = new AtomicReference<>();
    Thread caller = callInThread(transport, "malformed", failure);
    assertTrue(transport.requestSent.await(1, TimeUnit.SECONDS));
    transport.responses.add("not-json");
    caller.join(1_000);

    try {
      if (failure.get() != null) throw failure.get();
      fail("expected RpcException");
    } catch (RpcException expected) {
      assertTrue(expected.getMessage().startsWith("Delta Chat RPC response failed"));
      assertEquals(0, transport.pendingRequestCount());
    }

    transport.requestSent = new CountDownLatch(1);
    AtomicReference<String> result = new AtomicReference<>();
    AtomicReference<RpcException> healthyFailure = new AtomicReference<>();
    Thread healthyCaller = callForResultInThread(transport, "healthy", result, healthyFailure);
    assertTrue(transport.requestSent.await(1, TimeUnit.SECONDS));
    transport.responses.add("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"ready\"}");
    healthyCaller.join(1_000);
    assertTrue(healthyFailure.get() == null);
    assertEquals("ready", result.get());
  }

  @Test
  public void runtimeResponseSourceFailureFailsPendingCallAndRestartsWorker() throws Exception {
    TestTransport transport = new TestTransport(2_000);
    transport.blockAfterSourceFailure = true;

    AtomicReference<RpcException> failure = new AtomicReference<>();
    Thread caller = callInThread(transport, "broken-source", failure);
    assertTrue(transport.requestSent.await(1, TimeUnit.SECONDS));
    transport.responses.add(new IllegalStateException("source closed"));
    caller.join(1_000);

    assertTrue(failure.get().getMessage().startsWith("Delta Chat RPC response failed"));
    assertEquals(0, transport.pendingRequestCount());

    Thread failedWorker = transport.failedWorker;
    assertTrue("failed worker was not captured", failedWorker != null);
    failedWorker.join(1_000);
    assertFalse("failed worker is still alive", failedWorker.isAlive());

    transport.blockAfterSourceFailure = false;
    transport.responses.add("{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"restarted\"}");
    assertEquals("restarted", transport.callForResult(STRING_RESULT, "after-restart"));
    assertNotSame(failedWorker, transport.workerThread());
  }

  @Test
  public void interruptionPreservesStatusAndRemovesPendingRequest() throws Exception {
    TestTransport transport = new TestTransport(5_000);
    CountDownLatch finished = new CountDownLatch(1);
    AtomicReference<RpcException> failure = new AtomicReference<>();
    AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
    Thread caller = new Thread(() -> {
      try {
        transport.call("interrupt-me");
      } catch (RpcException e) {
        failure.set(e);
        interrupted.set(Thread.currentThread().isInterrupted());
      } finally {
        finished.countDown();
      }
    });
    caller.start();
    assertTrue(transport.requestSent.await(1, TimeUnit.SECONDS));

    caller.interrupt();

    assertTrue(finished.await(1, TimeUnit.SECONDS));
    assertEquals("Delta Chat RPC interrupted", failure.get().getMessage());
    assertTrue(interrupted.get());
    assertEquals(0, transport.pendingRequestCount());
  }

  private static Thread callInThread(
      TestTransport transport, String method, AtomicReference<RpcException> failure) {
    Thread caller = new Thread(() -> {
      try {
        transport.call(method);
      } catch (RpcException e) {
        failure.set(e);
      }
    });
    caller.start();
    return caller;
  }

  private static Thread callForResultInThread(
      TestTransport transport,
      String method,
      AtomicReference<String> result,
      AtomicReference<RpcException> failure) {
    Thread caller = new Thread(() -> {
      try {
        result.set(transport.callForResult(STRING_RESULT, method));
      } catch (RpcException e) {
        failure.set(e);
      }
    });
    caller.start();
    return caller;
  }

  private static final class TestTransport extends BaseRpcTransport {
    private final BlockingQueue<Object> responses = new LinkedBlockingQueue<>();
    private volatile CountDownLatch requestSent = new CountDownLatch(1);
    private volatile boolean blockAfterSourceFailure;
    private volatile Thread failedWorker;

    TestTransport(long timeoutMillis) {
      super(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void sendRequest(String jsonRequest) {
      requestSent.countDown();
    }

    @Override
    protected String getResponse() {
      try {
        Object response = responses.take();
        if (response instanceof RuntimeException) {
          if (blockAfterSourceFailure) failedWorker = Thread.currentThread();
          throw (RuntimeException) response;
        }
        return (String) response;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("response source interrupted", e);
      }
    }
  }
}
