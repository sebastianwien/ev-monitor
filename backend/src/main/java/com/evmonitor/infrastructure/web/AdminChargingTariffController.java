package com.evmonitor.infrastructure.web;

import com.evmonitor.application.tariff.AdminChargingTariffService;
import com.evmonitor.application.tariff.AdminTariffResponses;
import com.evmonitor.application.tariff.TariffCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Admin-Tab "Ladekarten": gepflegte Tarife ansehen, anlegen, ändern, beenden. */
@RestController
@RequestMapping("/api/admin/charging-tariffs")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class AdminChargingTariffController {

    private final AdminChargingTariffService service;

    @GetMapping
    public AdminTariffResponses.Overview overview() {
        return service.overview();
    }

    @PostMapping
    public AdminTariffResponses.Row create(@Valid @RequestBody TariffCommand cmd) {
        return service.create(cmd);
    }

    @PutMapping("/{id}")
    public AdminTariffResponses.Row update(@PathVariable UUID id, @Valid @RequestBody TariffCommand cmd) {
        return service.update(id, cmd);
    }

    @PostMapping("/{id}/end")
    public AdminTariffResponses.Row end(@PathVariable UUID id, @Valid @RequestBody AdminTariffResponses.End body) {
        return service.end(id, body.validUntil());
    }
}
