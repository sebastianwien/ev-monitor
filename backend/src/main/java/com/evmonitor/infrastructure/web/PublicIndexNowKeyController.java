package com.evmonitor.infrastructure.web;

import com.evmonitor.application.seo.SeoAuditProperties;
import com.evmonitor.infrastructure.external.indexnow.IndexNowClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IndexNow-Schlüsseldatei. nginx reicht {@code /indexnow-key.txt} hierher durch. Der Schlüssel ist laut Protokoll
 * öffentlich und beweist nur, dass Meldungen für den Host von dessen Betreiber kommen.
 */
@RestController
@RequiredArgsConstructor
public class PublicIndexNowKeyController {

    private final IndexNowClient indexNow;
    private final SeoAuditProperties properties;

    @GetMapping("/api/public/seo/indexnow-key")
    public ResponseEntity<String> key() {
        if (!indexNow.isEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(properties.getIndexnowKey().trim());
    }
}
