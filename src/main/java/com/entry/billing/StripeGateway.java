package com.entry.billing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Everything we need from Stripe, behind an interface so billing logic can be tested without the network. */
public interface StripeGateway {

    String createCustomer(String email, String name, UUID entranceId);

    /** @return the hosted Checkout URL */
    String createCheckoutSession(CheckoutRequest request);

    /** @return the hosted Billing Portal URL (update card, cancel, invoices) */
    String createPortalSession(String customerId, String returnUrl);

    Optional<SubscriptionSnapshot> retrieveSubscription(String subscriptionId);

    /** The customer's most relevant subscription (a live one if any, otherwise the newest). */
    Optional<SubscriptionSnapshot> latestSubscription(String customerId);

    /** Verifies the Stripe-Signature header. @throws InvalidWebhookException if it doesn't match */
    WebhookEvent verifyWebhook(String payload, String signatureHeader);

    record CheckoutRequest(String customerId, String priceId, UUID entranceId, Instant trialEnd, String successUrl,
            String cancelUrl) {
    }

    record SubscriptionSnapshot(String id, String customerId, String status, Instant currentPeriodEnd,
            boolean cancelAtPeriodEnd, String entranceId) {
    }

    /** The parts of an event we use. Any of the ids may be null depending on the event type. */
    record WebhookEvent(String type, String subscriptionId, String customerId, String entranceId) {
    }

    class InvalidWebhookException extends RuntimeException {
        public InvalidWebhookException(String message) {
            super(message);
        }
    }
}
