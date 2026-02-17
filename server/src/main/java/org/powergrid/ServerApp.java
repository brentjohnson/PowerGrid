package org.powergrid;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Props;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.ServerBinding;
import org.apache.pekko.http.javadsl.model.ws.Message;
import org.apache.pekko.http.javadsl.server.AllDirectives;
import org.apache.pekko.http.javadsl.server.Route;
import org.apache.pekko.stream.OverflowStrategy;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.stream.javadsl.SourceQueueWithComplete;
import org.powergrid.actor.LobbyActor;
import org.powergrid.actor.PlayerConnectionActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

public class ServerApp extends AllDirectives {

    private static final Logger log = LoggerFactory.getLogger(ServerApp.class);

    public static void start() {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        ActorSystem<LobbyActor.Command> system =
                ActorSystem.create(LobbyActor.create(), "powergrid");

        Route route = new ServerApp().buildRoute(system);

        CompletionStage<ServerBinding> binding =
                Http.get(system)
                        .newServerAt("0.0.0.0", port)
                        .bind(route);

        binding.whenComplete((b, ex) -> {
            if (ex == null) {
                log.info("PowerGrid server online at ws://0.0.0.0:{}/ws", port);
                log.info("Press CTRL+C to stop");
            } else {
                log.error("Failed to bind to port {}", port, ex);
                system.terminate();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            system.terminate();
        }));
    }

    private Route buildRoute(ActorSystem<LobbyActor.Command> system) {
        return path("ws", () ->
                get(() ->
                        handleWebSocketMessages(buildWsFlow(system))
                )
        );
    }

    /**
     * Creates a WebSocket Flow for one connection. Pattern:
     * 1. Pre-materialize an outbound queue (actor → WS client).
     * 2. Spawn PlayerConnectionActor with a reference to that queue.
     * 3. Wire inbound WS text → IncomingText commands → actor.
     * 4. Watch for stream termination → send ConnectionClosed to actor.
     */
    private Flow<Message, Message, NotUsed> buildWsFlow(ActorSystem<LobbyActor.Command> system) {
        String playerId = UUID.randomUUID().toString();

        // Step 1: Pre-materialize outbound queue
        var preMat = Source.<Message>queue(256, OverflowStrategy.dropHead())
                .preMaterialize(system);
        SourceQueueWithComplete<Message> outQueue = preMat.first();
        Source<Message, NotUsed> outSource = preMat.second();

        // Step 2: Spawn connection actor with the queue
        ActorRef<PlayerConnectionActor.Command> connectionActor =
                system.systemActorOf(
                        PlayerConnectionActor.create(playerId, system, outQueue),
                        "player-" + playerId,
                        Props.empty()
                );

        // Step 3: Inbound sink — WS text messages → actor
        Sink<Message, NotUsed> inSink = Flow.<Message>create()
                .filter(Message::isText)
                .flatMapConcat(m -> m.asTextMessage().getStreamedText())
                .to(Sink.foreach(text ->
                        connectionActor.tell(new PlayerConnectionActor.IncomingText(text))
                ))
                .mapMaterializedValue(x -> NotUsed.getInstance());

        // Step 4: Compose and watch for termination
        return Flow.fromSinkAndSource(inSink, outSource)
                .watchTermination((nu, done) -> {
                    done.whenComplete((d, ex) ->
                            connectionActor.tell(new PlayerConnectionActor.ConnectionClosed())
                    );
                    return nu;
                });
    }
}
