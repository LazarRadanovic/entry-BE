package com.entry.entrance.dto;

import java.util.UUID;

/** {@code inviteCode} is only filled in for entrance admins. */
public record EntranceDto(UUID id, String name, String address, long memberCount, String inviteCode) {
}
