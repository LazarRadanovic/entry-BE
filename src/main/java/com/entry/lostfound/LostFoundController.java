package com.entry.lostfound;

import com.entry.lostfound.dto.LostFoundDto;
import com.entry.lostfound.dto.LostFoundRequest;
import com.entry.lostfound.dto.LostFoundStatus;
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
@RequestMapping("/api/lost-found")
@RequiredArgsConstructor
public class LostFoundController {

    private final LostFoundService service;

    @GetMapping
    public List<LostFoundDto> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(defaultValue = "OPEN") LostFoundStatus status,
            @RequestParam(required = false) LostFoundKind kind) {
        return service.list(me, status, kind);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LostFoundDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LostFoundRequest request) {
        return service.create(me, request);
    }

    @GetMapping("/{id}")
    public LostFoundDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}")
    public LostFoundDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody LostFoundRequest request) {
        return service.update(me, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    /** "It's mine" (FOUND) / "I found it" (LOST). */
    @PutMapping("/{id}/response")
    public LostFoundDto respond(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.respond(me, id, true);
    }

    @DeleteMapping("/{id}/response")
    public LostFoundDto withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.respond(me, id, false);
    }

    @PutMapping("/{id}/resolved")
    public LostFoundDto resolve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.setResolved(me, id, true);
    }

    @DeleteMapping("/{id}/resolved")
    public LostFoundDto reopen(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.setResolved(me, id, false);
    }
}
