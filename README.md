# PowerGrid

An online multiplayer implementation of the Power Grid board game.

## Tech Stack

| Component | Technology |
|-----------|------------|
| Server    | Java 21, Apache Pekko (Typed Actors), Pekko HTTP (WebSocket), Gradle |
| Client    | Godot 4.x (desktop: Linux / Windows / macOS) |
| Protocol  | WebSocket + JSON |
| CI        | GitLab CI/CD |

## Quick Start

### Server

Requirements: JDK 21+

```bash
cd server
./gradlew shadowJar
java -jar build/libs/powergrid-server.jar
```

The server binds on `0.0.0.0:8080` by default. Override with the `PORT` environment variable:

```bash
PORT=9000 java -jar build/libs/powergrid-server.jar
```

Run tests:

```bash
cd server
./gradlew test
```

### Client

Requirements: Godot 4.x editor

1. Open the Godot editor and import `client/project.godot`.
2. Press **F5** (or Run Project) to launch the main menu.
3. Enter the server URL (e.g. `ws://localhost:8080/ws`) and connect.

## Protocol

All WebSocket messages use the envelope:

```json
{ "type": "MESSAGE_TYPE", "payload": { ... } }
```

### Client → Server

| Type | Description |
|------|-------------|
| `HELLO` | Initial handshake, sends `playerName` |
| `LIST_ROOMS` | Request current lobby list |
| `CREATE_ROOM` | Create a new game room |
| `JOIN_ROOM` | Join an existing room by ID |
| `LEAVE_ROOM` | Leave the current room |
| `START_GAME` | Host starts the game |
| `BID_PLANT` | Place a bid on a power plant |
| `PASS_BID` | Pass during an auction |
| `BUY_RESOURCE` | Purchase resources |
| `BUILD_CITY` | Build in a city |
| `END_TURN` | Signal end of turn |
| `PING` | Keep-alive ping |

### Server → Client

| Type | Description |
|------|-------------|
| `WELCOME` | Assigns player UUID |
| `ERROR` | Error with code and message |
| `ROOM_LIST` | Current list of lobby rooms |
| `ROOM_JOINED` | Confirmation of room join |
| `ROOM_UPDATED` | Room state changed |
| `GAME_STARTING` | Game is about to begin |
| `GAME_STATE_UPDATE` | Full game state snapshot |
| `PLAYER_TURN` | Whose turn it is |
| `AUCTION_STARTED` | Power plant auction begins |
| `BID_PLACED` | A bid was placed |
| `PLANT_SOLD` | Auction result |
| `GAME_OVER` | Game ended with results |
| `PONG` | Keep-alive response |

## Project Structure

```
PowerGrid/
├── server/          # Java/Pekko backend
│   └── src/
│       └── main/java/org/powergrid/
│           ├── actor/     # Pekko Typed actors
│           ├── model/     # Domain model
│           ├── protocol/  # Wire message types
│           └── util/      # Shared utilities
└── client/          # Godot 4 frontend
    └── src/
        ├── autoload/  # Singletons (NetworkManager, GameState)
        ├── scenes/    # Main menu, lobby, game
        └── ui/        # Reusable UI components
```

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
