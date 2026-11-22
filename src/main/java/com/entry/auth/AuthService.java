package com.entry.auth;

import com.entry.auth.dto.LoginRequest;
import com.entry.auth.dto.RegisterRequest;
import com.entry.common.exception.ApiException;
import com.entry.entrance.Entrance;
import com.entry.entrance.EntranceRepository;
import com.entry.entrance.InviteCodeGenerator;
import com.entry.security.JwtService;
import com.entry.user.Role;
import com.entry.user.User;
import com.entry.user.UserRepository;
import com.entry.user.UserStatus;
import com.entry.user.UserMapper;
import com.entry.common.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import com.entry.user.event.JoinRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final EntranceRepository entrances;
    private final InviteCodeGenerator inviteCodes;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokens;
    private final UserMapper userMapper;
    private final ApplicationEventPublisher events;
    private final AppProperties props;

    @Transactional
    public AuthResult register(RegisterRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName().trim());
        user.setLastName(req.lastName().trim());
        user.setFloor(req.floor().trim());
        user.setApartment(req.apartment().trim());

        if (req.hasInviteCode()) {
            Entrance entrance = entrances.findByInviteCode(req.inviteCode().trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> ApiException.badRequest("INVALID_INVITE_CODE", "Invite code is not valid"));
            user.setEntrance(entrance);
            user.setRole(Role.RESIDENT);
            user.setStatus(UserStatus.PENDING);
        } else {
            Entrance entrance = entrances.save(new Entrance(
                    req.newEntrance().name().trim(),
                    req.newEntrance().address().trim(),
                    inviteCodes.generateUnique(),
                    Instant.now().plus(Duration.ofDays(props.billing().trialDays()))));
            user.setEntrance(entrance);
            user.setRole(Role.ENTRANCE_ADMIN);
            user.setStatus(UserStatus.ACTIVE);
        }

        user = users.save(user);
        if (user.getStatus() == UserStatus.PENDING) {
            events.publishEvent(new JoinRequestedEvent(user.getEntrance().getId(), user.getId()));
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(LoginRequest req) {
        User user = users.findByEmail(normalizeEmail(req.email()))
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password"));
        ensureNotRejected(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired, please log in again");
        }
        User user = refreshTokens.consume(rawRefreshToken);
        ensureNotRejected(user);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    private AuthResult issueTokens(User user) {
        String access = jwtService.issueAccessToken(user);
        String refresh = refreshTokens.create(user);
        return new AuthResult(access, jwtService.accessTokenTtlSeconds(), refresh, userMapper.toUserDto(user));
    }

    private static void ensureNotRejected(User user) {
        if (user.getStatus() == UserStatus.REJECTED) {
            throw ApiException.forbidden("MEMBERSHIP_REJECTED",
                    "Your membership was rejected or removed by the entrance admin");
        }
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
