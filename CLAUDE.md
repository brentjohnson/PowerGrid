# CLAUDE.md — PowerGrid AI Coding Guide

This file provides rules and conventions for AI-assisted development of this project.

## Package Structure

All server code lives under `org.powergrid`. Sub-packages and their responsibilities:

| Package | Contains |
|---------|----------|
| `org.powergrid` | `Main`, `ServerApp` — entry points only |
| `org.powergrid.actor` | Pekko Typed actors: `LobbyActor`, `GameSessionActor`, `PlayerConnectionActor` |
| `org.powergrid.model` | Immutable domain objects: `GameState`, `Player`, `LobbyRoom` |
| `org.powergrid.protocol` | Wire types: `MessageType` enum, `InboundMessage`, `OutboundMessage` |
| `org.powergrid.util` | Shared utilities: `JsonMapper` singleton |

Do not add classes to the root `org.powergrid` package beyond the two entry points.

## Pekko Typed Actor Rules

1. All actors **must** extend `AbstractBehavior<T>` — never use `Behaviors.receive` lambdas for non-trivial actors.
2. Every actor **must** define a `sealed interface Command` (or `sealed interface Message`) as its protocol. All commands implement this interface.
3. **No blocking I/O** in message handlers. Use `Behaviors.withTimers` for scheduled work, `context.pipeToSelf()` to lift async results back into the actor.
4. Actor creation: always use `context.spawn(...)` for children, never `ActorSystem.create(...)` inside handlers.
5. `GameSessionActor` uses an explicit state machine enum (`WAITING`, `STARTING`, `IN_PROGRESS`, `ENDED`). Use `Behaviors.same()` when state doesn't change; return a new `Behavior<Command>` when it does.
6. All mutable actor state **must** be declared as non-static fields — never use `static` mutable state.

## JSON Protocol

- Every message on the wire: `{ "type": "MESSAGE_TYPE", "payload": { ... } }`
- Use **`JsonMapper`** singleton for all serialization/deserialization. Never instantiate `ObjectMapper` elsewhere.
- Payload classes in `org.powergrid.protocol` use Jackson annotations (`@JsonProperty`, etc.).
- Inbound messages: deserialize to `InboundMessage`; read `type` discriminator first, then call `JsonMapper.getInstance().treeToValue(msg.payload(), ...)` to parse the specific payload.
- Outbound messages: construct `OutboundMessage` and serialize with `JsonMapper.getInstance().writeValueAsString(...)`.
- **Never** pass raw `Map<String, Object>` as a payload. Always use a typed payload class.
- When adding a new message type, follow the checklist below.

## Build Commands

- Always use `./gradlew` from the `server/` directory — **never** use a system-installed `gradle`.
- Common commands:
  - `./gradlew build` — compile + test
  - `./gradlew test` — tests only
  - `./gradlew shadowJar` — build fat JAR at `build/libs/powergrid-server.jar`
  - `./gradlew classes` — compile only (fast check)

## GDScript Conventions

1. All GDScript files use **static typing** — every variable and function parameter must have a type annotation.
2. `NetworkManager` (autoload singleton) is the **only** place that creates or manages a `WebSocketPeer`. Scenes must never create their own WebSocket connections.
3. Signals must be connected in `_ready()` and disconnected in `_exit_tree()`:
   ```gdscript
   func _ready() -> void:
       NetworkManager.message_received.connect(_on_message)

   func _exit_tree() -> void:
       NetworkManager.message_received.disconnect(_on_message)
   ```
4. `GameState` (autoload singleton) is the single source of truth for client-side game state. Scenes read from it; they do not store duplicate state.
5. Inbound messages are dispatched by `type` string. Use a `match` statement, not a chain of `if/elif`.
6. Scene files (`.tscn`) and their scripts (`.gd`) always share the same base name and live in the same directory.

## Protocol Change Checklist

When adding a new message type, do **all** of the following in order:

1. Add the new constant to `MessageType.java` (both client-originated and server-originated sections as appropriate).
2. Create the payload POJO in `org.powergrid.protocol` (e.g., `BuyResourcePayload.java`).
3. Handle the inbound message in the appropriate actor (`LobbyActor` or `GameSessionActor`).
4. Handle the outbound message in the relevant Godot scene or singleton.
5. Update the protocol table in `README.md`.
6. Add a test case to `MessageSerializationTest.java`.

## CI Notes

- CI runs on **GitHub Actions** (workflow: `.github/workflows/build.yml`).
- Server jobs use JDK 21 (Eclipse Temurin); client export jobs use `barichello/godot-ci:4.4` in a container.
- The Gradle daemon is disabled in CI via `GRADLE_OPTS=-Dorg.gradle.daemon=false`.
- `GRADLE_USER_HOME` is set to `.gradle-home/` inside the workspace so caches work with GitHub’s cache.
- The workflow runs only when files under `server/`, `client/`, or `.github/` change (path filters on push and pull_request).
- Server: validate wrapper → build → test; on push to default branch (or tag), package shadow JAR and upload artifact. Client: on push to default branch (or tag), export Linux/Windows/macOS and upload artifacts.
