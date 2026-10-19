package com.evmonitor.application.seo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Zugang zur Google Search Console. Leerer Key = Feature aus. */
@ConfigurationProperties(prefix = "gsc")
@Component
@Getter
@Setter
public class GscProperties {

    /** Service-Account-JSON, Base64-kodiert (eine Zeile) oder roh. Nie loggen. */
    private String serviceAccountJson = "";

    /** Property in der Search Console, z. B. {@code sc-domain:example.com}. */
    private String siteUrl = "sc-domain:ev-monitor.net";

    /** GSC hält 16 Monate; älteres wird nach dem Sync gelöscht. */
    private int retentionDays = 490;

    public boolean isConfigured() {
        return serviceAccountJson != null && !serviceAccountJson.isBlank();
    }
}
