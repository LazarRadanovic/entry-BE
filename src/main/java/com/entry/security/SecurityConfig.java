package com.entry.security;

import static org.springframework.security.authorization.AuthorityAuthorizationManager.hasAuthority;

import com.entry.common.config.AppProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Access rules:
 * <ul>
 *   <li>{@code /api/auth/**}, docs, health, websocket handshake: public (websocket is authenticated on STOMP CONNECT)</li>
 *   <li>{@code /api/users/me/**}, {@code /api/push/**}: any logged-in user, including PENDING ones
 *       (the "waiting for approval" screen, and push so they hear about approval)</li>
 *   <li>{@code /api/admin/**}: ACTIVE entrance admins</li>
 *   <li>everything else under {@code /api/**}: ACTIVE members</li>
 * </ul>
 * Entrance scoping (a member only sees their own entrance) is enforced in the services.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthUserJwtConverter jwtConverter;
    private final AppProperties props;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer auth; the refresh cookie is SameSite and scoped to /api/auth.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // image downloads are authorised by a signed URL, see FileUrlSigner
                        .requestMatchers(HttpMethod.GET, "/api/files/*").permitAll()
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        .requestMatchers("/api/push/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/billing/webhook").permitAll()
                        .requestMatchers("/api/admin/**").access(AuthorizationManagers.allOf(
                                hasAuthority("ROLE_ENTRANCE_ADMIN"), hasAuthority("STATUS_ACTIVE")))
                        .requestMatchers("/api/**").hasAuthority("STATUS_ACTIVE")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authenticationEntryPoint()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()));
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> ProblemJsonWriter.write(response, HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED", "Authentication is required or the access token is invalid/expired");
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof AuthUser user && !user.isActive()) {
                ProblemJsonWriter.write(response, HttpStatus.FORBIDDEN, "MEMBERSHIP_NOT_ACTIVE",
                        "Your entrance membership is not active yet");
            } else {
                ProblemJsonWriter.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "You do not have permission to perform this action");
            }
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
