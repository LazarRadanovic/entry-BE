package com.entry.billing;

import java.util.UUID;

public record TrialEndingEvent(UUID entranceId, long daysLeft) {
}
