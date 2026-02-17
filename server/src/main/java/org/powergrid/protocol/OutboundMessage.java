package org.powergrid.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope for all messages sent to a WebSocket client.
 * Wire format: {@code { "type": "MESSAGE_TYPE", "payload": { ... } }}
 */
public record OutboundMessage(
        @JsonProperty("type") MessageType type,
        @JsonProperty("payload") JsonNode payload
) {}
