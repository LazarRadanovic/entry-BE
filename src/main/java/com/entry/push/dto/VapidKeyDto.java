package com.entry.push.dto;

/** {@code publicKey} goes to {@code SwPush.requestSubscription({serverPublicKey})}. */
public record VapidKeyDto(boolean enabled, String publicKey) {
}
