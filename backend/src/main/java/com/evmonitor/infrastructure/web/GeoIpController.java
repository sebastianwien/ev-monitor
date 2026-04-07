package com.evmonitor.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/geoip")
public class GeoIpController {

    @GetMapping("/country")
    public ResponseEntity<Map<String, String>> getCountry(HttpServletRequest request) {
        String country = request.getHeader("X-Country-Code");
        if (country != null && country.length() == 2) {
            return ResponseEntity.ok(Map.of("country", country.toUpperCase()));
        }
        return ResponseEntity.ok(Map.of());
    }
}
