package com.entry.announcement;

import com.entry.announcement.dto.AnnouncementDto;
import com.entry.announcement.dto.AnnouncementRequest;
import com.entry.announcement.dto.AnnouncementStatus;
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
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @GetMapping
    public List<AnnouncementDto> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(defaultValue = "ACTIVE") AnnouncementStatus status) {
        return announcementService.list(me, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AnnouncementDto create(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody AnnouncementRequest request) {
        return announcementService.create(me, request);
    }

    @GetMapping("/{id}")
    public AnnouncementDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return announcementService.get(me, id);
    }

    @PutMapping("/{id}")
    public AnnouncementDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody AnnouncementRequest request) {
        return announcementService.update(me, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        announcementService.delete(me, id);
        return ResponseEntity.noContent().build();
    }
}
