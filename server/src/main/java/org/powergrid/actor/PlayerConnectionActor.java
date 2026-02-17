package org.powergrid.actor;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.model.ws.TextMessage;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.powergrid.protocol.InboundMessage;
import org.powergrid.protocol.MessageType;
import org.powergrid.protocol.OutboundMessage;
import org.powergrid.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges a single WebSocket connection into the actor hierarchy.
 *
 * Lifecycle (managed by ServerApp):
 * 1. ServerApp pre-materializes a {@code SourceQueueWithComplete<Message>} for outbound messages.
 * 2. ServerApp spawns a {@code PlayerConnectionActor} with that queue.
 * 3. ServerApp wires inbound WS text → {@code IncomingText} commands → this actor.
 * 4. On WS termination, ServerApp sends {@code ConnectionClosed}.
 * 5. Outbound responses are sent via {@link #send} which offers to the queue.
 */
public class PlayerConnectionActor extends AbstractBehavior<PlayerConnectionActor.Command> {

    private static final Logger log = LoggerFactory.getLogger(PlayerConnectionActor.class);

    // ─── Command protocol ────────────────────────────────────────────────────

    public sealed interface Command permits
            IncomingText,
            SendText,
            ConnectionClosed {
    }

    public record IncomingText(String json) implements Command {}
    public record SendText(String json) implements Command {}
    public record ConnectionClosed() implements Command {}

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static Behavior<Command> create(
            String playerId,
            ActorSystem<LobbyActor.Command> system,
            SourceQueueWithComplete<Message> outQueue
    ) {
        return Behaviors.setup(ctx -> new PlayerConnectionActor(ctx, playerId, system, outQueue));
    }

    // ─── State ───────────────────────────────────────────────────────────────

    private final String playerId;
    private final ActorSystem<LobbyActor.Command> system;
    private final SourceQueueWithComplete<Message> outQueue;
    private boolean registered = false;

    // ─── Constructor ─────────────────────────────────────────────────────────

    private PlayerConnectionActor(
            ActorContext<Command> context,
            String playerId,
            ActorSystem<LobbyActor.Command> system,
            SourceQueueWithComplete<Message> outQueue
    ) {
        super(context);
        this.playerId = playerId;
        this.system = system;
        this.outQueue = outQueue;
        log.info("PlayerConnectionActor created for {}", playerId);
    }

    // ─── Message dispatch ────────────────────────────────────────────────────

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(IncomingText.class, this::onIncomingText)
                .onMessage(SendText.class, this::onSendText)
                .onMessage(ConnectionClosed.class, this::onConnectionClosed)
                .build();
    }

    // ─── Handlers ────────────────────────────────────────────────────────────

    private Behavior<Command> onIncomingText(IncomingText cmd) {
        try {
            InboundMessage msg = JsonMapper.getInstance().readValue(cmd.json(), InboundMessage.class);
            dispatch(msg);
        } catch (Exception e) {
            log.warn("Failed to parse message from {}: {}", playerId, cmd.json(), e);
        }
        return Behaviors.same();
    }

    private Behavior<Command> onSendText(SendText cmd) {
        outQueue.offer(TextMessage.create(cmd.json()));
        return Behaviors.same();
    }

    private Behavior<Command> onConnectionClosed(ConnectionClosed cmd) {
        log.info("Connection closed for player {}", playerId);
        if (registered) {
            system.tell(new LobbyActor.PlayerDisconnected(playerId));
        }
        outQueue.complete();
        return Behaviors.stopped();
    }

    // ─── Dispatch ────────────────────────────────────────────────────────────

    private void dispatch(InboundMessage msg) {
        JsonNode payload = msg.payload();
        switch (msg.type()) {
            case HELLO -> {
                String name = payload != null && payload.has("playerName")
                        ? payload.get("playerName").asText("Unknown")
                        : "Unknown";
                system.tell(new LobbyActor.PlayerConnected(playerId, name, getContext().getSelf()));
                registered = true;
            }
            case LIST_ROOMS -> system.tell(new LobbyActor.ListRooms(playerId));
            case CREATE_ROOM -> {
                String roomName = payload != null && payload.has("roomName")
                        ? payload.get("roomName").asText("New Room")
                        : "New Room";
                system.tell(new LobbyActor.CreateRoom(playerId, roomName));
            }
            case JOIN_ROOM -> {
                String roomId = payload != null && payload.has("roomId")
                        ? payload.get("roomId").asText()
                        : null;
                if (roomId != null) {
                    system.tell(new LobbyActor.JoinRoom(playerId, roomId));
                }
            }
            case LEAVE_ROOM -> system.tell(new LobbyActor.LeaveRoom(playerId));
            case START_GAME -> system.tell(new LobbyActor.StartGame(playerId));
            case PING -> {
                try {
                    var pongPayload = JsonMapper.getInstance().createObjectNode();
                    var pong = new OutboundMessage(MessageType.PONG, pongPayload);
                    getContext().getSelf().tell(new SendText(JsonMapper.getInstance().writeValueAsString(pong)));
                } catch (Exception e) {
                    log.error("Failed to send PONG", e);
                }
            }
            default -> log.warn("Unhandled message type from {}: {}", playerId, msg.type());
        }
    }
}
