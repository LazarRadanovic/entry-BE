package com.entry.poll;

import com.entry.poll.dto.CreatePollRequest;
import com.entry.poll.dto.PollDto;
import com.entry.poll.dto.PollStatus;
import com.entry.poll.dto.VoteRequest;
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
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @GetMapping
    public List<PollDto> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(defaultValue = "ACTIVE") PollStatus status) {
        return pollService.list(me, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PollDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreatePollRequest request) {
        return pollService.create(me, request);
    }

    @GetMapping("/{id}")
    public PollDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return pollService.get(me, id);
    }

    @PutMapping("/{id}/vote")
    public PollDto vote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody VoteRequest request) {
        return pollService.vote(me, id, request.value());
    }

    @DeleteMapping("/{id}/vote")
    public PollDto retractVote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return pollService.retractVote(me, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        pollService.delete(me, id);
        return ResponseEntity.noContent().build();
    }
}
