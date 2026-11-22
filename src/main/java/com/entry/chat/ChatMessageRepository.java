package com.entry.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @EntityGraph(attributePaths = {"sender", "image"})
    @Query("""
            select m from ChatMessage m
            where m.entranceId = :entranceId
              and (m.createdAt < :ts or (m.createdAt = :ts and m.id < :id))
            order by m.createdAt desc, m.id desc""")
    List<ChatMessage> findPage(@Param("entranceId") UUID entranceId, @Param("ts") Instant ts, @Param("id") UUID id,
            Pageable pageable);
}
