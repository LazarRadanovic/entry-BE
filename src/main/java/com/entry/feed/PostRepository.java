package com.entry.feed;

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

public interface PostRepository extends JpaRepository<Post, UUID> {

    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Post p
            where p.entranceId = :entranceId
              and (p.createdAt < :ts or (p.createdAt = :ts and p.id < :id))
            order by p.createdAt desc, p.id desc""")
    List<Post> findFeedPage(@Param("entranceId") UUID entranceId, @Param("ts") Instant ts, @Param("id") UUID id,
            Pageable pageable);

    @EntityGraph(attributePaths = "author")
    @Query("""
            select p from Post p
            where p.entranceId = :entranceId and p.author.id = :authorId
              and (p.createdAt < :ts or (p.createdAt = :ts and p.id < :id))
            order by p.createdAt desc, p.id desc""")
    List<Post> findAuthorPage(@Param("entranceId") UUID entranceId, @Param("authorId") UUID authorId,
            @Param("ts") Instant ts, @Param("id") UUID id, Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Optional<Post> findByIdAndEntranceId(UUID id, UUID entranceId);

    // ---- likes (no entity: a pure join table) ----

    @Modifying
    @Query(value = "INSERT INTO post_like (post_id, user_id) VALUES (:postId, :userId) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertLike(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Modifying
    @Query(value = "DELETE FROM post_like WHERE post_id = :postId AND user_id = :userId", nativeQuery = true)
    int deleteLike(@Param("postId") UUID postId, @Param("userId") UUID userId);

    @Query(value = "SELECT post_id FROM post_like WHERE user_id = :userId AND post_id IN (:postIds)",
            nativeQuery = true)
    List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);

    // ---- counters ----

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :id")
    void addToLikeCount(@Param("id") UUID id, @Param("delta") int delta);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Post p set p.commentCount = p.commentCount + :delta where p.id = :id")
    void addToCommentCount(@Param("id") UUID id, @Param("delta") int delta);

    @Query("select p.likeCount from Post p where p.id = :id")
    int likeCount(@Param("id") UUID id);

    @Query("select p.commentCount from Post p where p.id = :id")
    int commentCount(@Param("id") UUID id);
}
