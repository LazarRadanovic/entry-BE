package com.entry.fee;

import java.util.UUID;

/** A cost, payment or the apartment list changed: clients reload what they show. */
public record CostsChangedEvent(UUID entranceId) {
}
