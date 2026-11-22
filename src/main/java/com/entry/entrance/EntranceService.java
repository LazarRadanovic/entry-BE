package com.entry.entrance;

import com.entry.common.exception.ApiException;
import com.entry.entrance.dto.EntranceDto;
import com.entry.entrance.dto.InviteCodeResponse;
import com.entry.security.AuthUser;
import com.entry.user.UserRepository;
import com.entry.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntranceService {

    private final EntranceRepository entrances;
    private final UserRepository users;
    private final InviteCodeGenerator inviteCodes;

    @Transactional(readOnly = true)
    public EntranceDto getCurrent(AuthUser me) {
        Entrance e = load(me);
        long members = users.countByEntrance_IdAndStatus(e.getId(), UserStatus.ACTIVE);
        return new EntranceDto(e.getId(), e.getName(), e.getAddress(), members,
                me.isAdmin() ? e.getInviteCode() : null);
    }

    /** Invalidates the old code; already-pending requests are unaffected. */
    @Transactional
    public InviteCodeResponse regenerateInviteCode(AuthUser me) {
        Entrance e = load(me);
        e.setInviteCode(inviteCodes.generateUnique());
        return new InviteCodeResponse(e.getInviteCode());
    }

    private Entrance load(AuthUser me) {
        return entrances.findById(me.entranceId()).orElseThrow(() -> ApiException.notFound("Entrance not found"));
    }
}
