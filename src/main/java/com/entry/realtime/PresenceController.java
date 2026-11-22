package com.entry.realtime;

import com.entry.security.AuthUser;
import com.entry.user.User;
import com.entry.user.UserMapper;
import com.entry.user.UserRepository;
import com.entry.user.dto.ResidentDto;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceTracker presence;
    private final UserRepository users;
    private final UserMapper userMapper;

    /** Initial online list; afterwards follow {@code /topic/entrance/{id}/presence}. */
    @GetMapping("/api/presence")
    @Transactional(readOnly = true)
    public List<ResidentDto> online(@AuthenticationPrincipal AuthUser me) {
        return users.findAllById(presence.onlineUserIds(me.entranceId())).stream()
                .filter(u -> u.isActive() && u.getEntrance().getId().equals(me.entranceId()))
                .sorted(Comparator.comparing(User::getFirstName).thenComparing(User::getLastName))
                .map(userMapper::toResident)
                .toList();
    }
}
