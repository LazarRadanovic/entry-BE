package com.entry.announcement;

import com.entry.announcement.dto.AnnouncementDto;
import com.entry.announcement.dto.AnnouncementRequest;
import com.entry.announcement.dto.AnnouncementStatus;
import com.entry.announcement.event.AnnouncementCreatedEvent;
import com.entry.announcement.event.AnnouncementDeletedEvent;
import com.entry.announcement.event.AnnouncementUpdatedEvent;
import com.entry.common.exception.ApiException;
import com.entry.security.AuthUser;
import com.entry.user.UserMapper;
import com.entry.user.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Noise announcements ("I'm renovating / having a celebration"). Creating one notifies every neighbour; edits and
 * deletes are only broadcast live. A resident can have a few active announcements at a time, so it can't be used
 * to spam the entrance with pushes.
 */
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    /** Dates are calendar days where the buildings are. */
    static final ZoneId ZONE = ZoneId.of("Europe/Belgrade");
    static final int MAX_ACTIVE_PER_AUTHOR = 2;
    private static final int MAX_DAYS = 31;
    private static final int MAX_DAYS_AHEAD = 90;
    private static final int LIST_LIMIT = 100;

    private final AnnouncementRepository announcements;
    private final UserRepository users;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<AnnouncementDto> list(AuthUser me, AnnouncementStatus status) {
        LocalDate today = today();
        PageRequest limit = PageRequest.of(0, LIST_LIMIT);
        List<Announcement> rows = status == AnnouncementStatus.PAST
                ? announcements.findPast(me.entranceId(), today, limit)
                : announcements.findActive(me.entranceId(), today, limit);
        return rows.stream().map(a -> toDto(a, today)).toList();
    }

    @Transactional(readOnly = true)
    public AnnouncementDto get(AuthUser me, UUID id) {
        return toDto(load(me, id), today());
    }

    @Transactional
    public AnnouncementDto create(AuthUser me, AnnouncementRequest req) {
        LocalDate today = today();
        validate(req, today, null);
        if (announcements.countByAuthor_IdAndEndDateGreaterThanEqual(me.id(), today) >= MAX_ACTIVE_PER_AUTHOR) {
            throw ApiException.conflict("TOO_MANY_ANNOUNCEMENTS",
                    "You can have at most " + MAX_ACTIVE_PER_AUTHOR + " active announcements");
        }
        Announcement a = new Announcement();
        a.setEntranceId(me.entranceId());
        a.setAuthor(users.getReferenceById(me.id()));
        apply(a, req);
        a = announcements.saveAndFlush(a);

        AnnouncementDto dto = toDto(a, today);
        events.publishEvent(new AnnouncementCreatedEvent(me.entranceId(), dto));
        return dto;
    }

    /** Author or entrance admin. An announcement that already started may keep its start date. */
    @Transactional
    public AnnouncementDto update(AuthUser me, UUID id, AnnouncementRequest req) {
        Announcement a = loadForChange(me, id);
        LocalDate today = today();
        validate(req, today, a.getStartDate());
        apply(a, req);
        AnnouncementDto dto = toDto(a, today);
        events.publishEvent(new AnnouncementUpdatedEvent(me.entranceId(), dto));
        return dto;
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Announcement a = loadForChange(me, id);
        announcements.delete(a);
        events.publishEvent(new AnnouncementDeletedEvent(me.entranceId(), id));
    }

    static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    private static void validate(AnnouncementRequest req, LocalDate today, LocalDate currentStart) {
        boolean keepsStart = req.startDate().equals(currentStart);
        if (req.startDate().isBefore(today) && !keepsStart) {
            throw ApiException.badRequest("START_IN_PAST", "The start date can't be in the past");
        }
        if (req.endDate().isBefore(req.startDate())) {
            throw ApiException.badRequest("END_BEFORE_START", "The end date must be on or after the start date");
        }
        if (req.endDate().isBefore(today)) {
            throw ApiException.badRequest("END_IN_PAST", "The end date can't be in the past");
        }
        if (ChronoUnit.DAYS.between(req.startDate(), req.endDate()) >= MAX_DAYS) {
            throw ApiException.badRequest("TOO_LONG", "An announcement can cover at most " + MAX_DAYS + " days");
        }
        if (req.startDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest("START_TOO_FAR",
                    "Announce at most " + MAX_DAYS_AHEAD + " days in advance");
        }
        if ((req.fromTime() == null) != (req.toTime() == null)) {
            throw ApiException.badRequest("INCOMPLETE_HOURS", "Give both the start and end hour, or neither");
        }
        if (req.fromTime() != null && req.fromTime().equals(req.toTime())) {
            throw ApiException.badRequest("INVALID_HOURS", "The start and end hour can't be the same");
        }
    }

    private static void apply(Announcement a, AnnouncementRequest req) {
        a.setKind(req.kind());
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setFromTime(req.fromTime());
        a.setToTime(req.toTime());
        a.setNote(req.note() == null || req.note().isBlank() ? null : req.note().strip());
    }

    private Announcement load(AuthUser me, UUID id) {
        return announcements.findByIdAndEntranceId(id, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Announcement not found"));
    }

    private Announcement loadForChange(AuthUser me, UUID id) {
        Announcement a = load(me, id);
        if (!a.getAuthor().getId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("NOT_AUTHOR", "Only the author or an entrance admin can do this");
        }
        return a;
    }

    private AnnouncementDto toDto(Announcement a, LocalDate today) {
        boolean ongoing = !today.isBefore(a.getStartDate()) && !today.isAfter(a.getEndDate());
        return new AnnouncementDto(a.getId(), userMapper.toResident(a.getAuthor()), a.getKind(), a.getStartDate(),
                a.getEndDate(), a.getFromTime(), a.getToTime(), a.getNote(), ongoing, a.getCreatedAt());
    }
}
