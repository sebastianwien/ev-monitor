package com.evmonitor.infrastructure.web;

import com.evmonitor.application.ChargingProviderTariffResponse;
import com.evmonitor.application.ChargingProviderTariffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/charging-provider-tariffs")
@RequiredArgsConstructor
public class ChargingProviderTariffController {

    private final ChargingProviderTariffService service;

    @GetMapping
    public List<ChargingProviderTariffResponse> getAllTariffs() {
        return service.getAllCurrentTariffs();
    }

    @GetMapping("/emps")
    public List<String> getAvailableEmps() {
        return service.getAvailableEmps();
    }

    @GetMapping("/cpos")
    public List<String> getKnownCpos(@RequestParam(required = false) String country) {
        return service.getKnownCpoNames(country);
    }
}
