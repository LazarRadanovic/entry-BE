package com.entry.lostfound.event;

import com.entry.lostfound.LostFoundKind;
import java.util.UUID;

/** A neighbour said "it's mine" / "I found it": tells the author. */
public record LostFoundRespondedEvent(UUID itemId, UUID authorId, UUID responderId, LostFoundKind kind,
        String title) {
}
