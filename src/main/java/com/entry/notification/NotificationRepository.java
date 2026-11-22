package com.entry.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @EntityGraph(attributePaths = "actor")
    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (n.createdAt < :ts or (n.createdAt = :ts and n.id < :id))
            order by n.createdAt desc, n.id desc""")
    List<Notification> findPage(@Param("userId") UUID userId, @Param("ts") Instant ts, @Param("id") UUID id,
            Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    Optional<Notification> findFirstByUserIdAndTypeAndTargetIdAndReadAtIsNull(UUID userId, NotificationType type,
            UUID targetId);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
