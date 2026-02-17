extends Control

## HUD — in-game heads-up display overlay.
## Displays round, phase, current player, and per-player status cards.

@onready var _round_label: Label = $TopBar/RoundLabel
@onready var _phase_label: Label = $TopBar/PhaseLabel
@onready var _turn_label: Label = $TopBar/TurnLabel
@onready var _player_cards: HBoxContainer = $BottomBar/PlayerCards

const PlayerCardScene: PackedScene = preload("res://src/ui/components/PlayerCard.tscn")


func _ready() -> void:
	GameState.state_updated.connect(_on_state_updated)
	_refresh()


func _exit_tree() -> void:
	GameState.state_updated.disconnect(_on_state_updated)


func _on_state_updated() -> void:
	_refresh()


func _refresh() -> void:
	_round_label.text = "Round %d" % GameState.round
	_phase_label.text = GameState.phase

	if GameState.current_player_id == GameState.local_player_id:
		_turn_label.text = "Your turn"
	else:
		_turn_label.text = "Waiting..."

	# Rebuild player cards
	for child: Node in _player_cards.get_children():
		child.queue_free()

	for p: Dictionary in GameState.players:
		var card: PanelContainer = PlayerCardScene.instantiate() as PanelContainer
		_player_cards.add_child(card)
		(card as PlayerCard).setup(p, p.get("id", "") == GameState.local_player_id)
