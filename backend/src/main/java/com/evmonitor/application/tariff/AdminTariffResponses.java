package com.evmonitor.application.tariff;

import com.evmonitor.domain.ChargingProviderTariff;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Antworten des Admin-Tabs "Ladekarten". */
public final class AdminTariffResponses {

    private AdminTariffResponses() {}

    /** Alle Tarife plus die Kataloge für die Auswahl im Formular. */
    public record Overview(List<Row> tariffs, List<String> emps, List<String> cpos) {}

    public record Row(UUID id, String empName, String tariffVariant, String cpoName, String priceTier,
                      String chargingType, BigDecimal pricePerKwh, BigDecimal sessionFeeEur, BigDecimal monthlyFeeEur,
                      BigDecimal blockingFeePerMin, Integer blockingFeeAfterMin, boolean dynamicPricing,
                      LocalDate validFrom, LocalDate validUntil, String sourceUrl, LocalDateTime lastVerifiedAt) {

        public static Row of(ChargingProviderTariff t) {
            return new Row(t.getId(), t.getEmpName(), t.getTariffVariant(), t.getCpoName(), t.getPriceTier(),
                    t.getChargingType().name(), t.getPricePerKwh(), t.getSessionFeeEur(), t.getMonthlyFeeEur(),
                    t.getBlockingFeePerMin(), t.getBlockingFeeAfterMin(), t.isDynamicPricing(),
                    t.getValidFrom(), t.getValidUntil(), t.getSourceUrl(), t.getLastVerifiedAt());
        }
    }

    /** Gültigkeit beenden: letzter Tag, an dem der Tarif noch gilt. */
    public record End(@jakarta.validation.constraints.NotNull(message = "Letzter Tag ist Pflicht.") LocalDate validUntil) {}
}
