package com.evmonitor.infrastructure.web;

import com.evmonitor.application.weather.WeatherOutlook;
import com.evmonitor.application.weather.WeatherOutlookService;
import com.evmonitor.infrastructure.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Wetter am Heimladeort des angemeldeten Nutzers. JWT Pflicht (Security-Default fuer /api/**). */
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherOutlookService outlookService;

    /** GET /api/weather/outlook: 200 mit Ausblick, 204 ohne Heimladeort oder ohne Wetterdaten. */
    @GetMapping("/outlook")
    public ResponseEntity<WeatherOutlook> outlook(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return outlookService.outlook(principal.getUser().getId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
