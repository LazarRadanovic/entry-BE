package com.entry.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cookie cookie,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Storage storage,
        @Valid @NotNull Push push,
        @Valid @NotNull Billing billing) {

    public record Jwt(
            @NotBlank String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl) {
    }

    public record Cookie(boolean secure, @NotBlank String sameSite) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Storage(@NotBlank String localDir, @NotNull Duration urlTtl) {
    }

    /** Billing is enforced only when Stripe is configured (secret key + price). */
    public record Billing(
            @Min(1) int trialDays,
            String stripeSecretKey,
            String stripeWebhookSecret,
            String stripePriceId,
            String priceLabel,
            @NotBlank String appUrl) {

        public boolean enabled() {
            return stripeSecretKey != null && !stripeSecretKey.isBlank()
                    && stripePriceId != null && !stripePriceId.isBlank();
        }
    }

    /** Push is disabled when the keys are blank. */
    public record Push(String vapidPublicKey, String vapidPrivateKey, @NotBlank String subject) {

        public boolean enabled() {
            return vapidPublicKey != null && !vapidPublicKey.isBlank()
                    && vapidPrivateKey != null && !vapidPrivateKey.isBlank();
        }
    }
}
