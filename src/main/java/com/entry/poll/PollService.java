package com.entry.poll;

import com.entry.common.exception.ApiException;
import com.entry.poll.dto.CreatePollRequest;
import com.entry.poll.dto.PollDto;
import com.entry.poll.dto.PollResults;
import com.entry.poll.dto.PollStatus;
import com.entry.poll.event.PollCreatedEvent;
import com.entry.poll.event.PollDeletedEvent;
import com.entry.poll.event.PollResultsChangedEvent;
import com.entry.security.AuthUser;
import com.entry.user.UserMapper;
import com.entry.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PollService {

    private static final int LIST_LIMIT = 100;
    private static final Duration MAX_DURATION = Duration.ofDays(366);

    private final PollRepository polls;
    private final UserRepository users;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<PollDto> list(AuthUser me, PollStatus status) {
        Instant now = Instant.now();
        PageRequest limit = PageRequest.of(0, LIST_LIMIT);
        List<Poll> rows = status == PollStatus.EXPIRED
                ? polls.findExpired(me.entranceId(), now, limit)
                : polls.findActive(me.entranceId(), now, limit);
        return toDtos(me, rows, now);
    }

    @Transactional(readOnly = true)
    public PollDto get(AuthUser me, UUID pollId) {
        return toDtos(me, List.of(load(me, pollId)), Instant.now()).getFirst();
    }

    @Transactional
    public PollDto create(AuthUser me, CreatePollRequest req) {
        Instant now = Instant.now();
        if (req.expiresAt() != null && req.expiresAt().isAfter(now.plus(MAX_DURATION))) {
            throw ApiException.badRequest("EXPIRY_TOO_FAR", "A poll can run for at most one year");
        }
        Poll poll = new Poll();
        poll.setEntranceId(me.entranceId());
        poll.setAuthor(users.getReferenceById(me.id()));
        poll.setQuestion(req.question().trim());
        poll.setExpiresAt(req.expiresAt());
        poll = polls.saveAndFlush(poll);

        PollDto dto = new PollDto(poll.getId(), userMapper.toResident(poll.getAuthor()), poll.getQuestion(),
                poll.getExpiresAt(), false, PollResults.of(poll.getId(), 0, 0), null, poll.getCreatedAt());
        events.publishEvent(new PollCreatedEvent(me.entranceId(), me.id(), dto));
        return dto;
    }

    /** Votes can be changed until the poll expires. */
    @Transactional
    public PollDto vote(AuthUser me, UUID pollId, boolean value) {
        Poll poll = load(me, pollId);
        Instant now = Instant.now();
        if (poll.isExpired(now)) {
            throw ApiException.conflict("POLL_EXPIRED", "This poll has ended");
        }
        polls.upsertVote(pollId, me.id(), value);
        PollDto dto = toDtos(me, List.of(poll), now).getFirst();
        events.publishEvent(new PollResultsChangedEvent(me.entranceId(), dto.results()));
        return dto;
    }

    @Transactional
    public PollDto retractVote(AuthUser me, UUID pollId) {
        Poll poll = load(me, pollId);
        Instant now = Instant.now();
        if (poll.isExpired(now)) {
            throw ApiException.conflict("POLL_EXPIRED", "This poll has ended");
        }
        boolean changed = polls.deleteVote(pollId, me.id()) == 1;
        PollDto dto = toDtos(me, List.of(poll), now).getFirst();
        if (changed) {
            events.publishEvent(new PollResultsChangedEvent(me.entranceId(), dto.results()));
        }
        return dto;
    }

    /** Authors can delete their own polls; entrance admins can delete any poll in their entrance. */
    @Transactional
    public void delete(AuthUser me, UUID pollId) {
        Poll poll = load(me, pollId);
        if (!poll.getAuthor().getId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("NOT_AUTHOR", "Only the author or an entrance admin can do this");
        }
        polls.delete(poll);
        events.publishEvent(new PollDeletedEvent(me.entranceId(), pollId));
    }

    private Poll load(AuthUser me, UUID pollId) {
        return polls.findByIdAndEntranceId(pollId, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Poll not found"));
    }

    private List<PollDto> toDtos(AuthUser me, List<Poll> rows, Instant now) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(Poll::getId).toList();
        Map<UUID, PollRepository.VoteCounts> counts = polls.countVotes(ids).stream()
                .collect(Collectors.toMap(PollRepository.VoteCounts::getPollId, Function.identity()));
        Map<UUID, Boolean> mine = polls.findMyVotes(me.id(), ids).stream()
                .collect(Collectors.toMap(PollRepository.MyVote::getPollId, PollRepository.MyVote::getValue));

        return rows.stream().map(p -> {
            PollRepository.VoteCounts c = counts.get(p.getId());
            PollResults results = c == null
                    ? PollResults.of(p.getId(), 0, 0)
                    : PollResults.of(p.getId(), c.getYesCount(), c.getNoCount());
            return new PollDto(p.getId(), userMapper.toResident(p.getAuthor()), p.getQuestion(), p.getExpiresAt(),
                    p.isExpired(now), results, mine.get(p.getId()), p.getCreatedAt());
        }).toList();
    }
}
