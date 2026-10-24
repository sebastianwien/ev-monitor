package com.evmonitor.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fahrzeugname aus einer Quelle (Entscheidung 24.10.2026): Das Backend liefert den fertigen
 * Anzeigenamen, das Frontend formatiert nie aus Enum-Namen. Die Marke heißt in der Anzeige "VW",
 * der URL-Slug der Modellseiten bleibt "Volkswagen".
 */
class CarBrandLabelTest {

    @Test
    void vwHeisstInDerAnzeigeVwUndImSlugWeiterVolkswagen() {
        assertThat(CarBrand.VW.getLabel()).isEqualTo("VW");
        assertThat(CarBrand.VW.getDisplayString()).isEqualTo("Volkswagen");
    }

    @Test
    void andereMarkenBehaltenIhrLabel() {
        assertThat(CarBrand.SKODA.getLabel()).isEqualTo(CarBrand.SKODA.getDisplayString());
        assertThat(CarBrand.BMW.getLabel()).isEqualTo("BMW");
    }

    @Test
    void vollerNameIstMarkeUndModell() {
        assertThat(CarBrand.CarModel.ID_5.getFullDisplayName()).isEqualTo("VW ID.5");
        assertThat(CarBrand.CarModel.MODEL_3.getFullDisplayName()).isEqualTo("Tesla Model 3");
    }

    @Test
    void markeWirdNichtDoppelt_wennDasModellSchonMitIhrBeginnt() {
        assertThat(CarBrand.CarModel.POLESTAR_2.getFullDisplayName()).isEqualTo("Polestar 2");
    }

    @Test
    void keinAnzeigenameTraegtEnumSchreibweise() {
        for (CarBrand.CarModel m : CarBrand.CarModel.values()) {
            assertThat(m.getFullDisplayName()).as(m.name())
                    .doesNotContain("_")
                    .isNotEqualTo(m.name());
        }
        for (CarBrand b : CarBrand.values()) {
            assertThat(b.getLabel()).as(b.name()).doesNotContain("_").isNotBlank();
        }
    }
}
