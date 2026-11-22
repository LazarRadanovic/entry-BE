package com.entry.fee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeUnitRepository extends JpaRepository<FeeUnit, UUID> {

    List<FeeUnit> findByEntranceId(UUID entranceId);

    Optional<FeeUnit> findByIdAndEntranceId(UUID id, UUID entranceId);
}
