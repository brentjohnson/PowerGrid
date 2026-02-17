package org.powergrid.actor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.powergrid.model.LobbyRoom;
import org.powergrid.model.Player;
import org.powergrid.protocol.MessageType;
import org.powergrid.protocol.OutboundMessage;
import org.powergrid.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Root guardian actor. Manages connected players and game rooms in the lobby.
 */
public class LobbyActor extends AbstractBehavior<LobbyActor.Command> {

    private static final Logger log = LoggerFactory.getLogger(LobbyActor.class);

    // ─── Command protocol ────────────────────────────────────────────────────

    public sealed interface Command permits
            PlayerConnected,
            PlayerDisconnected,
            CreateRoom,
            JoinRoom,
            LeaveRoom,
            ListRooms,
            StartGame {
    }

    public record PlayerConnected(
            String playerId,
            String playerName,
            ActorRef<PlayerConnectionActor.Command> connection
    ) implements Command {}

    public record PlayerDisconnected(String playerId) implements Command {}

    public record CreateRoom(
            String playerId,
            String roomName
    ) implements Command {}

    public record JoinRoom(
            String playerId,
            String roomId
    ) implements Command {}

    public record LeaveRoom(String playerId) implements Command {}

    public record ListRooms(String playerId) implements Command {}

    public record StartGame(String playerId) implements Command {}

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static Behavior<Command> create() {
        return Behaviors.setup(LobbyActor::new);
    }

    // ─── State ───────────────────────────────────────────────────────────────

    private final Map<String, Player> players = new HashMap<>();
    private final Map<String, ActorRef<PlayerConnectionActor.Command>> connections = new HashMap<>();
    private final Map<String, LobbyRoom> rooms = new HashMap<>();
    private final Map<String, String> playerRooms = new HashMap<>(); // playerId → roomId

    // ─── Constructor ─────────────────────────────────────────────────────────

    private LobbyActor(ActorContext<Command> context) {
        super(context);
    }

    // ─── Message dispatch ────────────────────────────────────────────────────

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlayerConnected.class, this::onPlayerConnected)
                .onMessage(PlayerDisconnected.class, this::onPlayerDisconnected)
                .onMessage(CreateRoom.class, this::onCreateRoom)
                .onMessage(JoinRoom.class, this::onJoinRoom)
                .onMessage(LeaveRoom.class, this::onLeaveRoom)
                .onMessage(ListRooms.class, this::onListRooms)
                .onMessage(StartGame.class, this::onStartGame)
                .build();
    }

    // ─── Handlers ────────────────────────────────────────────────────────────

    private Behavior<Command> onPlayerConnected(PlayerConnected cmd) {
        log.info("Player connected: {} ({})", cmd.playerName(), cmd.playerId());
        players.put(cmd.playerId(), new Player(cmd.playerId(), cmd.playerName()));
        connections.put(cmd.playerId(), cmd.connection());

        var payload = JsonMapper.getInstance().createObjectNode()
                .put("playerId", cmd.playerId());
        send(cmd.playerId(), MessageType.WELCOME, payload);
        return Behaviors.same();
    }

    private Behavior<Command> onPlayerDisconnected(PlayerDisconnected cmd) {
        log.info("Player disconnected: {}", cmd.playerId());
        leaveCurrentRoom(cmd.playerId());
        players.remove(cmd.playerId());
        connections.remove(cmd.playerId());
        return Behaviors.same();
    }

    private Behavior<Command> onCreateRoom(CreateRoom cmd) {
        String roomId = UUID.randomUUID().toString();
        String playerId = cmd.playerId();
        Player player = players.get(playerId);
        if (player == null) {
            sendError(playerId, "NOT_CONNECTED", "Player not registered.");
            return Behaviors.same();
        }

        leaveCurrentRoom(playerId);

        LobbyRoom room = new LobbyRoom(roomId, cmd.roomName(), playerId, new ArrayList<>(List.of(playerId)));
        rooms.put(roomId, room);
        playerRooms.put(playerId, roomId);

        log.info("Room created: {} by {}", roomId, playerId);

        var payload = JsonMapper.getInstance().createObjectNode()
                .put("roomId", roomId)
                .put("roomName", room.name());
        send(playerId, MessageType.ROOM_JOINED, payload);
        return Behaviors.same();
    }

    private Behavior<Command> onJoinRoom(JoinRoom cmd) {
        String playerId = cmd.playerId();
        LobbyRoom room = rooms.get(cmd.roomId());
        if (room == null) {
            sendError(playerId, "ROOM_NOT_FOUND", "Room does not exist.");
            return Behaviors.same();
        }
        if (room.playerIds().size() >= 6) {
            sendError(playerId, "ROOM_FULL", "Room is full.");
            return Behaviors.same();
        }

        leaveCurrentRoom(playerId);

        List<String> updated = new ArrayList<>(room.playerIds());
        updated.add(playerId);
        LobbyRoom updatedRoom = new LobbyRoom(room.id(), room.name(), room.hostId(), updated);
        rooms.put(room.id(), updatedRoom);
        playerRooms.put(playerId, room.id());

        log.info("Player {} joined room {}", playerId, room.id());

        var joinPayload = JsonMapper.getInstance().createObjectNode()
                .put("roomId", room.id())
                .put("roomName", room.name());
        send(playerId, MessageType.ROOM_JOINED, joinPayload);

        broadcastRoomUpdate(updatedRoom);
        return Behaviors.same();
    }

    private Behavior<Command> onLeaveRoom(LeaveRoom cmd) {
        leaveCurrentRoom(cmd.playerId());
        return Behaviors.same();
    }

    private Behavior<Command> onListRooms(ListRooms cmd) {
        var array = JsonMapper.getInstance().createArrayNode();
        for (LobbyRoom r : rooms.values()) {
            array.addObject()
                    .put("roomId", r.id())
                    .put("roomName", r.name())
                    .put("playerCount", r.playerIds().size())
                    .put("hostId", r.hostId());
        }
        var payload = JsonMapper.getInstance().createObjectNode().set("rooms", array);
        send(cmd.playerId(), MessageType.ROOM_LIST, payload);
        return Behaviors.same();
    }

    private Behavior<Command> onStartGame(StartGame cmd) {
        String playerId = cmd.playerId();
        String roomId = playerRooms.get(playerId);
        if (roomId == null) {
            sendError(playerId, "NOT_IN_ROOM", "You are not in a room.");
            return Behaviors.same();
        }
        LobbyRoom room = rooms.get(roomId);
        if (room == null || !room.hostId().equals(playerId)) {
            sendError(playerId, "NOT_HOST", "Only the host can start the game.");
            return Behaviors.same();
        }
        if (room.playerIds().size() < 2) {
            sendError(playerId, "NOT_ENOUGH_PLAYERS", "Need at least 2 players to start.");
            return Behaviors.same();
        }

        log.info("Starting game in room {}", roomId);

        // Spawn game session actor
        ActorRef<GameSessionActor.Command> session = getContext().spawn(
                GameSessionActor.create(roomId, room.playerIds(), getContext().getSelf()),
                "room-" + roomId
        );

        // Notify all players
        var payload = JsonMapper.getInstance().createObjectNode()
                .put("roomId", roomId);
        for (String pid : room.playerIds()) {
            send(pid, MessageType.GAME_STARTING, payload);
        }

        // Remove room from lobby (game is now active)
        rooms.remove(roomId);
        for (String pid : room.playerIds()) {
            playerRooms.remove(pid);
        }

        return Behaviors.same();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void leaveCurrentRoom(String playerId) {
        String roomId = playerRooms.remove(playerId);
        if (roomId == null) return;

        LobbyRoom room = rooms.get(roomId);
        if (room == null) return;

        List<String> remaining = new ArrayList<>(room.playerIds());
        remaining.remove(playerId);

        if (remaining.isEmpty()) {
            rooms.remove(roomId);
            log.info("Room {} removed (empty)", roomId);
        } else {
            String newHost = room.hostId().equals(playerId) ? remaining.get(0) : room.hostId();
            LobbyRoom updated = new LobbyRoom(roomId, room.name(), newHost, remaining);
            rooms.put(roomId, updated);
            broadcastRoomUpdate(updated);
        }
    }

    private void broadcastRoomUpdate(LobbyRoom room) {
        var array = JsonMapper.getInstance().createArrayNode();
        for (String pid : room.playerIds()) {
            Player p = players.get(pid);
            if (p != null) {
                array.addObject()
                        .put("playerId", pid)
                        .put("playerName", p.name());
            }
        }
        var payload = JsonMapper.getInstance().createObjectNode()
                .put("roomId", room.id())
                .put("roomName", room.name())
                .put("hostId", room.hostId())
                .set("players", array);

        for (String pid : room.playerIds()) {
            send(pid, MessageType.ROOM_UPDATED, payload);
        }
    }

    private void send(String playerId, MessageType type, com.fasterxml.jackson.databind.JsonNode payload) {
        ActorRef<PlayerConnectionActor.Command> conn = connections.get(playerId);
        if (conn == null) return;
        try {
            OutboundMessage msg = new OutboundMessage(type, payload);
            String json = JsonMapper.getInstance().writeValueAsString(msg);
            conn.tell(new PlayerConnectionActor.SendText(json));
        } catch (Exception e) {
            log.error("Failed to serialize message {} for player {}", type, playerId, e);
        }
    }

    private void sendError(String playerId, String code, String message) {
        var payload = JsonMapper.getInstance().createObjectNode()
                .put("code", code)
                .put("message", message);
        send(playerId, MessageType.ERROR, payload);
    }
}
