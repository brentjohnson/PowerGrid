package org.powergrid.actor;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.actor.typed.javadsl.TimerScheduler;
import org.powergrid.model.GameState;
import org.powergrid.model.Player;
import org.powergrid.protocol.MessageType;
import org.powergrid.protocol.OutboundMessage;
import org.powergrid.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a single active game session. Lifecycle: WAITING → STARTING → IN_PROGRESS → ENDED.
 * One instance per room, spawned by LobbyActor when a game starts.
 */
public class GameSessionActor extends AbstractBehavior<GameSessionActor.Command> {

    private static final Logger log = LoggerFactory.getLogger(GameSessionActor.class);

    // ─── State machine ───────────────────────────────────────────────────────

    public enum Phase { WAITING, STARTING, IN_PROGRESS, ENDED }

    // ─── Command protocol ────────────────────────────────────────────────────

    public sealed interface Command permits
            PlayerAction,
            PhaseTimeout,
            PlayerLeft {
    }

    public record PlayerAction(
            String playerId,
            MessageType actionType,
            com.fasterxml.jackson.databind.JsonNode payload
    ) implements Command {}

    public record PhaseTimeout(String phase) implements Command {}

    public record PlayerLeft(String playerId) implements Command {}

    // Timer key
    private record PhaseTimerKey(String phase) {}

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static Behavior<Command> create(
            String roomId,
            List<String> playerIds,
            ActorRef<LobbyActor.Command> lobby
    ) {
        return Behaviors.withTimers(timers ->
                Behaviors.setup(ctx -> new GameSessionActor(ctx, timers, roomId, playerIds, lobby))
        );
    }

    // ─── State ───────────────────────────────────────────────────────────────

    private final String roomId;
    private final List<String> playerIds;
    private final ActorRef<LobbyActor.Command> lobby;
    private final TimerScheduler<Command> timers;

    private Phase phase = Phase.WAITING;
    private GameState gameState;
    private int currentPlayerIndex = 0;

    // ─── Constructor ─────────────────────────────────────────────────────────

    private GameSessionActor(
            ActorContext<Command> context,
            TimerScheduler<Command> timers,
            String roomId,
            List<String> playerIds,
            ActorRef<LobbyActor.Command> lobby
    ) {
        super(context);
        this.timers = timers;
        this.roomId = roomId;
        this.playerIds = new ArrayList<>(playerIds);
        this.lobby = lobby;
        this.gameState = GameState.initial(roomId, playerIds);

        startGame();
    }

    // ─── Message dispatch ────────────────────────────────────────────────────

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(PlayerAction.class, this::onPlayerAction)
                .onMessage(PhaseTimeout.class, this::onPhaseTimeout)
                .onMessage(PlayerLeft.class, this::onPlayerLeft)
                .build();
    }

    // ─── Handlers ────────────────────────────────────────────────────────────

    private Behavior<Command> onPlayerAction(PlayerAction cmd) {
        if (phase != Phase.IN_PROGRESS) {
            log.warn("Ignoring action {} from {} — game phase is {}", cmd.actionType(), cmd.playerId(), phase);
            return Behaviors.same();
        }

        log.debug("Player action: {} from {}", cmd.actionType(), cmd.playerId());

        switch (cmd.actionType()) {
            case BID_PLANT -> handleBidPlant(cmd);
            case PASS_BID -> handlePassBid(cmd);
            case BUY_RESOURCE -> handleBuyResource(cmd);
            case BUILD_CITY -> handleBuildCity(cmd);
            case END_TURN -> handleEndTurn(cmd);
            default -> log.warn("Unhandled action type: {}", cmd.actionType());
        }

        return Behaviors.same();
    }

    private Behavior<Command> onPhaseTimeout(PhaseTimeout cmd) {
        log.info("Phase timeout: {} in room {}", cmd.phase(), roomId);
        advanceTurn();
        return Behaviors.same();
    }

    private Behavior<Command> onPlayerLeft(PlayerLeft cmd) {
        log.info("Player {} left game {}", cmd.playerId(), roomId);
        playerIds.remove(cmd.playerId());
        if (playerIds.size() < 2) {
            endGame("Player disconnected — not enough players.");
        }
        return Behaviors.same();
    }

    // ─── Game logic stubs ────────────────────────────────────────────────────

    private void startGame() {
        phase = Phase.IN_PROGRESS;
        log.info("Game started in room {} with players {}", roomId, playerIds);
        broadcastGameState();
        schedulePhaseTimeout("TURN", Duration.ofSeconds(120));
    }

    private void handleBidPlant(PlayerAction cmd) {
        // TODO: implement auction logic
        broadcastGameState();
    }

    private void handlePassBid(PlayerAction cmd) {
        // TODO: implement pass bid logic
        broadcastGameState();
    }

    private void handleBuyResource(PlayerAction cmd) {
        // TODO: implement resource buying logic
        broadcastGameState();
    }

    private void handleBuildCity(PlayerAction cmd) {
        // TODO: implement city building logic
        broadcastGameState();
    }

    private void handleEndTurn(PlayerAction cmd) {
        String currentPlayer = playerIds.get(currentPlayerIndex);
        if (!currentPlayer.equals(cmd.playerId())) {
            log.warn("Out-of-turn END_TURN from {}", cmd.playerId());
            return;
        }
        advanceTurn();
    }

    private void advanceTurn() {
        timers.cancel(new PhaseTimerKey("TURN"));
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        broadcastGameState();
        schedulePhaseTimeout("TURN", Duration.ofSeconds(120));
    }

    private void endGame(String reason) {
        phase = Phase.ENDED;
        timers.cancelAll();
        log.info("Game over in room {}: {}", roomId, reason);

        var payload = JsonMapper.getInstance().createObjectNode()
                .put("roomId", roomId)
                .put("reason", reason);
        broadcast(MessageType.GAME_OVER, payload);
    }

    // ─── Broadcast helpers ───────────────────────────────────────────────────

    private void broadcastGameState() {
        try {
            var payload = JsonMapper.getInstance().valueToTree(gameState);
            broadcast(MessageType.GAME_STATE_UPDATE, payload);
        } catch (Exception e) {
            log.error("Failed to serialize game state", e);
        }
    }

    private void broadcast(MessageType type, com.fasterxml.jackson.databind.JsonNode payload) {
        // GameSessionActor doesn't hold connection refs directly;
        // it sends through PlayerConnectionActor refs stored in the context children.
        // For now, this is a placeholder — real delivery goes through LobbyActor routing.
        log.debug("Broadcast {} to room {}", type, roomId);
    }

    private void schedulePhaseTimeout(String phase, Duration delay) {
        timers.startSingleTimer(new PhaseTimerKey(phase), new PhaseTimeout(phase), delay);
    }
}
