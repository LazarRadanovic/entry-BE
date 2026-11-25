package com.entry.lostfound;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LostFoundRepository extends JpaRepository<LostFoundItem, UUID> {

    @EntityGraph(attributePaths = "author")
    @Query("""
            select i from LostFoundItem i
            where i.entranceId = :entranceId and i.resolvedAt is null
              and (:kind is null or i.kind = :kind)
            order by i.createdAt desc""")
    List<LostFoundItem> findOpen(@Param("entranceId") UUID entranceId, @Param("kind") LostFoundKind kind,
            Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
            select i from LostFoundItem i
            where i.entranceId = :entranceId and i.resolvedAt is not null
              and (:kind is null or i.kind = :kind)
            order by i.resolvedAt desc""")
    List<LostFoundItem> findResolved(@Param("entranceId") UUID entranceId, @Param("kind") LostFoundKind kind,
            Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<LostFoundItem> findByIdAndEntranceId(UUID id, UUID entranceId);

    // ---- responses (pure join table, no entity) ----

    /** Returns 1 when this is the neighbour's first response, 0 when they already responded. */
    @Modifying
    @Query(value = """
            INSERT INTO lost_found_response (item_id, user_id) VALUES (:itemId, :userId)
            ON CONFLICT DO NOTHING""", nativeQuery = true)
    int addResponse(@Param("itemId") UUID itemId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM lost_found_response WHERE item_id = :itemId AND user_id = :userId",
            nativeQuery = true)
    int removeResponse(@Param("itemId") UUID itemId, @Param("userId") UUID userId);

    interface ResponseRow {
        UUID getItemId();

        UUID getUserId();
    }

    @Query(value = """
            SELECT item_id AS "itemId", user_id AS "userId" FROM lost_found_response
            WHERE item_id IN (:itemIds) ORDER BY created_at""", nativeQuery = true)
    List<ResponseRow> findResponses(@Param("itemIds") Collection<UUID> itemIds);
}
