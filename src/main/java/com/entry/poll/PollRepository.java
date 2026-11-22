package com.entry.poll;

import java.time.Instant;
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

public interface PollRepository extends JpaRepository<Poll, UUID> {

    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Poll p
            where p.entranceId = :entranceId and (p.expiresAt is null or p.expiresAt > :now)
            order by p.createdAt desc""")
    List<Poll> findActive(@Param("entranceId") UUID entranceId, @Param("now") Instant now, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Poll p
            where p.entranceId = :entranceId and p.expiresAt <= :now
            order by p.expiresAt desc""")
    List<Poll> findExpired(@Param("entranceId") UUID entranceId, @Param("now") Instant now, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<Poll> findByIdAndEntranceId(UUID id, UUID entranceId);

    // ---- votes (pure join table, no entity) ----

    @Modifying
    @Query(value = """
            INSERT INTO poll_vote (poll_id, user_id, value) VALUES (:pollId, :userId, :value)
            ON CONFLICT (poll_id, user_id) DO UPDATE SET value = EXCLUDED.value, updated_at = now()""",
            nativeQuery = true)
    void upsertVote(@Param("pollId") UUID pollId, @Param("userId") UUID userId, @Param("value") boolean value);

    @Modifying
    @Query(value = "DELETE FROM poll_vote WHERE poll_id = :pollId AND user_id = :userId", nativeQuery = true)
    int deleteVote(@Param("pollId") UUID pollId, @Param("userId") UUID userId);

    interface VoteCounts {
        UUID getPollId();

        long getYesCount();

        long getNoCount();
    }

    @Query(value = """
            SELECT poll_id AS "pollId",
                   COUNT(*) FILTER (WHERE value)     AS "yesCount",
                   COUNT(*) FILTER (WHERE NOT value) AS "noCount"
            FROM poll_vote WHERE poll_id IN (:pollIds) GROUP BY poll_id""", nativeQuery = true)
    List<VoteCounts> countVotes(@Param("pollIds") Collection<UUID> pollIds);

    interface MyVote {
        UUID getPollId();

        boolean getValue();
    }

    @Query(value = """
            SELECT poll_id AS "pollId", value AS "value"
            FROM poll_vote WHERE user_id = :userId AND poll_id IN (:pollIds)""", nativeQuery = true)
    List<MyVote> findMyVotes(@Param("userId") UUID userId, @Param("pollIds") Collection<UUID> pollIds);
}
