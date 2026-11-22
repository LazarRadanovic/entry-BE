package com.entry.fee;

import com.entry.fee.dto.AddUnitRequest;
import com.entry.fee.dto.AssignUnitsRequest;
import com.entry.fee.dto.CostDto;
import com.entry.fee.dto.CostPaymentRequest;
import com.entry.fee.dto.CostRequest;
import com.entry.fee.dto.CostTableDto;
import com.entry.fee.dto.MyCostsDto;
import com.entry.fee.dto.UnitDto;
import com.entry.fee.dto.UpdateUnitRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Entrance costs and apartments. Everyone reads them; entrance admins (/api/admin/**) edit them. */
@RestController
@RequiredArgsConstructor
public class CostController {

    private final CostService service;

    @GetMapping("/api/costs")
    public List<CostDto> costs(@AuthenticationPrincipal AuthUser me) {
        return service.costs(me);
    }

    @GetMapping("/api/costs/mine")
    public MyCostsDto mine(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.mine(me, year);
    }

    @GetMapping("/api/costs/{id}/table")
    public CostTableDto table(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @RequestParam(required = false) Integer year) {
        return service.table(me, id, year);
    }

    @GetMapping("/api/units")
    public List<UnitDto> units(@AuthenticationPrincipal AuthUser me) {
        return service.units(me);
    }

    // ---- admin ----

    @PostMapping("/api/admin/costs")
    @ResponseStatus(HttpStatus.CREATED)
    public CostDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CostRequest request) {
        return service.create(me, request);
    }

    @PutMapping("/api/admin/costs/{id}")
    public CostDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody CostRequest request) {
        return service.update(me, id, request);
    }

    @PutMapping("/api/admin/costs/{id}/units")
    public CostDto assign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody AssignUnitsRequest request) {
        return service.assign(me, id, request.appliesToAll(), request.unitIds());
    }

    @DeleteMapping("/api/admin/costs/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/costs/payments")
    public ResponseEntity<Void> setPaid(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody CostPaymentRequest request) {
        service.setPaid(me, request.costId(), request.unitId(), request.period(), request.paid());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/units")
    public ResponseEntity<Void> addUnit(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AddUnitRequest request) {
        service.addUnit(me, request.floor(), request.apartment(), request.areaM2());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/units/{id}")
    public ResponseEntity<Void> updateUnit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody UpdateUnitRequest request) {
        service.updateUnit(me, id, request.areaM2());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/units/{id}")
    public ResponseEntity<Void> removeUnit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.removeUnit(me, id);
        return ResponseEntity.noContent().build();
    }
}
