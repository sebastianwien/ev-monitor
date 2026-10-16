package com.evmonitor.infrastructure.web;

import com.evmonitor.application.vehiclespec.AdminSpecResponses;
import com.evmonitor.application.vehiclespec.AdminVehicleSpecificationService;
import com.evmonitor.application.vehiclespec.SpecCommand;
import com.evmonitor.domain.CarBrand;
import com.evmonitor.infrastructure.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin-Tab "Fahrzeug-Specs": Zeilen pflegen, Autos umhängen, Änderungsprotokoll. */
@RestController
@RequestMapping("/api/admin/vehicle-specifications")
@PreAuthorize("hasRole('ADMIN')")
@Validated
@RequiredArgsConstructor
public class AdminVehicleSpecificationController {

    private final AdminVehicleSpecificationService service;

    @GetMapping
    public AdminSpecResponses.Overview overview() {
        return service.overview();
    }

    @PostMapping
    public AdminSpecResponses.SaveResult create(@Valid @RequestBody SpecCommand cmd, @AuthenticationPrincipal UserPrincipal admin) {
        return service.create(admin.getUser().getId(), cmd);
    }

    @PutMapping("/{id}")
    public AdminSpecResponses.SaveResult update(@PathVariable UUID id, @Valid @RequestBody SpecCommand cmd,
                                                @AuthenticationPrincipal UserPrincipal admin) {
        return service.update(admin.getUser().getId(), id, cmd);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id, @RequestParam @Size(max = 500) String reason,
                       @AuthenticationPrincipal UserPrincipal admin) {
        service.delete(admin.getUser().getId(), id, reason);
    }

    @GetMapping("/{id}/changes")
    public List<AdminSpecResponses.ChangeEntry> changes(@PathVariable UUID id) {
        return service.changes(id);
    }

    @GetMapping("/{id}/cars")
    public List<AdminSpecResponses.CarForReassign> carsOfSpec(@PathVariable UUID id) {
        return service.carsOfSpec(id);
    }

    @GetMapping("/reassign-candidates")
    public List<AdminSpecResponses.CarForReassign> reassignCandidates(@RequestParam CarBrand.CarModel model) {
        return service.carsForReassign(model);
    }

    @PostMapping("/cars/{carId}/assign/{specId}")
    public void assignCar(@PathVariable UUID carId, @PathVariable UUID specId, @AuthenticationPrincipal UserPrincipal admin) {
        service.reassignCar(admin.getUser().getId(), carId, specId);
    }
}
