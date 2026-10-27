package com.evmonitor.application.tariff;

import com.evmonitor.application.user.EmpCatalog;
import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.exception.NotFoundException;
import com.evmonitor.domain.exception.ValidationException;
import com.evmonitor.infrastructure.persistence.EmpEntity;
import com.evmonitor.infrastructure.persistence.JpaEmpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pflege der gepflegten Tarife ({@code charging_provider_tariffs}). Eine Preisänderung ist
 * "beenden" plus "neu anlegen", damit der alte Preis für vergangene Ladungen stehen bleibt;
 * "ändern" ist für Tippfehler und erneuert den Stand.
 *
 * <p>Betreiber oder Tier, nicht beides: ein Tarif mit Betreiber gilt nur dort, einer mit Tier
 * dort, wo das Tier-Mapping ihn hinlegt, einer ohne beides im eigenen Netz des Anbieters.
 * Der Referenzpreis des Rechners (Schnellladen ohne Abo) liest dieselbe Tabelle, sein Cache
 * wird bei jeder Änderung verworfen.
 */
@Service
@RequiredArgsConstructor
public class AdminChargingTariffService {

    private final ChargingProviderTariffRepository tariffRepository;
    private final EmpCatalog empCatalog;
    private final JpaEmpRepository empRepository;

    public AdminTariffResponses.Overview overview() {
        List<AdminTariffResponses.Row> rows = tariffRepository.findAll().stream()
                .sorted(Comparator.comparing(ChargingProviderTariff::getEmpName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(t -> t.getTariffVariant() == null ? "" : t.getTariffVariant())
                        .thenComparing(t -> t.getChargingType().name())
                        .thenComparing(ChargingProviderTariff::getValidFrom, Comparator.reverseOrder()))
                .map(AdminTariffResponses.Row::of)
                .toList();
        return new AdminTariffResponses.Overview(rows, empCatalog.allNames(), tariffRepository.findAllKnownCpoNames());
    }

    @Transactional
    @CacheEvict(value = "chargingReferencePrices", allEntries = true)
    public AdminTariffResponses.Row create(TariffCommand cmd) {
        return AdminTariffResponses.Row.of(tariffRepository.save(apply(ChargingProviderTariff.builder().id(UUID.randomUUID()), cmd)));
    }

    @Transactional
    @CacheEvict(value = "chargingReferencePrices", allEntries = true)
    public AdminTariffResponses.Row update(UUID id, TariffCommand cmd) {
        ChargingProviderTariff existing = find(id);
        return AdminTariffResponses.Row.of(tariffRepository.save(apply(existing.toBuilder(), cmd)));
    }

    @Transactional
    @CacheEvict(value = "chargingReferencePrices", allEntries = true)
    public AdminTariffResponses.Row end(UUID id, LocalDate validUntil) {
        ChargingProviderTariff existing = find(id);
        if (validUntil.isBefore(existing.getValidFrom())) {
            throw new ValidationException("Der letzte Tag liegt vor dem ersten.");
        }
        return AdminTariffResponses.Row.of(tariffRepository.save(existing.toBuilder().validUntil(validUntil).build()));
    }

    /** Seite geprüft, Werte stimmen: nur der Stand wird heute. */
    @Transactional
    public AdminTariffResponses.Row verify(UUID id) {
        ChargingProviderTariff existing = find(id);
        return AdminTariffResponses.Row.of(tariffRepository.save(existing.toBuilder().lastVerifiedAt(LocalDateTime.now()).build()));
    }

    private ChargingProviderTariff find(UUID id) {
        return tariffRepository.findById(id).orElseThrow(() -> new NotFoundException("Tarif nicht gefunden."));
    }

    private ChargingProviderTariff apply(ChargingProviderTariff.ChargingProviderTariffBuilder b, TariffCommand cmd) {
        if (cmd.chargingType() == ChargingType.UNKNOWN) throw new ValidationException("Ladeart ist AC oder DC.");
        if (cmd.validUntil() != null && cmd.validUntil().isBefore(cmd.validFrom())) {
            throw new ValidationException("Gültig bis liegt vor Gültig ab.");
        }
        String cpo = blankToNull(cmd.cpoName());
        if (cpo != null && cmd.priceTier() != null) {
            throw new ValidationException("Betreiber oder Tier, nicht beides.");
        }
        if (cpo != null && cmd.appliesElsewhere()) {
            throw new ValidationException("Ein Tarif mit Betreiber gilt nur dort, nicht überall.");
        }
        if (cmd.minPowerKw() != null && cmd.chargingType() != ChargingType.DC) {
            throw new ValidationException("Leistungsgrenze nur bei DC.");
        }
        return b.empName(empOrCreate(cmd.empName()))
                .tariffVariant(blankToNull(cmd.tariffVariant()))
                .cpoName(cpo == null ? null : knownCpo(cpo))
                .priceTier(cmd.priceTier())
                .chargingType(cmd.chargingType())
                .pricePerKwh(cmd.pricePerKwh())
                .sessionFeeEur(orZero(cmd.sessionFeeEur()))
                .monthlyFeeEur(orZero(cmd.monthlyFeeEur()))
                .blockingFeePerMin(cmd.blockingFeePerMin())
                .blockingFeeAfterMin(cmd.blockingFeeAfterMin())
                .dynamicPricing(cmd.dynamicPricing())
                .validFrom(cmd.validFrom())
                .validUntil(cmd.validUntil())
                .sourceUrl(blankToNull(cmd.sourceUrl()))
                .appliesElsewhere(cmd.appliesElsewhere())
                .minPowerKw(cmd.minPowerKw())
                .blockingFeeCapEur(cmd.blockingFeeCapEur())
                .lastVerifiedAt(LocalDateTime.now())
                .build();
    }

    /** Vorhandener Anbieter in seiner Katalog-Schreibweise, sonst neu im Katalog. */
    private String empOrCreate(String name) {
        String trimmed = name.trim();
        return empCatalog.resolve(trimmed).orElseGet(() -> {
            EmpEntity e = new EmpEntity();
            e.setName(trimmed);
            return empRepository.save(e).getName();
        });
    }

    /** Betreiber nur aus dem Katalog der Ladenetze, in dessen Schreibweise. */
    private String knownCpo(String name) {
        String wanted = name.trim().toLowerCase(Locale.ROOT);
        return tariffRepository.findAllKnownCpoNames().stream()
                .filter(n -> n.toLowerCase(Locale.ROOT).equals(wanted))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Betreiber steht nicht im Katalog: " + name.trim()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
