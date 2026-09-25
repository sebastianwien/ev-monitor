package com.evmonitor.application.ingest;

import com.evmonitor.domain.ChargingType;
import com.evmonitor.domain.EnergySource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LenientEnumsTest {

    @Test
    void chargingType_knownValue_orUnknownForMissingAndForeign() {
        assertThat(LenientEnums.chargingType("DC")).isEqualTo(ChargingType.DC);
        assertThat(LenientEnums.chargingType(null)).isEqualTo(ChargingType.UNKNOWN);
        assertThat(LenientEnums.chargingType("HPC")).isEqualTo(ChargingType.UNKNOWN);
    }

    @Test
    void energySource_knownValue_orNullForMissingAndForeign() {
        assertThat(LenientEnums.energySource("OEM_MEASURED")).isEqualTo(EnergySource.OEM_MEASURED);
        assertThat(LenientEnums.energySource(null)).isNull();
        assertThat(LenientEnums.energySource("GUESSED")).isNull();
    }
}
