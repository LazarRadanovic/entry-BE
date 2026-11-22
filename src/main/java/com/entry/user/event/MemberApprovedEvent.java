package com.entry.user.event;

import java.util.UUID;

public record MemberApprovedEvent(UUID entranceId, UUID userId, UUID approvedById) {
}
