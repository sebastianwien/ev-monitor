package com.evmonitor.application.vehiclespec;

import com.evmonitor.domain.exception.AuthException;
import com.evmonitor.domain.exception.ConflictException;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequest;
import com.evmonitor.domain.vehiclespec.MissingVehicleRequestRepository;
import com.evmonitor.infrastructure.security.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Meldeweg "Fehlt etwas?": Nutzer melden fehlende Varianten, Modelle oder Hersteller.
 * Schutz gegen Massenversand in dieser Reihenfolge: Rate-Limit je Nutzer und IP (429),
 * Dedupe gleicher Text in 24 h (stilles 200), Tagesdeckel der Admin-Mails (nur DB-Zeile).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MissingVehicleRequestService {

    private static final int MAX_TEXT = 150;
    private static final int MAX_EMAIL = 254;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final MissingVehicleRequestRepository repository;
    private final RateLimitService rateLimitService;
    private final MissingVehicleRequestNotifier notifier;

    /** Admin-Mails je Tag; darüber bleibt die Meldung nur im Tab sichtbar. */
    @Value("${vehicle-request.daily-mail-cap:50}")
    private int dailyMailCap;

    @Transactional
    public void submit(UUID userId, String clientIp, MissingVehicleRequestCommand cmd) {
        if (!rateLimitService.tryConsumeVehicleRequestUser(userId.toString())
                || !rateLimitService.tryConsumeVehicleRequestIp(clientIp)) {
            throw AuthException.rateLimited();
        }
        String text = cleanText(cmd.freeText());
        String email = cleanEmail(cmd.contactEmail());

        LocalDateTime dayAgo = LocalDateTime.now().minusHours(24);
        if (repository.existsByUserIdAndFreeTextIgnoreCaseAndCreatedAtAfter(userId, text, dayAgo)) {
            log.info("Fahrzeug-Meldung: Duplikat innerhalb 24 h, user={}", userId);
            return;
        }

        MissingVehicleRequest request = MissingVehicleRequest.create(userId,
                new MissingVehicleRequest.Draft(email, text, cmd.brand(), cmd.model(), cmd.netKwh(), cmd.buildYear()));
        if (repository.countByAdminNotifiedAtAfter(LocalDate.now().atStartOfDay()) < dailyMailCap) {
            request.markAdminNotified();
            repository.save(request);
            notifier.notifyAdmin(request);
        } else {
            repository.save(request);
            log.warn("Fahrzeug-Meldung: Tagesdeckel {} erreicht, nur gespeichert, id={}", dailyMailCap, request.getId());
        }
    }

    @Transactional(readOnly = true)
    public List<MissingVehicleRequestResponse> openRequests() {
        return repository.findAllByDoneAtIsNullOrderByCreatedAtDesc().stream()
                .map(MissingVehicleRequestResponse::from)
                .toList();
    }

    /** Nur ein Admin löst die Rückmeldung an den Nutzer aus, nie das System. */
    @Transactional
    public void markDone(UUID adminId, UUID id) {
        MissingVehicleRequest request = repository.findById(id)
                .orElseThrow(() -> NotFoundException.forEntity("MissingVehicleRequest", id));
        if (request.isDone()) {
            throw new ConflictException("ALREADY_DONE", "Diese Meldung ist schon erledigt.");
        }
        request.markDone(adminId);
        if (notifier.notifyUserDone(request)) {
            request.markUserNotified();
        }
        repository.save(request);
    }

    private static String cleanText(String raw) {
        String text = raw == null ? "" : WHITESPACE.matcher(TAGS.matcher(raw).replaceAll("")).replaceAll(" ").trim();
        if (text.isEmpty()) {
            throw new ValidationException("Bitte schreib, was fehlt.");
        }
        if (text.length() > MAX_TEXT) {
            throw new ValidationException("Der Text darf höchstens " + MAX_TEXT + " Zeichen haben.");
        }
        return text;
    }

    private static String cleanEmail(String raw) {
        String email = raw == null ? "" : raw.trim();
        if (email.length() > MAX_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new ValidationException("Bitte gib eine gültige E-Mail-Adresse an.");
        }
        return email;
    }
}
