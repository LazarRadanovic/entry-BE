package com.entry.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.entry.TestcontainersConfiguration;
import com.entry.billing.StripeGateway.CheckoutRequest;
import com.entry.billing.StripeGateway.SubscriptionSnapshot;
import com.entry.billing.StripeGateway.WebhookEvent;
import com.entry.support.ApiTestSupport;
import com.entry.support.ApiTestSupport.Member;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Billing enabled (fake Stripe keys) with the Stripe gateway mocked: no network. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "app.billing.stripe-secret-key=sk_test_dummy",
        "app.billing.stripe-price-id=price_dummy",
        "app.billing.stripe-webhook-secret=whsec_dummy",
        "app.billing.trial-days=30"})
class BillingIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    StripeGateway stripe;

    Member admin;
    Member resident;

    @BeforeEach
    void setUp() throws Exception {
        ApiTestSupport api = new ApiTestSupport(mvc);
        admin = api.newAdmin("Ana");
        resident = api.newResident(admin, "Marko");
    }

    @Test
    void newEntranceStartsWithTrial() throws Exception {
        mvc.perform(get("/api/billing").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.state").value("TRIAL"))
                .andExpect(jsonPath("$.trialDaysLeft").value(30))
                .andExpect(jsonPath("$.canSubscribe").value(true));
        mvc.perform(get("/api/billing").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.canSubscribe").value(false));
        mvc.perform(get("/api/posts").header("Authorization", resident.bearer())).andExpect(status().isOk());
    }

    @Test
    void expiredTrialLocksTheEntranceUntilSubscribed() throws Exception {
        expireTrial();

        mvc.perform(get("/api/posts").header("Authorization", resident.bearer()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_REQUIRED"));
        // still reachable while locked
        mvc.perform(get("/api/users/me").header("Authorization", resident.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/billing").header("Authorization", resident.bearer()))
                .andExpect(jsonPath("$.state").value("EXPIRED"))
                .andExpect(jsonPath("$.accessAllowed").value(false));

        // admin subscribes: Checkout, then Stripe sends a webhook
        when(stripe.createCustomer(anyString(), anyString(), any())).thenReturn("cus_123");
        when(stripe.createCheckoutSession(any())).thenReturn("https://checkout.stripe.test/s/abc");
        mvc.perform(post("/api/admin/billing/checkout").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://checkout.stripe.test/s/abc"));

        when(stripe.verifyWebhook(anyString(), eq("t=1,v1=good")))
                .thenReturn(new WebhookEvent("customer.subscription.created", "sub_1", "cus_123", null));
        when(stripe.retrieveSubscription("sub_1")).thenReturn(Optional.of(new SubscriptionSnapshot("sub_1",
                "cus_123", "active", Instant.now().plus(30, ChronoUnit.DAYS), false, admin.entranceId())));
        mvc.perform(post("/api/billing/webhook").header("Stripe-Signature", "t=1,v1=good")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"customer.subscription.created\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/posts").header("Authorization", resident.bearer())).andExpect(status().isOk());
        mvc.perform(get("/api/billing").header("Authorization", admin.bearer()))
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.canManage").value(true))
                .andExpect(jsonPath("$.canSubscribe").value(false));

        // a cancelled subscription whose paid period is over locks it again
        when(stripe.retrieveSubscription("sub_1")).thenReturn(Optional.of(new SubscriptionSnapshot("sub_1",
                "cus_123", "canceled", Instant.now().minus(1, ChronoUnit.DAYS), false, admin.entranceId())));
        when(stripe.verifyWebhook(anyString(), eq("t=2,v1=good")))
                .thenReturn(new WebhookEvent("customer.subscription.deleted", "sub_1", "cus_123", null));
        mvc.perform(post("/api/billing/webhook").header("Stripe-Signature", "t=2,v1=good").content("{}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts").header("Authorization", resident.bearer()))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void checkoutCarriesRemainingTrialOver() throws Exception {
        when(stripe.createCustomer(anyString(), anyString(), any())).thenReturn("cus_trial");
        when(stripe.createCheckoutSession(any())).thenReturn("https://checkout.stripe.test/s/t");

        mvc.perform(post("/api/admin/billing/checkout").header("Authorization", admin.bearer()))
                .andExpect(status().isOk());

        ArgumentCaptor<CheckoutRequest> req = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(stripe).createCheckoutSession(req.capture());
        assertThat(req.getValue().customerId()).isEqualTo("cus_trial");
        assertThat(req.getValue().priceId()).isEqualTo("price_dummy");
        assertThat(req.getValue().entranceId()).isEqualTo(UUID.fromString(admin.entranceId()));
        assertThat(req.getValue().trialEnd()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
        assertThat(req.getValue().successUrl()).endsWith("/billing?checkout=success");
    }

    @Test
    void syncAfterCheckoutWorksWithoutWebhooks() throws Exception {
        expireTrial();
        jdbc.update("update entrance set stripe_customer_id = 'cus_sync' where id = ?",
                UUID.fromString(admin.entranceId()));
        when(stripe.latestSubscription("cus_sync")).thenReturn(Optional.of(new SubscriptionSnapshot("sub_sync",
                "cus_sync", "active", Instant.now().plus(30, ChronoUnit.DAYS), false, null)));

        mvc.perform(post("/api/admin/billing/sync").header("Authorization", admin.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.accessAllowed").value(true));
    }

    @Test
    void onlyAdminsManageBillingAndWebhooksNeedValidSignature() throws Exception {
        mvc.perform(post("/api/admin/billing/checkout").header("Authorization", resident.bearer()))
                .andExpect(status().isForbidden());

        when(stripe.verifyWebhook(anyString(), eq("forged")))
                .thenThrow(new StripeGateway.InvalidWebhookException("Invalid signature"));
        mvc.perform(post("/api/billing/webhook").header("Stripe-Signature", "forged").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK"));
    }

    private void expireTrial() {
        jdbc.update("update entrance set trial_ends_at = now() - interval '1 day' where id = ?",
                UUID.fromString(admin.entranceId()));
    }
}
