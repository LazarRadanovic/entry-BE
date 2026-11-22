package com.entry.fee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostRepository extends JpaRepository<Cost, UUID> {

    @EntityGraph(attributePaths = "unitIds")
    List<Cost> findByEntranceIdAndActiveTrueOrderByCreatedAtAsc(UUID entranceId);

    @EntityGraph(attributePaths = "unitIds")
    Optional<Cost> findByIdAndEntranceIdAndActiveTrue(UUID id, UUID entranceId);
}
