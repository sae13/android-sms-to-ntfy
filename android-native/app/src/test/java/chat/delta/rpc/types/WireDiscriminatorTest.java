package chat.delta.rpc.types;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

public class WireDiscriminatorTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void deserializesAndSerializesPascalCaseDiscriminators() throws Exception {
    Account account = mapper.readValue("{\"kind\":\"Configured\",\"id\":7}", Account.class);
    assertTrue(account instanceof Account.Configured);
    assertEquals("Configured", kindOf(account));

    ChatListItemFetchResult chatListItem = mapper.readValue(
        "{\"kind\":\"ArchiveLink\",\"freshMessageCounter\":3}",
        ChatListItemFetchResult.class);
    assertTrue(chatListItem instanceof ChatListItemFetchResult.ArchiveLink);
    assertEquals("ArchiveLink", kindOf(chatListItem));

    EventType event = mapper.readValue("{\"kind\":\"Info\",\"msg\":\"ready\"}", EventType.class);
    assertTrue(event instanceof EventType.Info);
    assertEquals("Info", kindOf(event));

    MessageQuote quote = mapper.readValue(
        "{\"kind\":\"JustText\",\"text\":\"hello\"}", MessageQuote.class);
    assertTrue(quote instanceof MessageQuote.JustText);
    assertEquals("JustText", kindOf(quote));

    MuteDuration muteDuration = mapper.readValue("{\"kind\":\"NotMuted\"}", MuteDuration.class);
    assertTrue(muteDuration instanceof MuteDuration.NotMuted);
    assertEquals("NotMuted", kindOf(muteDuration));
  }

  @Test
  public void deserializesAndSerializesCamelCaseDiscriminators() throws Exception {
    Qr qr = mapper.readValue(
        "{\"kind\":\"askVerifyContact\",\"contact_id\":9,\"fingerprint\":\"fpr\",\"authcode\":\"auth\"}",
        Qr.class);
    assertTrue(qr instanceof Qr.AskVerifyContact);
    assertEquals("askVerifyContact", kindOf(qr));

    MessageListItem listItem = mapper.readValue(
        "{\"kind\":\"dayMarker\",\"timestamp\":123}", MessageListItem.class);
    assertTrue(listItem instanceof MessageListItem.DayMarker);
    assertEquals("dayMarker", kindOf(listItem));

    MessageLoadResult loadResult = mapper.readValue(
        "{\"kind\":\"loadingError\",\"error\":\"missing\"}", MessageLoadResult.class);
    assertTrue(loadResult instanceof MessageLoadResult.LoadingError);
    assertEquals("loadingError", kindOf(loadResult));
  }

  private String kindOf(Object value) throws Exception {
    JsonNode json = mapper.readTree(mapper.writeValueAsString(value));
    return json.get("kind").asText();
  }
}
