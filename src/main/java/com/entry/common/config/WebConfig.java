package com.entry.common.config;

import com.entry.billing.SubscriptionEnforcementInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SubscriptionEnforcementInterceptor subscriptionEnforcement;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(subscriptionEnforcement)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/**",
                        "/api/users/me", "/api/users/me/**",
                        "/api/push/**",
                        "/api/notifications", "/api/notifications/**",
                        "/api/billing", "/api/billing/**",
                        "/api/admin/billing/**",
                        "/api/files/*");
    }
}
