package com.entry.lostfound;

import com.entry.common.exception.ApiException;
import com.entry.file.FileService;
import com.entry.file.StoredFile;
import com.entry.file.StoredFileRepository;
import com.entry.lostfound.dto.LostFoundDto;
import com.entry.lostfound.dto.LostFoundRequest;
import com.entry.lostfound.dto.LostFoundStatus;
import com.entry.lostfound.event.LostFoundCreatedEvent;
import com.entry.lostfound.event.LostFoundDeletedEvent;
import com.entry.lostfound.event.LostFoundRespondedEvent;
import com.entry.lostfound.event.LostFoundUpdatedEvent;
import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserMapper;
import com.entry.user.UserRepository;
import com.entry.user.dto.ResidentDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lost & found of an entrance. Anyone posts; the author or an admin edits, resolves and deletes. Neighbours answer
 * "it's mine" / "I found it", which tells the author who to talk to.
 */
@Service
@RequiredArgsConstructor
public class LostFoundService {

    private static final int LIST_LIMIT = 100;

    private final LostFoundRepository items;
    private final StoredFileRepository storedFiles;
    private final FileService files;
    private final UserRepository users;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<LostFoundDto> list(AuthUser me, LostFoundStatus status, LostFoundKind kind) {
        PageRequest limit = PageRequest.of(0, LIST_LIMIT);
        List<LostFoundItem> rows = status == LostFoundStatus.RESOLVED
                ? items.findResolved(me.entranceId(), kind, limit)
                : items.findOpen(me.entranceId(), kind, limit);
        return toDtos(rows);
    }

    @Transactional(readOnly = true)
    public LostFoundDto get(AuthUser me, UUID id) {
        return toDto(find(me, id));
    }

    @Transactional
    public LostFoundDto create(AuthUser me, LostFoundRequest req) {
        LostFoundItem item = new LostFoundItem();
        item.setEntranceId(me.entranceId());
        item.setAuthor(users.getReferenceById(me.id()));
        apply(me, item, req);
        item = items.saveAndFlush(item);
        LostFoundDto dto = toDto(item);
        events.publishEvent(new LostFoundCreatedEvent(me.entranceId(), dto));
        return dto;
    }

    @Transactional
    public LostFoundDto update(AuthUser me, UUID id, LostFoundRequest req) {
        LostFoundItem item = editable(me, id);
        apply(me, item, req);
        return changed(me, item);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        LostFoundItem item = editable(me, id);
        UUID photo = item.getPhotoFileId();
        items.delete(item);
        items.flush();
        if (photo != null) {
            files.delete(photo);
        }
        events.publishEvent(new LostFoundDeletedEvent(me.entranceId(), id));
    }

    /** Not on your own post, and only while it's open. The author is told once per neighbour. */
    @Transactional
    public LostFoundDto respond(AuthUser me, UUID id, boolean respond) {
        LostFoundItem item = find(me, id);
        if (item.getAuthor().getId().equals(me.id())) {
            throw ApiException.badRequest("OWN_ITEM", "You can't respond to your own post");
        }
        if (item.getResolvedAt() != null) {
            throw ApiException.conflict("ITEM_RESOLVED", "This item is already resolved");
        }
        if (respond) {
            if (items.addResponse(id, me.id()) == 1) {
                events.publishEvent(new LostFoundRespondedEvent(id, item.getAuthor().getId(), me.id(),
                        item.getKind(), item.getTitle()));
            }
        } else {
            items.removeResponse(id, me.id());
        }
        return changed(me, item);
    }

    @Transactional
    public LostFoundDto setResolved(AuthUser me, UUID id, boolean resolved) {
        LostFoundItem item = editable(me, id);
        if (resolved != (item.getResolvedAt() != null)) {
            item.setResolvedAt(resolved ? Instant.now() : null);
        }
        return changed(me, item);
    }

    private LostFoundDto changed(AuthUser me, LostFoundItem item) {
        items.flush();
        LostFoundDto dto = toDto(item);
        events.publishEvent(new LostFoundUpdatedEvent(me.entranceId(), dto));
        return dto;
    }

    private void apply(AuthUser me, LostFoundItem item, LostFoundRequest r) {
        item.setKind(r.kind());
        item.setCategory(r.category());
        item.setTitle(r.title().strip());
        item.setDescription(blankToNull(r.description()));
        item.setLocation(blankToNull(r.location()));
        UUID previous = item.getPhotoFileId();
        if (!Objects.equals(previous, r.photoFileId())) {
            if (r.photoFileId() != null) {
                files.requireOwnFiles(me, List.of(r.photoFileId()));
            }
            item.setPhotoFileId(r.photoFileId());
            if (previous != null) {
                items.flush();
                files.delete(previous);
            }
        }
    }

    private LostFoundItem find(AuthUser me, UUID id) {
        return items.findByIdAndEntranceId(id, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Item not found"));
    }

    private LostFoundItem editable(AuthUser me, UUID id) {
        LostFoundItem item = find(me, id);
        if (!item.getAuthor().getId().equals(me.id()) && !me.isAdmin()) {
            throw ApiException.forbidden("NOT_AUTHOR", "Only the author or an entrance admin can do this");
        }
        return item;
    }

    private LostFoundDto toDto(LostFoundItem item) {
        return toDtos(List.of(item)).getFirst();
    }

    private List<LostFoundDto> toDtos(List<LostFoundItem> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rows.stream().map(LostFoundItem::getId).toList();
        List<LostFoundRepository.ResponseRow> responses = items.findResponses(ids);
        Map<UUID, User> responders = users.findAllById(
                        responses.stream().map(LostFoundRepository.ResponseRow::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, List<ResidentDto>> byItem = responses.stream()
                .filter(r -> responders.containsKey(r.getUserId()))
                .collect(Collectors.groupingBy(LostFoundRepository.ResponseRow::getItemId,
                        Collectors.mapping(r -> userMapper.toResident(responders.get(r.getUserId())),
                                Collectors.toList())));
        Map<UUID, StoredFile> photos = storedFiles.findAllById(
                        rows.stream().map(LostFoundItem::getPhotoFileId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(StoredFile::getId, Function.identity()));

        return rows.stream().map(i -> {
            StoredFile photo = photos.get(i.getPhotoFileId());
            return new LostFoundDto(i.getId(), userMapper.toResident(i.getAuthor()), i.getKind(), i.getCategory(),
                    i.getTitle(), i.getDescription(), i.getLocation(), photo == null ? null : files.toDto(photo),
                    byItem.getOrDefault(i.getId(), List.of()), i.getResolvedAt(), i.getCreatedAt());
        }).toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
