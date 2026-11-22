package com.entry.craftsman;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface CraftsmanRepository extends JpaRepository<Craftsman, UUID> {

    List<Craftsman> findByEntranceId(UUID entranceId);

    List<Craftsman> findByEntranceIdAndTrade(UUID entranceId, Trade trade);

    Optional<Craftsman> findByIdAndEntranceId(UUID id, UUID entranceId);

    // ---- recommendations (no entity: a pure join table) ----

    interface CountRow {
        UUID getCraftsmanId();

        long getCount();
    }

    @Query(value = """
            SELECT craftsman_id AS "craftsmanId", count(*) AS "count" FROM craftsman_recommendation
            WHERE craftsman_id IN (:ids) GROUP BY craftsman_id""", nativeQuery = true)
    List<CountRow> recommendationCounts(@Param("ids") Collection<UUID> ids);

    @Query(value = """
            SELECT craftsman_id FROM craftsman_recommendation
            WHERE user_id = :userId AND craftsman_id IN (:ids)""", nativeQuery = true)
    List<UUID> recommendedBy(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);

    @Modifying
    @Query(value = """
            INSERT INTO craftsman_recommendation (craftsman_id, user_id) VALUES (:craftsmanId, :userId)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    int recommend(@Param("craftsmanId") UUID craftsmanId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM craftsman_recommendation WHERE craftsman_id = :craftsmanId AND user_id = :userId",
            nativeQuery = true)
    int unrecommend(@Param("craftsmanId") UUID craftsmanId, @Param("userId") UUID userId);
}
