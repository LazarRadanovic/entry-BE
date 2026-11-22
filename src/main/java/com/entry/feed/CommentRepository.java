package com.entry.feed;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @EntityGraph(attributePaths = "author")
    List<Comment> findByPostIdOrderByCreatedAtAscIdAsc(UUID postId, Pageable pageable);
}
