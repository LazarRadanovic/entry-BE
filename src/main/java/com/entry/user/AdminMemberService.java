package com.entry.user;

import com.entry.auth.RefreshTokenService;
import com.entry.common.exception.ApiException;
import com.entry.security.AuthUser;
import com.entry.user.dto.MemberDto;
import java.util.List;
import java.util.UUID;
import com.entry.user.event.MemberApprovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Entrance-admin operations. Every lookup is scoped to the admin's own entrance. */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final UserRepository users;
    private final RefreshTokenService refreshTokens;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<MemberDto> list(AuthUser admin, UserStatus status) {
        return users.findByEntrance_IdAndStatusOrderByCreatedAtAsc(admin.entranceId(), status)
                .stream().map(userMapper::toMember).toList();
    }

    @Transactional
    public MemberDto approve(AuthUser admin, UUID memberId) {
        User user = loadOther(admin, memberId);
        requireStatus(user, UserStatus.PENDING);
        user.setStatus(UserStatus.ACTIVE);
        events.publishEvent(new MemberApprovedEvent(admin.entranceId(), user.getId(), admin.id()));
        return userMapper.toMember(user);
    }

    @Transactional
    public MemberDto reject(AuthUser admin, UUID memberId) {
        User user = loadOther(admin, memberId);
        requireStatus(user, UserStatus.PENDING);
        user.setStatus(UserStatus.REJECTED);
        refreshTokens.revokeAll(user);
        return userMapper.toMember(user);
    }

    /** Removes an active member from the entrance and ends their sessions. */
    @Transactional
    public void remove(AuthUser admin, UUID memberId) {
        User user = loadOther(admin, memberId);
        requireStatus(user, UserStatus.ACTIVE);
        user.setStatus(UserStatus.REJECTED);
        refreshTokens.revokeAll(user);
    }

    @Transactional
    public MemberDto changeRole(AuthUser admin, UUID memberId, Role role) {
        User user = loadOther(admin, memberId);
        requireStatus(user, UserStatus.ACTIVE);
        user.setRole(role);
        return userMapper.toMember(user);
    }

    /** Admins cannot act on themselves, so an entrance can never lose its last admin this way. */
    private User loadOther(AuthUser admin, UUID memberId) {
        if (admin.id().equals(memberId)) {
            throw ApiException.badRequest("CANNOT_MODIFY_SELF", "You cannot perform this action on yourself");
        }
        return users.findByIdAndEntrance_Id(memberId, admin.entranceId())
                .orElseThrow(() -> ApiException.notFound("Member not found"));
    }

    private static void requireStatus(User user, UserStatus expected) {
        if (user.getStatus() != expected) {
            throw ApiException.conflict("INVALID_MEMBER_STATUS",
                    "Member must be " + expected + " but is " + user.getStatus());
        }
    }
}
