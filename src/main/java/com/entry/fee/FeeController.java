package com.entry.fee;

import com.entry.fee.dto.AddUnitRequest;
import com.entry.fee.dto.FeeSettingsRequest;
import com.entry.fee.dto.FeeTableDto;
import com.entry.fee.dto.MarkPaymentRequest;
import com.entry.security.AuthUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Building-cleaning fee table. Everyone reads it; entrance admins (/api/admin/**) edit it. */
@RestController
@RequiredArgsConstructor
public class FeeController {

    private final FeeService fees;

    @GetMapping("/api/fees")
    public FeeTableDto table(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return fees.table(me, year);
    }

    @PutMapping("/api/admin/fees/payments")
    public ResponseEntity<Void> setPaid(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody MarkPaymentRequest request) {
        fees.setPaid(me, request.unitId(), request.year(), request.month(), request.paid());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/admin/fees/settings")
    public ResponseEntity<Void> settings(@AuthenticationPrincipal AuthUser me,
            @Valid @RequestBody FeeSettingsRequest request) {
        fees.updateSettings(me, request.amount(), request.currency());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/fees/units")
    public ResponseEntity<Void> addUnit(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AddUnitRequest request) {
        fees.addUnit(me, request.floor(), request.apartment());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/fees/units/{id}")
    public ResponseEntity<Void> removeUnit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        fees.removeUnit(me, id);
        return ResponseEntity.noContent().build();
    }
}
