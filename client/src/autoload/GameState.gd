extends Node

## GameState — autoload singleton
## Single source of truth for client-side game state.
## Scenes read from this node; they do NOT store duplicate state.

signal state_updated
signal player_id_assigned(player_id: String)
signal rooms_updated(rooms: Array)

# ─── Identity ────────────────────────────────────────────────────────────────

var local_player_id: String = ""
var local_player_name: String = ""

# ─── Lobby ───────────────────────────────────────────────────────────────────

var current_room_id: String = ""
var current_room_name: String = ""
var lobby_rooms: Array = []  # Array[Dictionary]
var room_players: Array = []  # Array[Dictionary]

# ─── In-game ─────────────────────────────────────────────────────────────────

var game_active: bool = false
var round: int = 0
var phase: String = ""
var current_player_id: String = ""
var players: Array = []  # Array[Dictionary]


func _ready() -> void:
	NetworkManager.message_received.connect(_on_message)


func _exit_tree() -> void:
	NetworkManager.message_received.disconnect(_on_message)


func reset() -> void:
	local_player_id = ""
	current_room_id = ""
	current_room_name = ""
	lobby_rooms = []
	room_players = []
	game_active = false
	round = 0
	phase = ""
	current_player_id = ""
	players = []


# ─── Message handling ─────────────────────────────────────────────────────────

func _on_message(msg: Dictionary) -> void:
	var type: String = msg.get("type", "")
	var payload: Dictionary = msg.get("payload", {})

	match type:
		"WELCOME":
			local_player_id = payload.get("playerId", "")
			player_id_assigned.emit(local_player_id)

		"ROOM_LIST":
			lobby_rooms = payload.get("rooms", [])
			rooms_updated.emit(lobby_rooms)

		"ROOM_JOINED":
			current_room_id = payload.get("roomId", "")
			current_room_name = payload.get("roomName", "")
			state_updated.emit()

		"ROOM_UPDATED":
			room_players = payload.get("players", [])
			state_updated.emit()

		"GAME_STARTING":
			game_active = true
			state_updated.emit()

		"GAME_STATE_UPDATE":
			round = payload.get("round", round)
			phase = payload.get("phase", phase)
			current_player_id = payload.get("currentPlayerId", current_player_id)
			players = payload.get("players", players)
			state_updated.emit()

		"GAME_OVER":
			game_active = false
			state_updated.emit()
