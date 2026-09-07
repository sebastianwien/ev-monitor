package com.evmonitor.infrastructure.external.xpeng;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class XpengDataApiClientTest {

    private static final String BASE = "https://open.test.xpeng.com/open/oauth2/queryData";
    private static final String APP_ID = "app123";
    private static final String APP_SECRET = "secret456";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private XpengDataApiClient client;

    private final XpengUserCredentials creds =
        new XpengUserCredentials("open-id-1", "access-token-1", "scope-1");

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new XpengDataApiClient(restTemplate, new ObjectMapper(),
            BASE, APP_ID, APP_SECRET, "EVMonitor");
        client.setNonceSupplier(() -> "fixednonce");
    }

    @Test
    void first_call_reports_exporting() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?")))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(jsonPath("$.openId").value("open-id-1"))
            .andExpect(jsonPath("$.accessToken").value("access-token-1"))
            .andExpect(jsonPath("$.scopeCode").value("scope-1"))
            .andExpect(jsonPath("$.enterpriseName").value("EVMonitor"))
            .andExpect(this::assertSignatureMatchesBody)
            .andRespond(withSuccess(
                "{\"code\":0,\"data\":\"DataFileExporting\",\"msg\":null,\"desc\":\"ok\"}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        XpengQueryResult result = client.queryData(creds);

        assertThat(result.status()).isEqualTo(XpengQueryResult.Status.EXPORTING);
        assertThat(result.downloadUrl()).isNull();
        server.verify();
    }

    @Test
    void ready_call_returns_download_url() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?")))
            .andRespond(withSuccess(
                "{\"code\":0,\"data\":\"https://dl.xpeng.com/export.zip?sig=x\",\"desc\":\"ok\"}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        XpengQueryResult result = client.queryData(creds);

        assertThat(result.status()).isEqualTo(XpengQueryResult.Status.READY);
        assertThat(result.downloadUrl()).isEqualTo("https://dl.xpeng.com/export.zip?sig=x");
    }

    @Test
    void export_failed_maps_to_failed_status() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?")))
            .andRespond(withSuccess(
                "{\"code\":0,\"data\":\"DataFileExportFailed\",\"desc\":\"gen failed\"}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        XpengQueryResult result = client.queryData(creds);

        assertThat(result.status()).isEqualTo(XpengQueryResult.Status.FAILED);
    }

    @Test
    void non_zero_code_throws_with_code() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?")))
            .andRespond(withSuccess(
                "{\"code\":4004001,\"data\":null,\"msg\":\"accessToken expired\"}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryData(creds))
            .isInstanceOf(XpengApiException.class)
            .satisfies(ex -> {
                XpengApiException e = (XpengApiException) ex;
                assertThat(e.getCode()).isEqualTo(4004001);
                assertThat(e.isTokenExpired()).isTrue();
                assertThat(e.isRateLimited()).isFalse();
            });
    }

    @Test
    void rate_limit_code_is_flagged() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?")))
            .andRespond(withSuccess(
                "{\"code\":12064024,\"msg\":\"download limit exceeded\"}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.queryData(creds))
            .isInstanceOf(XpengApiException.class)
            .satisfies(ex -> assertThat(((XpengApiException) ex).isRateLimited()).isTrue());
    }

    @Test
    void download_export_fetches_bytes() {
        String url = "https://dl.xpeng.com/export.zip?sig=x";
        byte[] payload = {0x50, 0x4B, 0x03, 0x04, 1, 2, 3};
        server.expect(requestTo(url))
            .andExpect(method(org.springframework.http.HttpMethod.GET))
            .andRespond(withSuccess(payload, org.springframework.http.MediaType.APPLICATION_OCTET_STREAM));

        byte[] bytes = client.downloadExport(url);

        assertThat(bytes).isEqualTo(payload);
        server.verify();
    }

    @Test
    void http_error_surfaces_as_api_exception() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "?")))
            .andRespond(withStatus(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.queryData(creds))
            .isInstanceOf(XpengApiException.class);
    }

    /**
     * Beweist, dass der Client GENAU den Body signiert, den er sendet:
     * sign-Query-Param muss zu SHA1 ueber (appId + body + nonce + secret) passen.
     */
    private void assertSignatureMatchesBody(org.springframework.http.client.ClientHttpRequest request) {
        var mock = (org.springframework.mock.http.client.MockClientHttpRequest) request;
        var params = UriComponentsBuilder.fromUri(mock.getURI()).build().getQueryParams();
        String appId = params.getFirst("appId");
        String nonce = params.getFirst("nonce");
        String sign = params.getFirst("sign");
        String body = mock.getBodyAsString();

        assertThat(appId).isEqualTo(APP_ID);
        assertThat(nonce).isEqualTo("fixednonce");
        assertThat(sign).isEqualTo(XpengApiSigner.sign(APP_ID, APP_SECRET, body, nonce));
    }
}
