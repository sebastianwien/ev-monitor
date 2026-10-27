package com.evmonitor.application.tariff.crawl;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.JpaTariffProposalRepository;
import com.evmonitor.infrastructure.persistence.JpaTariffSourceRepository;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity.Kind;
import com.evmonitor.infrastructure.persistence.TariffProposalEntity.Status;
import com.evmonitor.infrastructure.persistence.TariffSourceEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Führt die Vorschläge eines Crawler-Laufs. Ein Vorschlag ist derselbe, wenn Art, Tarif und Werte
 * gleich sind (Fingerabdruck); das Zitat zählt nicht mit, das Modell formuliert es jedes Mal anders.
 *
 * <p>Eine Preisänderung ohne Haltegrund wird übernommen, sobald ein zweiter Lauf, frühestens nach
 * {@code tariff-crawl.confirm-after-hours}, denselben Wert liefert. Alles andere wartet auf den
 * Admin. Ein offener Vorschlag, den ein späterer Lauf nicht mehr sieht, ist überholt.
 */
@Service
@RequiredArgsConstructor
public class TariffProposalBook {

    static final ObjectMapper JSON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final JpaTariffProposalRepository proposals;
    private final JpaTariffSourceRepository sources;
    private final TariffChangeApplier applier;
    private final ChargingProviderTariffRepository tariffs;
    private final TariffCrawlProperties props;

    /** Ein Unterschied aus einem Lauf, noch ohne Status. */
    public record Draft(Kind kind, UUID tariffId, Map<String, Object> proposed, String evidence, String holdReason) {

        boolean autoConfirmable() {
            return kind == Kind.CHANGE && holdReason == null;
        }

        String fingerprint() {
            return sha256(kind + "|" + tariffId + "|" + json(new TreeMap<>(proposed)));
        }
    }

    /** {@code needAdmin}: in diesem Lauf neu entstandene Vorschläge, die ein Admin entscheiden muss. */
    public record Recorded(int applied, List<TariffProposalEntity> needAdmin) {}

    public static Map<String, Object> toMap(Object value) {
        return JSON.convertValue(value, new TypeReference<>() {});
    }

    @Transactional
    public Recorded record(TariffSourceEntity source, List<Draft> drafts, LocalDateTime now) {
        Set<String> seen = new HashSet<>();
        List<TariffProposalEntity> needAdmin = new ArrayList<>();
        int applied = 0;
        for (Draft d : drafts) {
            String fp = d.fingerprint();
            if (!seen.add(fp)) continue;
            Optional<TariffProposalEntity> open = proposals.findBySourceIdAndFingerprintAndStatus(source.getId(), fp, Status.PENDING);
            if (open.isPresent()) {
                TariffProposalEntity p = open.get();
                p.setSeenCount(p.getSeenCount() + 1);
                p.setLastSeenAt(now);
                p.setEvidence(d.evidence());
                proposals.save(p);
                if (d.autoConfirmable() && !now.isBefore(p.getFirstSeenAt().plusHours(props.getConfirmAfterHours()))) {
                    if (apply(p, "AUTO", now)) applied++;
                }
                continue;
            }
            TariffProposalEntity p = new TariffProposalEntity();
            p.setId(UUID.randomUUID());
            p.setSourceId(source.getId());
            p.setEmpName(source.getEmpName());
            p.setKind(d.kind());
            p.setTariffId(d.tariffId());
            p.setProposed(d.proposed());
            p.setFingerprint(fp);
            p.setEvidence(d.evidence());
            p.setHoldReason(d.holdReason());
            p.setStatus(Status.PENDING);
            p.setFirstSeenAt(now);
            p.setLastSeenAt(now);
            proposals.save(p);
            if (!d.autoConfirmable()) needAdmin.add(p);
        }
        for (TariffProposalEntity stale : proposals.findBySourceIdAndStatus(source.getId(), Status.PENDING)) {
            if (seen.contains(stale.getFingerprint())) continue;
            decide(stale, Status.SUPERSEDED, "AUTO", now);
        }
        return new Recorded(applied, needAdmin);
    }

    public boolean hasOpen(UUID sourceId) {
        return !proposals.findBySourceIdAndStatus(sourceId, Status.PENDING).isEmpty();
    }

    /** Offene Preisänderung ohne Haltegrund: ein zweiter Lauf kann sie bestätigen. */
    public boolean hasConfirmable(UUID sourceId) {
        return proposals.findBySourceIdAndStatus(sourceId, Status.PENDING).stream()
                .anyMatch(p -> p.getKind() == Kind.CHANGE && p.getHoldReason() == null);
    }

    public void touch(List<ChargingProviderTariff> rows, LocalDateTime now) {
        if (!rows.isEmpty()) applier.touch(rows, now);
    }

    /** Admin übernimmt. false, wenn der Tarif inzwischen anders gepflegt wurde (Vorschlag überholt). */
    @Transactional
    public boolean applyByAdmin(UUID proposalId, LocalDateTime now) {
        return apply(pending(proposalId), "ADMIN", now);
    }

    @Transactional
    public void rejectByAdmin(UUID proposalId, LocalDateTime now) {
        decide(pending(proposalId), Status.REJECTED, "ADMIN", now);
    }

    private TariffProposalEntity pending(UUID id) {
        TariffProposalEntity p = proposals.findById(id).orElseThrow(() -> new NotFoundException("Vorschlag nicht gefunden."));
        if (p.getStatus() != Status.PENDING) throw new ValidationException("Vorschlag ist schon entschieden.");
        return p;
    }

    private boolean apply(TariffProposalEntity p, String by, LocalDateTime now) {
        TariffSourceEntity source = sources.findById(p.getSourceId()).orElseThrow();
        switch (p.getKind()) {
            case CHANGE, MISSING -> {
                Optional<ChargingProviderTariff> current = Optional.ofNullable(p.getTariffId()).flatMap(tariffs::findById)
                        .filter(t -> t.getValidUntil() == null);
                if (current.isEmpty()) {
                    decide(p, Status.SUPERSEDED, by, now);
                    return false;
                }
                if (p.getKind() == Kind.CHANGE) applier.change(current.get(), extracted(p), now);
                else applier.end(current.get(), now);
            }
            case NEW -> applier.create(source.getEmpName(), source.getUrl(), extracted(p), now);
            case MAPPING -> applier.assignTier(source.getEmpName(), (String) p.getProposed().get("operator"), (String) p.getProposed().get("tier"));
            case WATCH_CHANGED -> {
                source.setApprovedHash((String) p.getProposed().get("pageFingerprint"));
                sources.save(source);
                applier.touch(tariffs.findValidOn(now.toLocalDate()).stream()
                        .filter(t -> source.getUrl().equals(t.getSourceUrl())).toList(), now);
            }
        }
        decide(p, Status.APPLIED, by, now);
        return true;
    }

    private void decide(TariffProposalEntity p, Status status, String by, LocalDateTime now) {
        p.setStatus(status);
        p.setDecidedAt(now);
        p.setDecidedBy(by);
        proposals.save(p);
    }

    private static ExtractedTariff extracted(TariffProposalEntity p) {
        return JSON.convertValue(p.getProposed(), ExtractedTariff.class);
    }

    private static String json(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
