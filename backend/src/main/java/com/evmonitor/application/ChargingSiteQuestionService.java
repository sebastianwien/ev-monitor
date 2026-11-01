package com.evmonitor.application;

import com.evmonitor.domain.ChargingSite;
import com.evmonitor.domain.ChargingSiteQuestion;
import com.evmonitor.domain.ChargingSiteQuestionRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EvLog;
import com.evmonitor.domain.EvLogRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Frage nach der Säule: Bleiben bei einer Ladung mehrere Register-Säulen offen, fragt die App den
 * Nutzer einmal je Ort und Ladeart. Seine Antwort ordnet alle offenen Ladungen dort zu, die technisch
 * zur Säule passen; künftige Ladungen an dieser Säule erkennt danach die Historien-Regel des
 * {@link ChargingSiteMatcher}. "Weiß ich nicht" schließt die Frage, sie kommt nicht wieder.
 *
 * Die Kandidaten sind ein Schnappschuss aus dem Register: geantwortet wird nur mit einer Säule,
 * die darin steht, so kann niemand eine erfundene Säule an seine Ladungen schreiben.
 */
@Service
@RequiredArgsConstructor
public class ChargingSiteQuestionService {

    private static final TypeReference<List<NearbyStation>> CANDIDATES = new TypeReference<>() {
    };

    private final ChargingSiteQuestionRepository repository;
    private final EvLogRepository evLogRepository;
    private final ChargingSiteAttacher attacher;
    private final CoinLogService coinLogService;
    private final ObjectMapper objectMapper;

    /** @param candidates Säulen, die nach Umkreis und Technik in Frage kamen, die nächste zuerst */
    public record OpenQuestion(UUID id, ChargingType chargingType, int logCount, LocalDateTime lastLoggedAt,
                               List<NearbyStation> candidates) {
    }

    public record AnswerResult(int assigned, int watt) {
    }

    /** Legt die Frage zur Ladung an, sofern es zu Ort und Ladeart noch keine gibt. */
    void ask(UUID userId, EvLog evLog, List<NearbyStation> candidates) {
        try {
            repository.openIfAbsent(userId, evLog.getGeohash().substring(0, 7), evLog.getChargingType(),
                    write(candidates.stream().map(ChargingSiteQuestionService::withoutPosition).toList()));
        } catch (DataIntegrityViolationException raced) {
            // Eine parallele Zuordnung hat dieselbe Frage gerade angelegt - eine reicht.
        }
    }

    public List<OpenQuestion> openQuestions(UUID userId) {
        return repository.findOpenWithLogs(userId).stream()
                .map(w -> new OpenQuestion(w.question().id(), w.question().chargingType(), w.logCount(),
                        w.lastLoggedAt(), read(w.question().candidatesJson())))
                .toList();
    }

    /**
     * Ordnet die offenen Ladungen der Frage der gewählten Säule zu. Eine schon beantwortete Frage
     * ändert nichts mehr; Ladungen, die inzwischen eine Säule haben, behalten sie.
     *
     * @param siteName Name der Säule aus den Kandidaten der Frage
     */
    public AnswerResult answer(UUID userId, UUID questionId, String siteName) {
        ChargingSiteQuestion question = find(userId, questionId);
        String wanted = ChargingSite.nameKey(siteName);
        NearbyStation station = read(question.candidatesJson()).stream()
                .filter(s -> wanted.equals(ChargingSite.nameKey(s.name())))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Säule gehört nicht zur Frage"));
        if (question.status() != ChargingSiteQuestion.Status.OPEN) return new AnswerResult(0, 0);

        int assigned = 0;
        for (UUID logId : repository.findOpenLogIds(question)) {
            EvLog evLog = evLogRepository.findById(logId).orElse(null);
            if (evLog == null || !ChargingSiteMatcher.fits(station, evLog.getChargingType(), evLog.getMaxChargingPowerKw())) {
                continue;
            }
            if (attacher.attach(evLog, userId, station)) assigned++;
        }
        boolean closed = repository.close(questionId, userId, ChargingSiteQuestion.Status.ANSWERED);
        int watt = closed && assigned > 0
                ? coinLogService.awardOncePerEntity(userId, CoinLogService.CoinEvent.SITE_CONFIRMED, questionId)
                : 0;
        return new AnswerResult(assigned, watt);
    }

    public void dismiss(UUID userId, UUID questionId) {
        find(userId, questionId);
        repository.close(questionId, userId, ChargingSiteQuestion.Status.DISMISSED);
    }

    private ChargingSiteQuestion find(UUID userId, UUID questionId) {
        return repository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> NotFoundException.forEntity("ChargingSiteQuestion", questionId));
    }

    /** Die Position der Säule braucht die Frage nicht - gespeichert wird nur, was die Kachel zeigt. */
    private static NearbyStation withoutPosition(NearbyStation s) {
        return new NearbyStation(s.name(), s.known(), s.distanceMeters(), s.maxAcKw(), s.maxDcKw(), s.chargePoints(),
                s.geohash(), null, null, s.registerId(), s.street(), s.houseNumber(), s.postalCode(), s.city(),
                s.plugTypes(), s.commissionedOn(), s.siteLabel(), s.payment(), s.openingHours());
    }

    private String write(List<NearbyStation> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Kandidaten nicht serialisierbar", e);
        }
    }

    private List<NearbyStation> read(String json) {
        try {
            return objectMapper.readValue(json, CANDIDATES);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Kandidaten der Frage nicht lesbar", e);
        }
    }
}
