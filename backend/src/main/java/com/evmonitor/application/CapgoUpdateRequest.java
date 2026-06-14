package com.evmonitor.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request-Body, den der @capgo/capacitor-updater bei jedem App-Start an den
 * updateUrl-Endpoint sendet. Wir werten nur die aktuell installierte Bundle-Version
 * aus; alle weiteren Capgo-Felder werden ignoriert.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CapgoUpdateRequest(
        @JsonProperty("version_name") String versionName,
        @JsonProperty("platform") String platform) {
}
