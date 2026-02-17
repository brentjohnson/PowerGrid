package org.powergrid.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.powergrid.util.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class MessageSerializationTest {

    private final ObjectMapper mapper = JsonMapper.getInstance();

    @Test
    void inboundMessageDeserializesHello() throws Exception {
        String json = """
                {"type":"HELLO","payload":{"playerName":"Alice"}}
                """;

        InboundMessage msg = mapper.readValue(json, InboundMessage.class);

        assertEquals(MessageType.HELLO, msg.type());
        assertNotNull(msg.payload());
        assertEquals("Alice", msg.payload().get("playerName").asText());
    }

    @Test
    void inboundMessageDeserializesJoinRoom() throws Exception {
        String json = """
                {"type":"JOIN_ROOM","payload":{"roomId":"abc-123"}}
                """;

        InboundMessage msg = mapper.readValue(json, InboundMessage.class);

        assertEquals(MessageType.JOIN_ROOM, msg.type());
        assertEquals("abc-123", msg.payload().get("roomId").asText());
    }

    @Test
    void inboundMessageWithNullPayload() throws Exception {
        String json = """
                {"type":"LIST_ROOMS"}
                """;

        InboundMessage msg = mapper.readValue(json, InboundMessage.class);

        assertEquals(MessageType.LIST_ROOMS, msg.type());
        assertNull(msg.payload());
    }

    @Test
    void outboundMessageSerializesWelcome() throws Exception {
        JsonNode payload = mapper.createObjectNode().put("playerId", "uuid-001");
        OutboundMessage msg = new OutboundMessage(MessageType.WELCOME, payload);

        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"WELCOME\""), "Expected type in: " + json);
        assertTrue(json.contains("\"playerId\":\"uuid-001\""), "Expected playerId in: " + json);
    }

    @Test
    void outboundMessageSerializesError() throws Exception {
        JsonNode payload = mapper.createObjectNode()
                .put("code", "NOT_IN_ROOM")
                .put("message", "You are not in a room.");
        OutboundMessage msg = new OutboundMessage(MessageType.ERROR, payload);

        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"ERROR\""), "Expected type in: " + json);
        assertTrue(json.contains("NOT_IN_ROOM"), "Expected code in: " + json);
    }

    @Test
    void outboundRoundTrip() throws Exception {
        JsonNode payload = mapper.createObjectNode().put("roomId", "room-xyz");
        OutboundMessage original = new OutboundMessage(MessageType.GAME_STARTING, payload);

        String json = mapper.writeValueAsString(original);
        // Re-parse as generic JsonNode to verify structure
        JsonNode root = mapper.readTree(json);

        assertEquals("GAME_STARTING", root.get("type").asText());
        assertEquals("room-xyz", root.get("payload").get("roomId").asText());
    }

    @Test
    void allMessageTypesAreDeserializable() throws Exception {
        // Verify that every MessageType enum constant can be parsed from JSON
        for (MessageType type : MessageType.values()) {
            String json = String.format("{\"type\":\"%s\"}", type.name());
            InboundMessage msg = mapper.readValue(json, InboundMessage.class);
            assertEquals(type, msg.type(), "Failed to deserialize type: " + type);
        }
    }

    @Test
    void pingPongSerializes() throws Exception {
        String pingJson = """
                {"type":"PING","payload":{}}
                """;
        InboundMessage ping = mapper.readValue(pingJson, InboundMessage.class);
        assertEquals(MessageType.PING, ping.type());

        JsonNode emptyPayload = mapper.createObjectNode();
        OutboundMessage pong = new OutboundMessage(MessageType.PONG, emptyPayload);
        String pongJson = mapper.writeValueAsString(pong);
        assertTrue(pongJson.contains("\"type\":\"PONG\""), "Expected PONG in: " + pongJson);
    }
}
