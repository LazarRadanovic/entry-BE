package com.entry.craftsman;

import com.entry.common.exception.ApiException;
import com.entry.craftsman.dto.CraftsmanDto;
import com.entry.craftsman.dto.CraftsmanRequest;
import com.entry.craftsman.dto.RecommendResponse;
import com.entry.file.FileService;
import com.entry.file.StoredFile;
import com.entry.file.StoredFileRepository;
import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Craftsmen directory of an entrance: anyone adds, the author or an admin edits, neighbours recommend. */
@Service
@RequiredArgsConstructor
public class CraftsmanService {

    private final CraftsmanRepository craftsmen;
    private final StoredFileRepository storedFiles;
    private final FileService files;
    private final UserRepository users;

    /** Most recommended first, then by name. */
    @Transactional(readOnly = true)
    public List<CraftsmanDto> list(AuthUser me, Trade trade) {
        List<Craftsman> list = trade == null
                ? craftsmen.findByEntranceId(me.entranceId())
                : craftsmen.findByEntranceIdAndTrade(me.entranceId(), trade);
        if (list.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = list.stream().map(Craftsman::getId).toList();
        Map<UUID, Long> counts = craftsmen.recommendationCounts(ids).stream()
                .collect(Collectors.toMap(CraftsmanRepository.CountRow::getCraftsmanId,
                        CraftsmanRepository.CountRow::getCount));
        Set<UUID> mine = new HashSet<>(craftsmen.recommendedBy(me.id(), ids));
        Map<UUID, StoredFile> photos = storedFiles.findAllById(
                        list.stream().map(Craftsman::getPhotoFileId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(StoredFile::getId, Function.identity()));
        Map<UUID, User> authors = users.findAllById(
                        list.stream().map(Craftsman::getCreatedBy).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return list.stream()
                .map(c -> toDto(me, c, counts.getOrDefault(c.getId(), 0L), mine.contains(c.getId()),
                        photos.get(c.getPhotoFileId()), authors.get(c.getCreatedBy())))
                .sorted(Comparator.comparingLong(CraftsmanDto::recommendations).reversed()
                        .thenComparing(CraftsmanDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public CraftsmanDto create(AuthUser me, CraftsmanRequest r) {
        Craftsman c = new Craftsman();
        c.setEntranceId(me.entranceId());
        c.setCreatedBy(me.id());
        apply(me, c, r);
        craftsmen.save(c);
        return dto(me, c);
    }

    @Transactional
    public CraftsmanDto update(AuthUser me, UUID id, CraftsmanRequest r) {
        Craftsman c = editable(me, id);
        apply(me, c, r);
        return dto(me, c);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Craftsman c = editable(me, id);
        UUID photo = c.getPhotoFileId();
        craftsmen.delete(c);
        craftsmen.flush();
        if (photo != null) {
            files.delete(photo);
        }
    }

    @Transactional
    public RecommendResponse recommend(AuthUser me, UUID id, boolean recommend) {
        Craftsman c = find(me, id);
        if (recommend) {
            craftsmen.recommend(c.getId(), me.id());
        } else {
            craftsmen.unrecommend(c.getId(), me.id());
        }
        long count = craftsmen.recommendationCounts(List.of(c.getId())).stream()
                .mapToLong(CraftsmanRepository.CountRow::getCount).sum();
        return new RecommendResponse(c.getId(), count, recommend);
    }

    private void apply(AuthUser me, Craftsman c, CraftsmanRequest r) {
        c.setName(r.name().trim());
        c.setTrade(r.trade());
        c.setPhone(r.phone().trim());
        c.setEmail(blankToNull(r.email()));
        c.setWebsite(blankToNull(r.website()));
        c.setDescription(blankToNull(r.description()));
        UUID previous = c.getPhotoFileId();
        if (!Objects.equals(previous, r.photoFileId())) {
            if (r.photoFileId() != null) {
                files.requireOwnFiles(me, List.of(r.photoFileId()));
            }
            c.setPhotoFileId(r.photoFileId());
            if (previous != null) {
                craftsmen.flush();
                files.delete(previous);
            }
        }
    }

    private CraftsmanDto dto(AuthUser me, Craftsman c) {
        StoredFile photo = c.getPhotoFileId() == null ? null : storedFiles.findById(c.getPhotoFileId()).orElse(null);
        User author = c.getCreatedBy() == null ? null : users.findById(c.getCreatedBy()).orElse(null);
        long count = c.getId() == null ? 0 : craftsmen.recommendationCounts(List.of(c.getId())).stream()
                .mapToLong(CraftsmanRepository.CountRow::getCount).sum();
        boolean mine = c.getId() != null && !craftsmen.recommendedBy(me.id(), List.of(c.getId())).isEmpty();
        return toDto(me, c, count, mine, photo, author);
    }

    private CraftsmanDto toDto(AuthUser me, Craftsman c, long count, boolean mine, StoredFile photo, User author) {
        return new CraftsmanDto(c.getId(), c.getName(), c.getTrade(), c.getPhone(), c.getEmail(), c.getWebsite(),
                c.getDescription(), photo == null ? null : files.toDto(photo),
                author == null ? null : author.getFirstName() + " " + author.getLastName(),
                canEdit(me, c), count, mine, c.getCreatedAt());
    }

    private static boolean canEdit(AuthUser me, Craftsman c) {
        return me.isAdmin() || me.id().equals(c.getCreatedBy());
    }

    private Craftsman editable(AuthUser me, UUID id) {
        Craftsman c = find(me, id);
        if (!canEdit(me, c)) {
            throw ApiException.forbidden("NOT_AUTHOR", "Only the person who added it or an entrance admin can do this");
        }
        return c;
    }

    private Craftsman find(AuthUser me, UUID id) {
        return craftsmen.findByIdAndEntranceId(id, me.entranceId())
                .orElseThrow(() -> ApiException.notFound("Craftsman not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
