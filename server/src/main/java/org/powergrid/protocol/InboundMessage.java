package org.powergrid.protocol;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Envelope for all messages received from a WebSocket client.
 * Wire format: {@code { "type": "MESSAGE_TYPE", "payload": { ... } }}
 */
public record InboundMessage(
        @JsonProperty("type") MessageType type,
        @JsonProperty("payload") JsonNode payload
) {}
