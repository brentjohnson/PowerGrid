extends Control

## Game scene script.
## Renders the active game state and routes player actions to NetworkManager.

@onready var _hud: Control = $HUD
@onready var _status_label: Label = $HUD/StatusLabel


func _ready() -> void:
	NetworkManager.message_received.connect(_on_message)
	GameState.state_updated.connect(_on_state_updated)

	_refresh_ui()


func _exit_tree() -> void:
	NetworkManager.message_received.disconnect(_on_message)
	GameState.state_updated.disconnect(_on_state_updated)


# ─── Signal handlers ──────────────────────────────────────────────────────────

func _on_message(msg: Dictionary) -> void:
	var type: String = msg.get("type", "")
	var payload: Dictionary = msg.get("payload", {})

	match type:
		"GAME_OVER":
			var reason: String = payload.get("reason", "")
			_status_label.text = "Game over: " + reason
			await get_tree().create_timer(3.0).timeout
			get_tree().change_scene_to_file("res://src/scenes/main_menu/MainMenu.tscn")

		"PLAYER_TURN":
			var pid: String = payload.get("playerId", "")
			_status_label.text = "It's %s's turn" % _player_name(pid)

		"AUCTION_STARTED":
			var plant_id: int = payload.get("plantId", 0)
			var min_bid: int = payload.get("minimumBid", 0)
			_status_label.text = "Auction: Plant %d — minimum bid %d" % [plant_id, min_bid]

		"ERROR":
			push_warning("Game error: %s" % payload.get("message", "Unknown"))


func _on_state_updated() -> void:
	_refresh_ui()


# ─── Actions (called by UI buttons) ──────────────────────────────────────────

func action_end_turn() -> void:
	NetworkManager.send({"type": "END_TURN", "payload": {}})


func action_bid_plant(plant_id: int, amount: int) -> void:
	NetworkManager.send({
		"type": "BID_PLANT",
		"payload": {"plantId": plant_id, "amount": amount}
	})


func action_pass_bid() -> void:
	NetworkManager.send({"type": "PASS_BID", "payload": {}})


func action_buy_resource(resource: String, amount: int) -> void:
	NetworkManager.send({
		"type": "BUY_RESOURCE",
		"payload": {"resource": resource, "amount": amount}
	})


func action_build_city(city_id: String) -> void:
	NetworkManager.send({
		"type": "BUILD_CITY",
		"payload": {"cityId": city_id}
	})


# ─── Helpers ─────────────────────────────────────────────────────────────────

func _refresh_ui() -> void:
	if GameState.current_player_id == GameState.local_player_id:
		_status_label.text = "Your turn — Phase: " + GameState.phase
	else:
		_status_label.text = "Waiting for %s — Phase: %s" % [
			_player_name(GameState.current_player_id),
			GameState.phase
		]


func _player_name(player_id: String) -> String:
	for p: Dictionary in GameState.players:
		if p.get("id", "") == player_id:
			return p.get("name", player_id)
	return player_id
