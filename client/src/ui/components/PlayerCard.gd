extends PanelContainer

## PlayerCard — reusable UI component showing a single player's status.

@onready var _name_label: Label = $VBoxContainer/NameLabel
@onready var _status_label: Label = $VBoxContainer/StatusLabel


func setup(player: Dictionary, is_local: bool) -> void:
	var player_name: String = player.get("name", "Unknown")
	_name_label.text = player_name + (" (you)" if is_local else "")
	_status_label.text = ""
