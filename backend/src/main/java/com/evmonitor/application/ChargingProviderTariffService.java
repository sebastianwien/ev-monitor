package com.evmonitor.application;

import com.evmonitor.domain.ChargingProviderTariff;
import com.evmonitor.domain.ChargingProviderTariffRepository;
import com.evmonitor.domain.ChargingType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChargingProviderTariffService {

    private final ChargingProviderTariffRepository tariffRepository;

    public ChargingProviderTariffService(ChargingProviderTariffRepository tariffRepository) {
        this.tariffRepository = tariffRepository;
    }

    public List<ChargingProviderTariffResponse> getAllCurrentTariffs() {
        return tariffRepository.findAllCurrentTariffs().stream()
                .map(ChargingProviderTariffResponse::new)
                .toList();
    }

    public List<String> getAvailableEmps() {
        return tariffRepository.findAllEmpNames();
    }

    public List<String> getKnownCpoNames() {
        return tariffRepository.findAllKnownCpoNames();
    }

    public List<String> getTariffVariantsForEmp(String empName) {
        return tariffRepository.findDistinctTariffVariantsByEmp(empName);
    }

    /**
     * Löst den besten Tarif für eine konkrete Ladesession auf.
     * Berücksichtigt CPO-spezifisches Tier-Mapping (z.B. Maingau Low/Standard/High).
     * Gibt empty zurück wenn keine Daten vorhanden.
     */
    public Optional<ChargingProviderTariffResponse> resolveTariff(String empName, String tariffVariant, String cpoName, ChargingType chargingType) {
        return tariffRepository.findBestTariff(empName, tariffVariant, cpoName, chargingType)
                .map(ChargingProviderTariffResponse::new);
    }
}
