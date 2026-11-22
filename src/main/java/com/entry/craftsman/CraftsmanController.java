package com.entry.craftsman;

import com.entry.craftsman.dto.CraftsmanDto;
import com.entry.craftsman.dto.CraftsmanRequest;
import com.entry.craftsman.dto.RecommendResponse;
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

/** Craftsmen / handymen recommended by neighbours. */
@RestController
@RequestMapping("/api/craftsmen")
@RequiredArgsConstructor
public class CraftsmanController {

    private final CraftsmanService service;

    @GetMapping
    public List<CraftsmanDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Trade trade) {
        return service.list(me, trade);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CraftsmanDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CraftsmanRequest request) {
        return service.create(me, request);
    }

    @PutMapping("/{id}")
    public CraftsmanDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody CraftsmanRequest request) {
        return service.update(me, id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/recommend")
    public RecommendResponse recommend(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.recommend(me, id, true);
    }

    @DeleteMapping("/{id}/recommend")
    public RecommendResponse unrecommend(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.recommend(me, id, false);
    }
}
