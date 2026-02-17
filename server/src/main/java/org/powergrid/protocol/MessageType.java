package org.powergrid.protocol;

/**
 * All valid message types for the PowerGrid WebSocket protocol.
 * Every inbound and outbound message uses one of these constants as its "type" discriminator.
 *
 * When adding a new type, follow the protocol change checklist in CLAUDE.md.
 */
public enum MessageType {

    // ─── Client → Server ────────────────────────────────────────────────────

    /** Initial handshake. Payload: { "playerName": "..." } */
    HELLO,

    /** Request the current lobby room list. No payload. */
    LIST_ROOMS,

    /** Create a new game room. Payload: { "roomName": "..." } */
    CREATE_ROOM,

    /** Join an existing room. Payload: { "roomId": "..." } */
    JOIN_ROOM,

    /** Leave the current room. No payload. */
    LEAVE_ROOM,

    /** Host starts the game. No payload. */
    START_GAME,

    /** Place a bid on a power plant. Payload: { "plantId": int, "amount": int } */
    BID_PLANT,

    /** Pass during an auction round. No payload. */
    PASS_BID,

    /** Purchase resources from the market. Payload: { "resource": "...", "amount": int } */
    BUY_RESOURCE,

    /** Build a power plant in a city. Payload: { "cityId": "..." } */
    BUILD_CITY,

    /** End the current player's turn. No payload. */
    END_TURN,

    /** Keep-alive ping. No payload. */
    PING,

    // ─── Server → Client ────────────────────────────────────────────────────

    /** Assigns a player UUID after HELLO. Payload: { "playerId": "..." } */
    WELCOME,

    /** Error response. Payload: { "code": "...", "message": "..." } */
    ERROR,

    /** Current list of lobby rooms. Payload: { "rooms": [ ... ] } */
    ROOM_LIST,

    /** Confirmation that the player joined a room. Payload: { "roomId": "...", "roomName": "..." } */
    ROOM_JOINED,

    /** Room state changed (player joined/left). Payload: { "roomId": "...", "players": [ ... ] } */
    ROOM_UPDATED,

    /** Game is about to begin. Payload: { "roomId": "..." } */
    GAME_STARTING,

    /** Full game state snapshot after every action. Payload: GameState JSON */
    GAME_STATE_UPDATE,

    /** Indicates whose turn it is. Payload: { "playerId": "..." } */
    PLAYER_TURN,

    /** A power plant auction has begun. Payload: { "plantId": int, "minimumBid": int } */
    AUCTION_STARTED,

    /** A bid was placed. Payload: { "playerId": "...", "plantId": int, "amount": int } */
    BID_PLACED,

    /** A power plant was sold. Payload: { "playerId": "...", "plantId": int, "amount": int } */
    PLANT_SOLD,

    /** Game ended. Payload: { "winnerId": "...", "reason": "..." } */
    GAME_OVER,

    /** Keep-alive response. No payload. */
    PONG
}
