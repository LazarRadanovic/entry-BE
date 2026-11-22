package com.entry.realtime;

/** Envelope for every STOMP message: the client switches on {@code type}. */
public record RealtimeEvent(String type, Object payload) {
}
