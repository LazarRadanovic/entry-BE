package com.entry.billing;

import com.entry.common.exception.ApiException;
import com.entry.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Makes entrances whose trial ended without a subscription read-only: reads pass, writes get
 * 402 SUBSCRIPTION_REQUIRED. Nobody loses access to what was already shared.
 * Registered for /api/** minus auth, own profile, push, notifications, billing and image downloads (see WebConfig).
 */
@Component
public class SubscriptionEnforcementInterceptor implements HandlerInterceptor {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (READ_METHODS.contains(request.getMethod())) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user && user.entranceReadOnly()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_REQUIRED",
                    "The free trial of this entrance has ended, so it is read-only until an entrance admin subscribes.");
        }
        return true;
    }
}
