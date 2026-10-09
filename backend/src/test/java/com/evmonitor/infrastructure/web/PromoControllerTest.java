package com.evmonitor.infrastructure.web;

import com.evmonitor.domain.User;
import com.evmonitor.infrastructure.persistence.JpaPromoCodeRepository;
import com.evmonitor.infrastructure.persistence.PromoCodeEntity;
import com.evmonitor.infrastructure.security.UserPrincipal;
import com.evmonitor.testutil.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PromoControllerTest extends AbstractIntegrationTest {

    @Autowired
    private JpaPromoCodeRepository promoCodeRepository;

    @BeforeEach
    void firstevCode() {
        promoCodeRepository.save(new PromoCodeEntity("FIRSTEV", 28, 100, "firstev", null, true, LocalDateTime.now()));
    }

    @Test
    void firstevReaderSeesTheOffer() {
        ResponseEntity<String> response = get("/api/subscription/promo-offer", userFrom("firstev"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"code\":\"FIRSTEV\"").contains("\"trialDays\":28");
    }

    @Test
    void otherUsersGetNoOfferAndAReasonForTheCode() {
        String token = userFrom("reddit");

        assertThat(get("/api/subscription/promo-offer", token).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get("/api/subscription/promo/firstev", token).getBody())
                .contains("\"valid\":false").contains("NOT_ELIGIBLE");
    }

    @Test
    void guestsGet401() {
        assertThat(restTemplate.getForEntity("/api/subscription/promo/FIRSTEV", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String userFrom(String utmSource) {
        User user = createAndSaveUser("promo-" + System.nanoTime() + "@example.com");
        User withSource = userRepository.save(user.toBuilder().utmSource(utmSource).build());
        return jwtService.generateToken(UserPrincipal.create(withSource));
    }

    private ResponseEntity<String> get(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
