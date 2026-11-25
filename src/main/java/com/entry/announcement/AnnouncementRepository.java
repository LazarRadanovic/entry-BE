package com.entry.announcement;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {

    /** Ongoing and upcoming, soonest first. */
    @EntityGraph(attributePaths = "author")
    @Query("""
            select a from Announcement a
            where a.entranceId = :entranceId and a.endDate >= :today
            order by a.startDate asc, a.createdAt asc""")
    List<Announcement> findActive(@Param("entranceId") UUID entranceId, @Param("today") LocalDate today,
            Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
            select a from Announcement a
            where a.entranceId = :entranceId and a.endDate < :today
            order by a.endDate desc, a.createdAt desc""")
    List<Announcement> findPast(@Param("entranceId") UUID entranceId, @Param("today") LocalDate today,
            Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<Announcement> findByIdAndEntranceId(UUID id, UUID entranceId);

    long countByAuthor_IdAndEndDateGreaterThanEqual(UUID authorId, LocalDate today);
}
