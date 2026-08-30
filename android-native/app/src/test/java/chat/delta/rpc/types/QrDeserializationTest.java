package chat.delta.rpc.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class QrDeserializationTest {
  @Test
  public void parsesLowercaseLoginKindFromCore() throws Exception {
    Qr qr = new ObjectMapper().readValue(
        "{\"kind\":\"login\",\"address\":\"user@example.invalid\"}", Qr.class);

    assertTrue(qr instanceof Qr.Login);
    assertEquals("user@example.invalid", ((Qr.Login) qr).address);
  }
}
