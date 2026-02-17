package org.powergrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Immutable lobby room. Replaced (not mutated) when state changes.
 */
public record LobbyRoom(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("hostId") String hostId,
        @JsonProperty("playerIds") List<String> playerIds
) {}
