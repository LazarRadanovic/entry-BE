package com.entry.feed;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostImageRepository extends JpaRepository<PostImage, UUID> {

    @Query("select pi from PostImage pi join fetch pi.file where pi.postId in :postIds order by pi.position")
    List<PostImage> findWithFilesByPostIds(@Param("postIds") Collection<UUID> postIds);
}
