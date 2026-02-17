extends Node

## NetworkManager — autoload singleton
## The ONLY place in the client that manages a WebSocketPeer.
## All scenes communicate through this singleton and its signals.

signal connected_to_server
signal disconnected_from_server
signal message_received(msg: Dictionary)

const RECONNECT_DELAY_SEC: float = 3.0

var _socket: WebSocketPeer = WebSocketPeer.new()
var _state: WebSocketPeer.State = WebSocketPeer.STATE_CLOSED
var _url: String = ""


func _process(_delta: float) -> void:
	_socket.poll()
	var new_state: WebSocketPeer.State = _socket.get_ready_state()

	if new_state != _state:
		_on_state_changed(_state, new_state)
		_state = new_state

	if _state == WebSocketPeer.STATE_OPEN:
		while _socket.get_available_packet_count() > 0:
			var raw: PackedByteArray = _socket.get_packet()
			var text: String = raw.get_string_from_utf8()
			_on_text_received(text)


func connect_to_server(url: String) -> void:
	_url = url
	_socket = WebSocketPeer.new()
	var err: Error = _socket.connect_to_url(url)
	if err != OK:
		push_error("NetworkManager: Failed to initiate connection to %s (error %d)" % [url, err])


func disconnect_from_server() -> void:
	_socket.close(1000, "Client disconnected")


func send(msg: Dictionary) -> void:
	if _state != WebSocketPeer.STATE_OPEN:
		push_warning("NetworkManager: Attempted to send while not connected")
		return
	var json_string: String = JSON.stringify(msg)
	_socket.send_text(json_string)


# ─── Private ────────────────────────────────────────────────────────────────

func _on_state_changed(old: WebSocketPeer.State, new_s: WebSocketPeer.State) -> void:
	match new_s:
		WebSocketPeer.STATE_OPEN:
			print("NetworkManager: Connected to ", _url)
			connected_to_server.emit()
		WebSocketPeer.STATE_CLOSED:
			var code: int = _socket.get_close_code()
			var reason: String = _socket.get_close_reason()
			print("NetworkManager: Disconnected (code=%d reason=%s)" % [code, reason])
			disconnected_from_server.emit()
		WebSocketPeer.STATE_CONNECTING:
			print("NetworkManager: Connecting to ", _url)
		WebSocketPeer.STATE_CLOSING:
			print("NetworkManager: Closing connection")


func _on_text_received(text: String) -> void:
	var parsed: Variant = JSON.parse_string(text)
	if parsed == null or not parsed is Dictionary:
		push_warning("NetworkManager: Received non-JSON or non-object text: " + text)
		return
	message_received.emit(parsed as Dictionary)
