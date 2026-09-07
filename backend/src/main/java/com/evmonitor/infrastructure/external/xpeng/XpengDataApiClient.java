package com.evmonitor.infrastructure.external.xpeng;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Client fuer die XPeng EU-Data-Act Open API ({@code POST /open/oauth2/queryData}).
 *
 * <p>Signatur ueber {@link XpengApiSigner}. Der Datenexport laeuft als Two-Step-Polling:
 * der erste Aufruf stoesst den Export an ({@code DataFileExporting}), Folgeaufrufe mit
 * denselben Credentials liefern schliesslich eine kurzlebige Download-URL oder
 * {@code DataFileExportFailed}. Das Poll-/Wait-Timing orchestriert der aufrufende Service.
 *
 * <p>Enterprise-Credentials ({@code appId}/{@code appSecret}) kommen aus Env-Vars und
 * werden niemals geloggt oder im Response-DTO nach aussen gereicht.
 */
@Service
public class XpengDataApiClient {

    private static final Logger log = LoggerFactory.getLogger(XpengDataApiClient.class);

    private static final String DATA_EXPORTING = "DataFileExporting";
    private static final String DATA_EXPORT_FAILED = "DataFileExportFailed";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String appId;
    private final String appSecret;
    private final String enterpriseName;

    private final SecureRandom random = new SecureRandom();
    private Supplier<String> nonceSupplier = this::randomNonce;

    public XpengDataApiClient(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        @Value("${xpeng.api.base-url:https://open.xpeng.com/open/oauth2/queryData}") String baseUrl,
        @Value("${xpeng.api.app-id:}") String appId,
        @Value("${xpeng.api.app-secret:}") String appSecret,
        @Value("${xpeng.api.enterprise-name:EVMonitor}") String enterpriseName
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.appId = appId;
        this.appSecret = appSecret;
        this.enterpriseName = enterpriseName;
    }

    /**
     * Ein queryData-Aufruf. Wirft {@link XpengApiException} bei HTTP-Fehler oder
     * fachlichem Fehlercode ({@code code != 0}).
     */
    public XpengQueryResult queryData(XpengUserCredentials creds) {
        String body = serializeBody(creds);
        String nonce = nonceSupplier.get();
        String sign = XpengApiSigner.sign(appId, appSecret, body, nonce);

        String url = UriComponentsBuilder.fromUriString(baseUrl)
            .queryParam("appId", appId)
            .queryParam("nonce", nonce)
            .queryParam("sign", sign)
            .build()
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        JsonNode response;
        try {
            response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), JsonNode.class);
        } catch (RestClientException e) {
            throw new XpengApiException(XpengApiException.NO_CODE, "XPeng queryData HTTP-Fehler", e);
        }
        return parse(response);
    }

    /** Laedt die vom queryData gelieferte (kurzlebige) Export-URL als Rohbytes. */
    public byte[] downloadExport(String downloadUrl) {
        try {
            return restTemplate.getForObject(downloadUrl, byte[].class);
        } catch (RestClientException e) {
            throw new XpengApiException(XpengApiException.NO_CODE, "XPeng Export-Download fehlgeschlagen", e);
        }
    }

    private XpengQueryResult parse(JsonNode response) {
        if (response == null) {
            throw new XpengApiException(XpengApiException.NO_CODE, "Leere Antwort von XPeng queryData");
        }
        int code = response.path("code").asInt(XpengApiException.NO_CODE);
        if (code != 0) {
            String msg = firstNonBlank(
                text(response, "msg"),
                text(response, "desc"),
                "XPeng Fehlercode " + code);
            log.warn("XPeng queryData Fehlercode {}: {}", code, msg);
            throw new XpengApiException(code, msg);
        }

        JsonNode dataNode = response.path("data");
        String data = dataNode.isNull() || dataNode.isMissingNode() ? null : dataNode.asText();

        if (DATA_EXPORTING.equals(data)) {
            return XpengQueryResult.exporting();
        }
        if (DATA_EXPORT_FAILED.equals(data)) {
            return XpengQueryResult.failed();
        }
        if (data != null && (data.startsWith("http://") || data.startsWith("https://"))) {
            return XpengQueryResult.ready(data);
        }
        throw new XpengApiException(XpengApiException.NO_CODE, "Unerwartete data-Antwort von XPeng: " + data);
    }

    private String serializeBody(XpengUserCredentials creds) {
        try {
            return objectMapper.writeValueAsString(
                new QueryBody(creds.openId(), creds.accessToken(), enterpriseName, creds.scopeCode()));
        } catch (JsonProcessingException e) {
            throw new XpengApiException(XpengApiException.NO_CODE, "Body-Serialisierung fehlgeschlagen", e);
        }
    }

    private String randomNonce() {
        byte[] bytes = new byte[8];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Test-Hook, um den Nonce deterministisch zu machen. */
    void setNonceSupplier(Supplier<String> nonceSupplier) {
        this.nonceSupplier = nonceSupplier;
    }

    private static String text(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isNull() || child.isMissingNode() ? null : child.asText();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /** Feste Feldreihenfolge fuer eine reproduzierbare Body-Serialisierung. */
    private record QueryBody(String openId, String accessToken, String enterpriseName, String scopeCode) {
    }
}
