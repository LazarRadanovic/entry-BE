package com.entry.feed;

import com.entry.common.web.CursorPage;
import com.entry.feed.dto.CommentDto;
import com.entry.feed.dto.CreateCommentRequest;
import com.entry.feed.dto.CreatePostRequest;
import com.entry.feed.dto.LikeResponse;
import com.entry.feed.dto.PostDto;
import com.entry.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public CursorPage<PostDto> feed(@AuthenticationPrincipal AuthUser me,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return postService.feed(me, authorId, cursor, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreatePostRequest request) {
        return postService.create(me, request);
    }

    @GetMapping("/{id}")
    public PostDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return postService.get(me, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        postService.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/like")
    public LikeResponse like(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return postService.like(me, id);
    }

    @DeleteMapping("/{id}/like")
    public LikeResponse unlike(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return postService.unlike(me, id);
    }

    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return postService.comments(me, id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody CreateCommentRequest request) {
        return postService.addComment(me, id, request);
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @PathVariable UUID commentId) {
        postService.deleteComment(me, id, commentId);
        return ResponseEntity.noContent().build();
    }
}
