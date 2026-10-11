package com.evmonitor.application;

import java.math.BigDecimal;

/**
 * The ranking values the model page story needs besides the model stats.
 *
 * @param fastChargePowerKw typical DC power of this model from the ranking, null under too few sessions
 * @param fleetWinterFactor median winter ÷ summer consumption over the models with both seasons,
 *                          null under {@link ModelStoryContextService#MIN_FLEET_MODELS} models
 * @param fleetWinterModels how many models that median rests on
 * @param minRealKwhPer100km most frugal driver of the model (all variants), null when not ranked
 * @param maxRealKwhPer100km thirstiest driver of the model (all variants), null when not ranked
 */
public record ModelStoryContext(BigDecimal fastChargePowerKw, BigDecimal fleetWinterFactor, int fleetWinterModels,
                                BigDecimal minRealKwhPer100km, BigDecimal maxRealKwhPer100km) {}
