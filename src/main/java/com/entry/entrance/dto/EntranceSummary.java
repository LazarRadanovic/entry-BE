package com.entry.entrance.dto;

import com.entry.entrance.Entrance;
import java.util.UUID;

public record EntranceSummary(UUID id, String name, String address) {

    public static EntranceSummary from(Entrance e) {
        return new EntranceSummary(e.getId(), e.getName(), e.getAddress());
    }
}
