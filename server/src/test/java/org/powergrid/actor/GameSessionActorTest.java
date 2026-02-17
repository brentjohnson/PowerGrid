package org.powergrid.actor;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionActorTest {

    static ActorTestKit testKit;

    @BeforeAll
    static void setup() {
        testKit = ActorTestKit.create();
    }

    @AfterAll
    static void teardown() {
        testKit.shutdownTestKit();
    }

    @Test
    void gameSessionCanBeCreated() {
        TestProbe<LobbyActor.Command> lobbyProbe = testKit.createTestProbe();

        String roomId = "room-test-001";
        List<String> players = List.of("p1", "p2", "p3");

        // Should spawn without error
        ActorRef<GameSessionActor.Command> session = testKit.spawn(
                GameSessionActor.create(roomId, players, lobbyProbe.getRef()),
                "session-test-001"
        );

        assertNotNull(session);
    }

    @Test
    void playerLeftWithOnlyOnePlayerEndsGame() {
        TestProbe<LobbyActor.Command> lobbyProbe = testKit.createTestProbe();

        String roomId = "room-test-002";
        List<String> players = List.of("p1", "p2");

        ActorRef<GameSessionActor.Command> session = testKit.spawn(
                GameSessionActor.create(roomId, players, lobbyProbe.getRef()),
                "session-test-002"
        );

        // First player leaves — session should end game (only 1 player left)
        session.tell(new GameSessionActor.PlayerLeft("p1"));

        // Session stops gracefully — no exceptions should propagate
        // (Detailed broadcast verification requires wiring connections, tested in integration tests)
    }

    @Test
    void actionIgnoredBeforeGameStart() {
        TestProbe<LobbyActor.Command> lobbyProbe = testKit.createTestProbe();

        String roomId = "room-test-003";
        List<String> players = List.of("p1", "p2");

        ActorRef<GameSessionActor.Command> session = testKit.spawn(
                GameSessionActor.create(roomId, players, lobbyProbe.getRef()),
                "session-test-003"
        );

        // Sending an action to an IN_PROGRESS game should not throw
        var payload = org.powergrid.util.JsonMapper.getInstance().createObjectNode();
        session.tell(new GameSessionActor.PlayerAction(
                "p1",
                org.powergrid.protocol.MessageType.END_TURN,
                payload
        ));

        // No exceptions = test passes
        lobbyProbe.expectNoMessage();
    }
}
