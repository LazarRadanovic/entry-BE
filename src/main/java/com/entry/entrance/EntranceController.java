package com.entry.entrance;

import com.entry.entrance.dto.EntranceDto;
import com.entry.entrance.dto.InviteCodeResponse;
import com.entry.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EntranceController {

    private final EntranceService entranceService;

    @GetMapping("/api/entrances/current")
    public EntranceDto current(@AuthenticationPrincipal AuthUser me) {
        return entranceService.getCurrent(me);
    }

    @PostMapping("/api/admin/entrance/invite-code")
    public InviteCodeResponse regenerateInviteCode(@AuthenticationPrincipal AuthUser me) {
        return entranceService.regenerateInviteCode(me);
    }
}
