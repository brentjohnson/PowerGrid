extends Control

## Lobby scene script.
## Displays available rooms, allows creating/joining rooms, and starting the game.

@onready var _room_list: ItemList = $HSplitContainer/LeftPanel/RoomList
@onready var _refresh_button: Button = $HSplitContainer/LeftPanel/RefreshButton
@onready var _create_button: Button = $HSplitContainer/LeftPanel/CreateButton
@onready var _join_button: Button = $HSplitContainer/LeftPanel/JoinButton
@onready var _player_list: ItemList = $HSplitContainer/RightPanel/PlayerList
@onready var _start_button: Button = $HSplitContainer/RightPanel/StartButton
@onready var _leave_button: Button = $HSplitContainer/RightPanel/LeaveButton
@onready var _room_name_input: LineEdit = $HSplitContainer/LeftPanel/RoomNameInput

var _selected_room_id: String = ""


func _ready() -> void:
	NetworkManager.message_received.connect(_on_message)
	GameState.rooms_updated.connect(_on_rooms_updated)
	GameState.state_updated.connect(_on_state_updated)

	_refresh_button.pressed.connect(_on_refresh_pressed)
	_create_button.pressed.connect(_on_create_pressed)
	_join_button.pressed.connect(_on_join_pressed)
	_start_button.pressed.connect(_on_start_pressed)
	_leave_button.pressed.connect(_on_leave_pressed)
	_room_list.item_selected.connect(_on_room_selected)

	_start_button.disabled = true
	_leave_button.disabled = true
	_join_button.disabled = true

	NetworkManager.send({"type": "LIST_ROOMS", "payload": {}})


func _exit_tree() -> void:
	NetworkManager.message_received.disconnect(_on_message)
	GameState.rooms_updated.disconnect(_on_rooms_updated)
	GameState.state_updated.disconnect(_on_state_updated)


# ─── Signal handlers ──────────────────────────────────────────────────────────

func _on_refresh_pressed() -> void:
	NetworkManager.send({"type": "LIST_ROOMS", "payload": {}})


func _on_create_pressed() -> void:
	var room_name: String = _room_name_input.text.strip_edges()
	if room_name.is_empty():
		room_name = "%s's Room" % GameState.local_player_name
	NetworkManager.send({"type": "CREATE_ROOM", "payload": {"roomName": room_name}})


func _on_join_pressed() -> void:
	if _selected_room_id.is_empty():
		return
	NetworkManager.send({"type": "JOIN_ROOM", "payload": {"roomId": _selected_room_id}})


func _on_start_pressed() -> void:
	NetworkManager.send({"type": "START_GAME", "payload": {}})


func _on_leave_pressed() -> void:
	NetworkManager.send({"type": "LEAVE_ROOM", "payload": {}})
	_start_button.disabled = true
	_leave_button.disabled = true


func _on_room_selected(index: int) -> void:
	var meta: Variant = _room_list.get_item_metadata(index)
	if meta is String:
		_selected_room_id = meta as String
		_join_button.disabled = false


func _on_rooms_updated(rooms: Array) -> void:
	_room_list.clear()
	for room: Dictionary in rooms:
		var label: String = "%s (%d/6)" % [room.get("roomName", "?"), room.get("playerCount", 0)]
		var idx: int = _room_list.add_item(label)
		_room_list.set_item_metadata(idx, room.get("roomId", ""))


func _on_state_updated() -> void:
	if GameState.game_active:
		get_tree().change_scene_to_file("res://src/scenes/game/Game.tscn")
		return

	if not GameState.current_room_id.is_empty():
		_start_button.disabled = (GameState.local_player_id != _get_host_id())
		_leave_button.disabled = false
		_refresh_player_list()


func _on_message(msg: Dictionary) -> void:
	var type: String = msg.get("type", "")
	match type:
		"ERROR":
			var payload: Dictionary = msg.get("payload", {})
			push_warning("Lobby error: %s — %s" % [payload.get("code", ""), payload.get("message", "")])


# ─── Helpers ─────────────────────────────────────────────────────────────────

func _refresh_player_list() -> void:
	_player_list.clear()
	for p: Dictionary in GameState.room_players:
		var name_str: String = p.get("playerName", "Unknown")
		var pid: String = p.get("playerId", "")
		var label: String = name_str if pid != GameState.local_player_id else name_str + " (you)"
		_player_list.add_item(label)


func _get_host_id() -> String:
	if GameState.room_players.is_empty():
		return ""
	return (GameState.room_players[0] as Dictionary).get("playerId", "")
