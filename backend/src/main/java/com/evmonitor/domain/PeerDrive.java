package com.evmonitor.domain;

import java.math.BigDecimal;

/**
 * Fahrverbrauch anderer Fahrer für die Reichweite (Stufe 2): zuerst die exakte Variante
 * ({@code vehicle_specification_id}), sonst das Modell über alle Varianten, je ab
 * {@link ModelDriveAverage#MIN_USERS} anderen Fahrern.
 *
 * @param consumptionKwhPer100km null, wenn weder Variante noch Modell die Mindestzahl erreichen
 * @param users                  Fahrer hinter dem Wert
 * @param scope                  woher der Wert kommt
 */
public record PeerDrive(BigDecimal consumptionKwhPer100km, int users, Scope scope) {

    public enum Scope { VARIANT, MODEL }

    public static final PeerDrive NONE = new PeerDrive(null, 0, Scope.MODEL);

    public static PeerDrive of(ModelDriveAverage variant, ModelDriveAverage model) {
        if (variant != null && variant.consumptionKwhPer100km() != null) {
            return new PeerDrive(variant.consumptionKwhPer100km(), variant.users(), Scope.VARIANT);
        }
        if (model == null) return NONE;
        return new PeerDrive(model.consumptionKwhPer100km(), model.users(), Scope.MODEL);
    }

    public boolean hasValue() {
        return consumptionKwhPer100km != null;
    }
}
