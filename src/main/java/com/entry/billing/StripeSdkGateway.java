package com.entry.billing;

import com.entry.common.config.AppProperties;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.StripeCollection;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionListParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** {@link StripeGateway} backed by the official stripe-java SDK. */
@Component
public class StripeSdkGateway implements StripeGateway {

    private static final Set<String> LIVE = Set.of("active", "trialing", "past_due");

    private final StripeClient client;
    private final String webhookSecret;
    private final JsonMapper json;

    public StripeSdkGateway(AppProperties props, JsonMapper json) {
        AppProperties.Billing b = props.billing();
        this.client = b.enabled() ? new StripeClient(b.stripeSecretKey()) : null;
        this.webhookSecret = b.stripeWebhookSecret();
        this.json = json;
    }

    @Override
    public String createCustomer(String email, String name, UUID entranceId) {
        return call(() -> client().v1().customers().create(CustomerCreateParams.builder()
                .setEmail(email)
                .setName(name)
                .putMetadata("entranceId", entranceId.toString())
                .build()).getId());
    }

    @Override
    public String createCheckoutSession(CheckoutRequest r) {
        SessionCreateParams.SubscriptionData.Builder subscription = SessionCreateParams.SubscriptionData.builder()
                .putMetadata("entranceId", r.entranceId().toString());
        if (r.trialEnd() != null) {
            subscription.setTrialEnd(r.trialEnd().getEpochSecond());
        }
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setCustomer(r.customerId())
                .setClientReferenceId(r.entranceId().toString())
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(r.priceId()).setQuantity(1L).build())
                .setSubscriptionData(subscription.build())
                .setSuccessUrl(r.successUrl())
                .setCancelUrl(r.cancelUrl())
                .build();
        return call(() -> client().v1().checkout().sessions().create(params).getUrl());
    }

    @Override
    public String createPortalSession(String customerId, String returnUrl) {
        return call(() -> client().v1().billingPortal().sessions().create(
                com.stripe.param.billingportal.SessionCreateParams.builder()
                        .setCustomer(customerId)
                        .setReturnUrl(returnUrl)
                        .build()).getUrl());
    }

    @Override
    public Optional<SubscriptionSnapshot> retrieveSubscription(String subscriptionId) {
        return Optional.of(call(() -> snapshot(client().v1().subscriptions().retrieve(subscriptionId))));
    }

    @Override
    public Optional<SubscriptionSnapshot> latestSubscription(String customerId) {
        StripeCollection<Subscription> list = call(() -> client().v1().subscriptions().list(
                SubscriptionListParams.builder()
                        .setCustomer(customerId)
                        .setStatus(SubscriptionListParams.Status.ALL)
                        .setLimit(10L)
                        .build()));
        List<Subscription> subs = list.getData();
        return subs.stream()
                .sorted(Comparator.comparing((Subscription s) -> !LIVE.contains(s.getStatus()))
                        .thenComparing(Subscription::getCreated, Comparator.reverseOrder()))
                .findFirst()
                .map(this::snapshot);
    }

    @Override
    public WebhookEvent verifyWebhook(String payload, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new InvalidWebhookException("Webhook secret not configured");
        }
        try {
            Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new InvalidWebhookException("Invalid signature");
        }
        // Read ids from the raw JSON: independent of the SDK's pinned API version.
        JsonNode root = json.readTree(payload);
        JsonNode object = root.path("data").path("object");
        String objectType = text(object, "object");
        String subscriptionId = "subscription".equals(objectType) ? text(object, "id") : text(object, "subscription");
        String entranceId = text(object.path("metadata"), "entranceId");
        if (entranceId == null) {
            entranceId = text(object, "client_reference_id");
        }
        return new WebhookEvent(text(root, "type"), subscriptionId, text(object, "customer"), entranceId);
    }

    private SubscriptionSnapshot snapshot(Subscription s) {
        // Since API 2025-03-31 the billing period lives on the subscription items.
        Long periodEnd = s.getItems() == null ? null : s.getItems().getData().stream()
                .map(SubscriptionItem::getCurrentPeriodEnd)
                .filter(v -> v != null)
                .max(Long::compare)
                .orElse(null);
        return new SubscriptionSnapshot(
                s.getId(),
                s.getCustomer(),
                s.getStatus(),
                periodEnd == null ? null : Instant.ofEpochSecond(periodEnd),
                Boolean.TRUE.equals(s.getCancelAtPeriodEnd()),
                s.getMetadata() == null ? null : s.getMetadata().get("entranceId"));
    }

    private StripeClient client() {
        if (client == null) {
            throw new IllegalStateException("Stripe is not configured");
        }
        return client;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isTextual() ? v.asText() : null;
    }

    private interface StripeCall<T> {
        T run() throws StripeException;
    }

    private static <T> T call(StripeCall<T> call) {
        try {
            return call.run();
        } catch (StripeException e) {
            throw new StripeFailureException(e.getMessage(), e);
        }
    }

    /** Stripe API error (network, invalid request, auth...). Mapped to 502 by the controller advice. */
    public static class StripeFailureException extends RuntimeException {
        StripeFailureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
