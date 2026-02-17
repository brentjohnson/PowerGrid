package org.powergrid.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of the full game state, broadcast to all players after every action.
 */
public record GameState(
        @JsonProperty("roomId") String roomId,
        @JsonProperty("round") int round,
        @JsonProperty("phase") String phase,
        @JsonProperty("currentPlayerId") String currentPlayerId,
        @JsonProperty("players") List<Player> players
) {

    public static GameState initial(String roomId, List<String> playerIds) {
        List<Player> players = new ArrayList<>();
        for (String id : playerIds) {
            players.add(new Player(id, "Player-" + id.substring(0, 6)));
        }
        String first = playerIds.isEmpty() ? null : playerIds.get(0);
        return new GameState(roomId, 1, "AUCTION", first, players);
    }
}
