package com.entry.feed;

import com.entry.common.exception.ApiException;
import com.entry.common.web.Cursor;
import com.entry.common.web.CursorPage;
import com.entry.feed.dto.CommentDto;
import com.entry.feed.dto.CreateCommentRequest;
import com.entry.feed.dto.CreatePostRequest;
import com.entry.feed.dto.LikeResponse;
import com.entry.feed.dto.PostDto;
import com.entry.feed.event.CommentCreatedEvent;
import com.entry.feed.event.PostCreatedEvent;
import com.entry.feed.event.PostDeletedEvent;
import com.entry.feed.event.PostLikedEvent;
import com.entry.feed.event.PostStatsChangedEvent;
import com.entry.file.FileService;
import com.entry.file.StoredFile;
import com.entry.file.dto.FileDto;
import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserMapper;
import com.entry.user.UserRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int DEFAULT_PAGE = 20;
    private static final int MAX_PAGE = 50;
    private static final int MAX_COMMENTS = 500;

    private final PostRepository posts;
    private final PostImageRepository postImages;
    private final CommentRepository comments;
    private final UserRepository users;
    private final FileService fileService;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    // ---------- posts ----------

    /** Newest first. With {@code authorId}, only that resident's posts (used on profile pages). */
    @Transactional(readOnly = true)
    public CursorPage<PostDto> feed(AuthUser me, UUID authorId, String cursor, Integer size) {
        Cursor c = Cursor.decode(cursor);
        int pageSize = Cursor.pageSize(size, DEFAULT_PAGE, MAX_PAGE);
        PageRequest limit = PageRequest.of(0, pageSize + 1); // one extra row tells us if there is a next page
        List<Post> rows = authorId == null
                ? posts.findFeedPage(me.entranceId(), c.createdAt(), c.id(), limit)
                : posts.findAuthorPage(me.entranceId(), authorId, c.createdAt(), c.id(), limit);

        boolean hasMore = rows.size() > pageSize;
        List<Post> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore ? new Cursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode() : null;
        return new CursorPage<>(toDtos(me, page), next);
    }

    @Transactional(readOnly = true)
    public PostDto get(AuthUser me, UUID postId) {
        return toDtos(me, List.of(load(me, postId))).getFirst();
    }

    @Transactional
    public PostDto create(AuthUser me, CreatePostRequest req) {
        List<StoredFile> files = fileService.requireOwnFiles(me, req.imageIdsOrEmpty());

        Post post = new Post();
        post.setEntranceId(me.entranceId());
        post.setAuthor(users.getReferenceById(me.id()));
        post.setText(req.text() == null || req.text().isBlank() ? null : req.text().trim());
        post = posts.save(post);

        for (int i = 0; i < files.size(); i++) {
            postImages.save(new PostImage(post.getId(), files.get(i), i));
        }
        posts.flush();

        PostDto dto = new PostDto(post.getId(), userMapper.toResident(users.getReferenceById(me.id())), post.getText(),
                files.stream().map(fileService::toDto).toList(), 0, 0, false, post.getCreatedAt());
        events.publishEvent(new PostCreatedEvent(me.entranceId(), dto));
        return dto;
    }

    /** Authors can delete their own posts; entrance admins can delete any post in their entrance. */
    @Transactional
    public void delete(AuthUser me, UUID postId) {
        Post post = load(me, postId);
        requireAuthorOrAdmin(me, post.getAuthor());
        List<PostImage> images = postImages.findWithFilesByPostIds(List.of(postId));
        List<UUID> fileIds = images.stream().map(pi -> pi.getFile().getId()).toList();
        postImages.deleteAll(images); // explicit, so Hibernate doesn't keep references to the files we delete next
        posts.delete(post);
        posts.flush();
        fileIds.forEach(fileService::delete);
        events.publishEvent(new PostDeletedEvent(me.entranceId(), postId));
    }

    // ---------- likes ----------

    @Transactional
    public LikeResponse like(AuthUser me, UUID postId) {
        UUID postAuthorId = load(me, postId).getAuthor().getId();
        if (posts.insertLike(postId, me.id()) == 1) {
            posts.addToLikeCount(postId, 1);
            publishStats(me, postId);
            events.publishEvent(new PostLikedEvent(me.entranceId(), postId, postAuthorId, me.id()));
        }
        return new LikeResponse(postId, posts.likeCount(postId), true);
    }

    @Transactional
    public LikeResponse unlike(AuthUser me, UUID postId) {
        load(me, postId);
        if (posts.deleteLike(postId, me.id()) == 1) {
            posts.addToLikeCount(postId, -1);
            publishStats(me, postId);
        }
        return new LikeResponse(postId, posts.likeCount(postId), false);
    }

    // ---------- comments ----------

    @Transactional(readOnly = true)
    public List<CommentDto> comments(AuthUser me, UUID postId) {
        load(me, postId);
        return comments.findByPostIdOrderByCreatedAtAscIdAsc(postId, PageRequest.of(0, MAX_COMMENTS)).stream()
                .map(this::toDto).toList();
    }

    @Transactional
    public CommentDto addComment(AuthUser me, UUID postId, CreateCommentRequest req) {
        Post post = load(me, postId);
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthor(users.getReferenceById(me.id()));
        comment.setText(req.text().trim());
        comment = comments.save(comment);
        // Build everything that touches entities BEFORE the counter update clears the persistence context.
        CommentDto dto = toDto(comment);
        UUID postAuthorId = post.getAuthor().getId();
        posts.addToCommentCount(postId, 1);

        events.publishEvent(new CommentCreatedEvent(me.entranceId(), postAuthorId, dto));
        publishStats(me, postId);
        return dto;
    }

    @Transactional
    public void deleteComment(AuthUser me, UUID postId, UUID commentId) {
        load(me, postId);
        Comment comment = comments.findById(commentId)
                .filter(c -> c.getPostId().equals(postId))
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        requireAuthorOrAdmin(me, comment.getAuthor());
        comments.delete(comment);
        comments.flush();
        posts.addToCommentCount(postId, -1);
        publishStats(me, postId);
    }

    // ---------- helpers ----------

    private Post load(AuthUser me, UUID postId) {
        return posts.findByIdAndEntranceId(postId, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Post not found"));
    }

    private static void requireAuthorOrAdmin(AuthUser me, User author) {
        if (!author.getId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("NOT_AUTHOR", "Only the author or an entrance admin can do this");
        }
    }

    private void publishStats(AuthUser me, UUID postId) {
        events.publishEvent(new PostStatsChangedEvent(me.entranceId(), postId, posts.likeCount(postId),
                posts.commentCount(postId)));
    }

    /** Builds DTOs for a page with two batch queries (images, my likes) instead of N+1. */
    private List<PostDto> toDtos(AuthUser me, List<Post> page) {
        if (page.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = page.stream().map(Post::getId).toList();
        Map<UUID, List<FileDto>> images = new LinkedHashMap<>();
        for (PostImage pi : postImages.findWithFilesByPostIds(ids)) {
            images.computeIfAbsent(pi.getPostId(), k -> new ArrayList<>()).add(fileService.toDto(pi.getFile()));
        }
        Set<UUID> liked = new HashSet<>(posts.findLikedPostIds(me.id(), ids));

        return page.stream().map(p -> new PostDto(
                p.getId(),
                userMapper.toResident(p.getAuthor()),
                p.getText(),
                images.getOrDefault(p.getId(), List.of()),
                p.getLikeCount(),
                p.getCommentCount(),
                liked.contains(p.getId()),
                p.getCreatedAt())).collect(Collectors.toList());
    }

    private CommentDto toDto(Comment c) {
        return new CommentDto(c.getId(), c.getPostId(), userMapper.toResident(c.getAuthor()), c.getText(),
                c.getCreatedAt());
    }
}
