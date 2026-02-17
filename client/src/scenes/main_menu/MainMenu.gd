extends Control

## MainMenu scene script.
## Handles player name input, server URL input, and initiating connection.

@onready var _name_input: LineEdit = $VBoxContainer/NameInput
@onready var _url_input: LineEdit = $VBoxContainer/UrlInput
@onready var _connect_button: Button = $VBoxContainer/ConnectButton
@onready var _status_label: Label = $VBoxContainer/StatusLabel


func _ready() -> void:
	NetworkManager.connected_to_server.connect(_on_connected)
	NetworkManager.disconnected_from_server.connect(_on_disconnected)
	GameState.player_id_assigned.connect(_on_player_id_assigned)

	_url_input.text = "ws://localhost:8080/ws"
	_status_label.text = ""
	_connect_button.pressed.connect(_on_connect_pressed)


func _exit_tree() -> void:
	NetworkManager.connected_to_server.disconnect(_on_connected)
	NetworkManager.disconnected_from_server.disconnect(_on_disconnected)
	GameState.player_id_assigned.disconnect(_on_player_id_assigned)


# ─── Signal handlers ──────────────────────────────────────────────────────────

func _on_connect_pressed() -> void:
	var player_name: String = _name_input.text.strip_edges()
	var url: String = _url_input.text.strip_edges()

	if player_name.is_empty():
		_status_label.text = "Please enter a player name."
		return
	if url.is_empty():
		_status_label.text = "Please enter a server URL."
		return

	GameState.local_player_name = player_name
	_connect_button.disabled = true
	_status_label.text = "Connecting..."
	NetworkManager.connect_to_server(url)


func _on_connected() -> void:
	_status_label.text = "Connected — sending HELLO..."
	NetworkManager.send({
		"type": "HELLO",
		"payload": {"playerName": GameState.local_player_name}
	})


func _on_disconnected() -> void:
	_connect_button.disabled = false
	_status_label.text = "Disconnected."


func _on_player_id_assigned(_player_id: String) -> void:
	get_tree().change_scene_to_file("res://src/scenes/lobby/Lobby.tscn")
