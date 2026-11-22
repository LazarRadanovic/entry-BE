package com.entry.fee;

import java.util.UUID;

/** Someone's payment, the fee amount or the apartment list changed: clients reload the table. */
public record FeesChangedEvent(UUID entranceId, int year) {
}
