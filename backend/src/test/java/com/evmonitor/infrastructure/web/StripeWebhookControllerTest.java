package com.evmonitor.infrastructure.web;

import com.evmonitor.application.StripeService;
import com.stripe.exception.SignatureVerificationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for StripeWebhookController.
 *
 * StripeService is mocked — no real Stripe calls are made.
 * We verify that the controller correctly dispatches to the service
 * and handles signature errors with the right HTTP status codes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StripeWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StripeService stripeService;

    private static final String STRIPE_PAYLOAD = "{\"type\":\"customer.subscription.created\"}";

    @Test
    void handleWebhook_whenSignatureValid_returns200() throws Exception {
        doNothing().when(stripeService).handleWebhookEvent(any(), any());

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "valid-sig")
                        .content(STRIPE_PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void handleWebhook_whenSignatureInvalid_returns400() throws Exception {
        doThrow(new SignatureVerificationException("Invalid signature", "bad-sig"))
                .when(stripeService).handleWebhookEvent(any(), any());

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "bad-sig")
                        .content(STRIPE_PAYLOAD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void handleWebhook_whenServiceThrowsGenericException_returns500() throws Exception {
        // Application errors return 500 so Stripe retries the event
        doThrow(new RuntimeException("Unexpected processing error"))
                .when(stripeService).handleWebhookEvent(any(), any());

        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", "valid-sig")
                        .content(STRIPE_PAYLOAD))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void handleWebhook_withoutStripeSignatureHeader_returns400() throws Exception {
        mockMvc.perform(post("/api/webhooks/stripe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(STRIPE_PAYLOAD))
                .andExpect(status().isBadRequest());
    }
}
