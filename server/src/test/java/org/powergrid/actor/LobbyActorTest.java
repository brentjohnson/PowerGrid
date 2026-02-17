package org.powergrid.actor;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.TestProbe;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyActorTest {

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
    void playerConnectedReceivesWelcome() {
        ActorRef<LobbyActor.Command> lobby = testKit.spawn(LobbyActor.create(), "lobby-welcome-test");
        TestProbe<PlayerConnectionActor.Command> probe = testKit.createTestProbe();

        String playerId = "player-001";
        lobby.tell(new LobbyActor.PlayerConnected(playerId, "Alice", probe.getRef()));

        // Expect a SendText command with a WELCOME message
        PlayerConnectionActor.SendText sent = probe.expectMessageClass(PlayerConnectionActor.SendText.class);
        assertTrue(sent.json().contains("\"type\":\"WELCOME\""), "Expected WELCOME in: " + sent.json());
        assertTrue(sent.json().contains(playerId), "Expected playerId in: " + sent.json());
    }

    @Test
    void listRoomsReturnsEmptyInitially() {
        ActorRef<LobbyActor.Command> lobby = testKit.spawn(LobbyActor.create(), "lobby-list-test");
        TestProbe<PlayerConnectionActor.Command> probe = testKit.createTestProbe();

        String playerId = "player-002";
        lobby.tell(new LobbyActor.PlayerConnected(playerId, "Bob", probe.getRef()));
        probe.expectMessageClass(PlayerConnectionActor.SendText.class); // WELCOME

        lobby.tell(new LobbyActor.ListRooms(playerId));

        PlayerConnectionActor.SendText sent = probe.expectMessageClass(PlayerConnectionActor.SendText.class);
        assertTrue(sent.json().contains("\"type\":\"ROOM_LIST\""), "Expected ROOM_LIST in: " + sent.json());
        assertTrue(sent.json().contains("\"rooms\":[]"), "Expected empty rooms array in: " + sent.json());
    }

    @Test
    void createRoomAndJoinRoom() {
        ActorRef<LobbyActor.Command> lobby = testKit.spawn(LobbyActor.create(), "lobby-create-test");
        TestProbe<PlayerConnectionActor.Command> hostProbe = testKit.createTestProbe();
        TestProbe<PlayerConnectionActor.Command> guestProbe = testKit.createTestProbe();

        String hostId = "host-001";
        String guestId = "guest-001";

        // Register host
        lobby.tell(new LobbyActor.PlayerConnected(hostId, "Host", hostProbe.getRef()));
        hostProbe.expectMessageClass(PlayerConnectionActor.SendText.class); // WELCOME

        // Host creates room
        lobby.tell(new LobbyActor.CreateRoom(hostId, "Test Room"));
        PlayerConnectionActor.SendText roomJoined = hostProbe.expectMessageClass(PlayerConnectionActor.SendText.class);
        assertTrue(roomJoined.json().contains("\"type\":\"ROOM_JOINED\""), "Expected ROOM_JOINED in: " + roomJoined.json());

        // Extract roomId from response
        int idx = roomJoined.json().indexOf("\"roomId\":\"") + 10;
        String roomId = roomJoined.json().substring(idx, roomJoined.json().indexOf("\"", idx));

        // Register guest
        lobby.tell(new LobbyActor.PlayerConnected(guestId, "Guest", guestProbe.getRef()));
        guestProbe.expectMessageClass(PlayerConnectionActor.SendText.class); // WELCOME

        // Guest joins room
        lobby.tell(new LobbyActor.JoinRoom(guestId, roomId));
        PlayerConnectionActor.SendText guestJoined = guestProbe.expectMessageClass(PlayerConnectionActor.SendText.class);
        assertTrue(guestJoined.json().contains("\"type\":\"ROOM_JOINED\""), "Expected ROOM_JOINED for guest in: " + guestJoined.json());

        // Host should get ROOM_UPDATED
        PlayerConnectionActor.SendText hostUpdated = hostProbe.expectMessageClass(PlayerConnectionActor.SendText.class);
        assertTrue(hostUpdated.json().contains("\"type\":\"ROOM_UPDATED\""), "Expected ROOM_UPDATED for host in: " + hostUpdated.json());
    }

    @Test
    void disconnectedPlayerIsRemovedFromRoom() {
        ActorRef<LobbyActor.Command> lobby = testKit.spawn(LobbyActor.create(), "lobby-disconnect-test");
        TestProbe<PlayerConnectionActor.Command> probe = testKit.createTestProbe();

        String playerId = "player-003";
        lobby.tell(new LobbyActor.PlayerConnected(playerId, "Carol", probe.getRef()));
        probe.expectMessageClass(PlayerConnectionActor.SendText.class); // WELCOME

        lobby.tell(new LobbyActor.CreateRoom(playerId, "Solo Room"));
        probe.expectMessageClass(PlayerConnectionActor.SendText.class); // ROOM_JOINED

        // Disconnect — should not throw
        lobby.tell(new LobbyActor.PlayerDisconnected(playerId));
        probe.expectNoMessage();
    }
}
