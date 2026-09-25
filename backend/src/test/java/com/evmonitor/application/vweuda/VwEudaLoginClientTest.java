package com.evmonitor.application.vweuda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Passwort-Login gegen aufgezeichnete, bereinigte VW-ID-Antworten (src/test/resources/euda).
 * Der Fake liefert die Antworten in Reihenfolge und merkt sich jede Anfrage - so ist belegbar,
 * dass das Passwort genau einmal an genau eine URL geht und danach nur SSO-Cookies uebrig bleiben.
 */
class VwEudaLoginClientTest {

    private static final String CID = "3ea88bf9-1d4e-4a68-b3ad-4098c1f1d246@apps_vw-dilab_com";
    private static final String IDP = "https://identity.vwgroup.io";
    private static final String PORTAL = "https://eu-data-act.drivesomethinggreater.com";

    private ScriptedHttp http;
    private VwEudaLoginClient client;
    private String identifierPage;
    private String passwordPage;

    @BeforeEach
    void setUp() throws IOException {
        http = new ScriptedHttp();
        client = new VwEudaLoginClient(http, "skoda");
        identifierPage = fixture("euda/login_identifier_page.html");
        passwordPage = fixture("euda/login_authenticate_page_spa.html");
    }

    private void scriptLoginUpToPassword() {
        http.respond(200, "", "Set-Cookie", "affinity=aff1; Path=/");                       // GET portal /
        http.respond(302, "", "Location", IDP + "/signin-service/v1/signin/" + CID + "?relayState=FIXTURE_RELAYSTATE");
        http.respond(200, identifierPage, "Set-Cookie", "SESSION=sess1; Path=/; HttpOnly");      // Identifier-Seite
        http.respond(303, "", "Location", IDP + "/signin-service/v1/" + CID + "/login/authenticate?relayState=FIXTURE_RELAYSTATE");
        http.respond(200, passwordPage);                                                       // SPA-Passwortseite
    }

    private void scriptSuccessChain() {
        http.respond(302, "", "Location", IDP + "/oidc/v1/oauth/sso?clientId=" + CID + "&relayState=x&userId=u&HMAC=h");
        http.respond(302, "", "Location", PORTAL + "/login?state=de__de__SKODA&code=CODE1",
                "Set-Cookie", "s_uuid=IDP_SESSION; Max-Age=86400; Path=/",
                "Set-Cookie", "d_uuid=IDP_DEVICE; Max-Age=31536000; Path=/");
        http.respond(302, "", "Location", "/services/callbacklogin?state=de__de__SKODA&code=CODE1");
        http.respond(301, "", "Location", "/content/euda/de/de/user.html",
                "Set-Cookie", "ath=ATH; Max-Age=3600; Path=/",
                "Set-Cookie", "access_token=PORTAL_TOKEN; Max-Age=3600; Path=/");
        http.respond(200, "<html>Abmelden</html>");
    }

    @Test
    void login_happyPath_walksRecordedFlowAndExportsOnlySsoCookies() {
        scriptLoginUpToPassword();
        scriptSuccessChain();

        Map<String, String> cookies = client.login("user@example.com", "secret-pw");

        List<VwEudaHttp.Request> r = http.requests;
        assertEquals("GET", r.get(0).method());
        assertEquals(PORTAL + "/", r.get(0).url());

        String authorize = r.get(1).url();
        assertTrue(authorize.startsWith(IDP + "/oidc/v1/authorize?"), authorize);
        assertTrue(authorize.contains("client_id=" + enc(CID)));
        assertTrue(authorize.contains("scope=openid+cars+profile"));
        assertTrue(authorize.contains("state=de__de__SKODA"));
        assertTrue(authorize.contains("redirect_uri=" + enc(PORTAL + "/login")));
        assertTrue(authorize.contains("prompt=login"));

        VwEudaHttp.Request identifierPost = r.get(3);
        assertEquals("POST", identifierPost.method());
        assertEquals(IDP + "/signin-service/v1/" + CID + "/login/identifier", identifierPost.url());
        Map<String, String> f1 = form(identifierPost);
        assertEquals("FIXTURE_CSRF", f1.get("_csrf"));
        assertEquals("FIXTURE_RELAYSTATE", f1.get("relayState"));
        assertEquals("FIXTURE_HMAC", f1.get("hmac"));
        assertEquals("user@example.com", f1.get("email"));
        assertTrue(identifierPost.headers().get("Cookie").contains("SESSION=sess1"));
        assertFalse(identifierPost.headers().get("Cookie").contains("affinity"), "Portal-Cookie nicht an den IDP");

        VwEudaHttp.Request passwordPost = r.get(5);
        assertEquals("POST", passwordPost.method());
        assertEquals(IDP + "/signin-service/v1/" + CID + "/login/authenticate", passwordPost.url());
        Map<String, String> f2 = form(passwordPost);
        assertEquals("FIXTURE_CSRF", f2.get("_csrf"));
        assertEquals("user@example.com", f2.get("email"));
        assertEquals("secret-pw", f2.get("password"));

        assertEquals(PORTAL + "/content/euda/de/de/user.html", r.get(9).url());
        assertEquals(10, r.size());

        // Nur die SSO-Cookies der VW-ID verlassen den Client - kein Portal-Token, kein Passwort
        assertEquals(Map.of("s_uuid", "IDP_SESSION", "d_uuid", "IDP_DEVICE"), cookies);
    }

    @Test
    void login_neverSendsPasswordAnywhereButThePasswordPost() {
        scriptLoginUpToPassword();
        scriptSuccessChain();
        client.login("user@example.com", "secret-pw");

        for (int i = 0; i < http.requests.size(); i++) {
            if (i == 5) continue;
            VwEudaHttp.Request req = http.requests.get(i);
            assertFalse(String.valueOf(req.formBody()).contains("secret-pw"), "Passwort in Request " + i);
            assertFalse(req.url().contains("secret-pw"));
            assertFalse(String.valueOf(req.headers()).contains("secret-pw"));
        }
    }

    @Test
    void redirectToForeignHost_abortsBeforeAnyRequestLeavesVwHosts() {
        // Kompromittierte Seite oder MITM leitet auf fremden Host: kein Request dorthin, erst recht kein Passwort
        http.respond(200, "", "Set-Cookie", "affinity=aff1; Path=/");
        http.respond(302, "", "Location", "https://evil.example.org/signin?relayState=x");

        assertThrows(VwEudaAuthException.InteractionRequired.class, () -> client.login("user@example.com", "secret-pw"));
        assertEquals(2, http.requests.size());
        http.requests.forEach(r -> assertFalse(r.url().contains("evil.example.org"), r.url()));
    }

    @Test
    void redirectToPlainHttp_aborts_evenOnVwHost() {
        http.respond(200, "", "Set-Cookie", "affinity=aff1; Path=/");
        http.respond(302, "", "Location", "http://identity.vwgroup.io/signin-service/v1/signin/" + CID);

        assertThrows(VwEudaAuthException.InteractionRequired.class, () -> client.login("user@example.com", "secret-pw"));
        assertEquals(2, http.requests.size());
    }

    @Test
    void passwordPostTargetOutsideIdp_isRefused() {
        // Passwortseite (per Redirect) auf dem Portal-Host statt der VW-ID: der Passwort-POST darf nur an die VW-ID gehen
        http.respond(200, "", "Set-Cookie", "affinity=aff1; Path=/");
        http.respond(302, "", "Location", IDP + "/signin-service/v1/signin/" + CID + "?relayState=FIXTURE_RELAYSTATE");
        http.respond(200, identifierPage);
        http.respond(303, "", "Location", PORTAL + "/login/authenticate?relayState=FIXTURE_RELAYSTATE");
        http.respond(200, passwordPage);

        assertThrows(VwEudaAuthException.InteractionRequired.class, () -> client.login("user@example.com", "secret-pw"));
        http.requests.forEach(r -> assertFalse(String.valueOf(r.formBody()).contains("secret-pw"), "Passwort in " + r.url()));
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        scriptLoginUpToPassword();
        String errorPage = passwordPage.replace("\"error\":null",
                "\"error\":{\"errorCode\":\"login.errors.password_invalid\"}");
        assertNotEquals(passwordPage, errorPage, "Fixture muss ein error-Feld haben");
        http.respond(302, "", "Location", IDP + "/signin-service/v1/" + CID + "/login/authenticate?relayState=FIXTURE_RELAYSTATE");
        http.respond(200, errorPage);

        assertThrows(VwEudaAuthException.InvalidCredentials.class, () -> client.login("user@example.com", "wrong"));
    }

    @Test
    void login_termsInterstitial_throwsInteractionRequired() {
        scriptLoginUpToPassword();
        http.respond(302, "", "Location", IDP + "/signin-service/v1/" + CID + "/terms-and-conditions?relayState=x");
        http.respond(200, "<script>window._IDK = { templateModel: {\"template\":\"termsAndConditions\",\"hmac\":\"h\"} };</script>");

        VwEudaAuthException.InteractionRequired ex = assertThrows(VwEudaAuthException.InteractionRequired.class,
                () -> client.login("user@example.com", "pw"));
        assertEquals("termsAndConditions", ex.reason());
    }

    @Test
    void login_idpDown_throwsPortalUnavailable() {
        http.respond(200, "");
        http.respond(503, "<html>maintenance</html>");

        assertThrows(VwEudaAuthException.PortalUnavailable.class, () -> client.login("user@example.com", "pw"));
    }

    @Test
    void unknownBrand_isRejectedBeforeAnyRequest() {
        assertThrows(IllegalArgumentException.class, () -> new VwEudaLoginClient(http, "tesla"));
        assertTrue(http.requests.isEmpty());
    }

    // ── Helfer ───────────────────────────────────────────────────────────────────

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Map<String, String> form(VwEudaHttp.Request req) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String pair : req.formBody().split("&")) {
            String[] kv = pair.split("=", 2);
            m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return m;
    }

    private String fixture(String path) throws IOException {
        try (var in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Antwortet der Reihe nach mit vorbereiteten Responses und protokolliert jede Anfrage. */
    static class ScriptedHttp implements VwEudaHttp {
        final List<Request> requests = new ArrayList<>();
        private final Deque<Response> responses = new ArrayDeque<>();

        void respond(int status, String body, String... headerPairs) {
            Map<String, List<String>> headers = new LinkedHashMap<>();
            for (int i = 0; i + 1 < headerPairs.length; i += 2) {
                headers.computeIfAbsent(headerPairs[i], k -> new ArrayList<>()).add(headerPairs[i + 1]);
            }
            responses.add(new Response(status, headers, body.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public Response send(Request request) {
            requests.add(request);
            Response r = responses.poll();
            if (r == null) throw new AssertionError("Keine Antwort vorbereitet fuer " + request.method() + " " + request.url());
            return r;
        }
    }
}
