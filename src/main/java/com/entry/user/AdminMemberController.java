package com.entry.user;

import com.entry.security.AuthUser;
import com.entry.user.dto.ChangeRoleRequest;
import com.entry.user.dto.MemberDto;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    /** {@code ?status=PENDING} for join requests, {@code ACTIVE} (default) for members. */
    @GetMapping
    public List<MemberDto> list(@AuthenticationPrincipal AuthUser me,
            @RequestParam(defaultValue = "ACTIVE") UserStatus status) {
        return adminMemberService.list(me, status);
    }

    @PostMapping("/{id}/approve")
    public MemberDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return adminMemberService.approve(me, id);
    }

    @PostMapping("/{id}/reject")
    public MemberDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return adminMemberService.reject(me, id);
    }

    @PutMapping("/{id}/role")
    public MemberDto changeRole(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return adminMemberService.changeRole(me, id, request.role());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        adminMemberService.remove(me, id);
        return ResponseEntity.noContent().build();
    }
}
