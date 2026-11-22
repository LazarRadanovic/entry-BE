package com.entry.billing;

import com.entry.common.exception.ApiException;
import com.entry.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Locks the app (402 SUBSCRIPTION_REQUIRED) for entrances whose trial ended without a subscription.
 * Registered for /api/** minus auth, own profile, push, billing and image downloads (see WebConfig).
 */
@Component
public class SubscriptionEnforcementInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user && user.entranceLocked()) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "SUBSCRIPTION_REQUIRED",
                    "The free trial of this entrance has ended. An entrance admin needs to subscribe.");
        }
        return true;
    }
}
