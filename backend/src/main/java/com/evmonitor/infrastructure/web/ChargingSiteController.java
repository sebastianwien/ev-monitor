package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingSiteQuestionService;
import com.evmonitor.application.ChargingSiteService;
import com.evmonitor.application.NearbyStation;
import com.evmonitor.domain.ChargingSiteUsage;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/charging-sites")
@RequiredArgsConstructor
public class ChargingSiteController {

    private final ChargingSiteService chargingSiteService;
    private final ChargingSiteQuestionService questionService;

    /** Zuletzt genutzte Ladestandorte des angemeldeten Nutzers, fuer den Ortsschritt im Wizard. */
    @GetMapping("/recent")
    public List<RecentChargingSiteResponse> recent(Authentication authentication) {
        return chargingSiteService.recentlyUsed(userId(authentication)).stream().map(RecentChargingSiteResponse::from).toList();
    }

    /** Offene Fragen nach der Säule, die mit den meisten Ladungen zuerst. */
    @GetMapping("/questions")
    public List<QuestionResponse> questions(Authentication authentication) {
        return questionService.openQuestions(userId(authentication)).stream().map(QuestionResponse::from).toList();
    }

    @PostMapping("/questions/{id}/answer")
    public ChargingSiteQuestionService.AnswerResult answer(@PathVariable UUID id, @Valid @RequestBody AnswerRequest request,
                                                           Authentication authentication) {
        return questionService.answer(userId(authentication), id, request.name());
    }

    @PostMapping("/questions/{id}/dismiss")
    public ResponseEntity<Void> dismiss(@PathVariable UUID id, Authentication authentication) {
        questionService.dismiss(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private static UUID userId(Authentication authentication) {
        return ((UserPrincipal) authentication.getPrincipal()).getUser().getId();
    }

    public record AnswerRequest(@NotBlank @Size(max = 100) String name) {
    }

    /** Was die Kachel zeigt - Registerdaten ohne Position und Entfernung. */
    public record CandidateResponse(String name, Double maxAcKw, Double maxDcKw, int chargePoints, String address) {
        static CandidateResponse from(NearbyStation s) {
            return new CandidateResponse(s.name(), s.maxAcKw(), s.maxDcKw(), s.chargePoints(), s.address());
        }
    }

    public record QuestionResponse(UUID id, String geohash, ChargingType chargingType, int logCount, LocalDateTime lastLoggedAt,
                                   List<CandidateResponse> candidates) {
        static QuestionResponse from(ChargingSiteQuestionService.OpenQuestion q) {
            return new QuestionResponse(q.id(), q.geohash(), q.chargingType(), q.logCount(), q.lastLoggedAt(),
                    q.candidates().stream().map(CandidateResponse::from).toList());
        }
    }

    public record RecentChargingSiteResponse(UUID id, String name, String cpoName, String geohash,
                                             BigDecimal maxAcKw, BigDecimal maxDcKw, int chargePoints, boolean fastCharging,
                                             String address, java.util.List<String> plugTypes,
                                             LocalDateTime lastUsedAt, long usageCount) {
        static RecentChargingSiteResponse from(ChargingSiteUsage u) {
            var s = u.site();
            return new RecentChargingSiteResponse(s.id(), s.name(), s.cpoName(), s.geohash(), s.maxAcKw(), s.maxDcKw(),
                    s.chargePoints(), s.fastCharging(), s.register().address(), s.register().plugTypes(),
                    u.lastUsedAt(), u.usageCount());
        }
    }
}
