package com.evmonitor.application.euda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Einmaliger Login bei der VW-ID (Volkswagen, Škoda, Audi, SEAT, CUPRA) fuer den
 * EU-Data-Act-AutoSync. Das ist die einzige Stelle in EV Monitor, die das Passwort des
 * Nutzers sieht - deshalb liegt sie im oeffentlichen Repo und ist bewusst so gebaut:
 * <ul>
 *   <li>Das Passwort ist ausschliesslich ein Methodenparameter von {@link #login}. Es wird in
 *       kein Feld geschrieben, nicht geloggt und nicht gespeichert.</li>
 *   <li>Es geht genau einmal an genau eine URL: den Passwort-Schritt der VW-ID
 *       ({@code identity.vwgroup.io/.../login/authenticate}). Sonst nirgends hin.</li>
 *   <li>Ergebnis des Logins sind allein die VW-ID-SSO-Cookies ({@code s_*}, {@code d_*}). Nur die
 *       gehen an den Connectors-Service, der damit die Portal-Session stuendlich passwortfrei
 *       erneuert und sie AES-verschluesselt ablegt. Das Portal-Token selbst wird verworfen.</li>
 * </ul>
 * Ablauf (aus aufgezeichnetem Browser-Verkehr): Portal-Startseite, OIDC-Authorize mit
 * {@code prompt=login}, Identifier-Seite (E-Mail), Passwort-Seite (clientseitig gerendert,
 * Felder in {@code window._IDK}), Redirect-Kette zurueck ins Portal. Jede Abweichung landet als
 * {@link EudaAuthException}.
 * <p>
 * Ein Client pro Login-Versuch (eigenes Cookie-Jar), siehe {@link EudaLoginClientFactory}.
 */
public class EudaLoginClient {

    static final String PORTAL = "https://eu-data-act.drivesomethinggreater.com";
    static final String PORTAL_HOST = "eu-data-act.drivesomethinggreater.com";
    static final String IDP = "https://identity.vwgroup.io";
    static final String IDP_HOST = "identity.vwgroup.io";
    static final String PORTAL_SESSION_COOKIE = "access_token";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36";
    private static final int MAX_REDIRECTS = 15;

    record Brand(String clientId, String scope, String stateBrand) {}

    private static final String VW_CLIENT = "9b58543e-1c15-4193-91d5-8a14145bebb0@apps_vw-dilab_com";
    private static final String SEAT_CUPRA_CLIENT = "f85e5b69-e3b2-43aa-9c0d-1b7d0e0b576f@apps_vw-dilab_com";
    static final Map<String, Brand> BRANDS = Map.of(
            "volkswagen", new Brand(VW_CLIENT, "openid cars profile", "VOLKSWAGEN_PASSENGER_CARS"),
            "skoda", new Brand("3ea88bf9-1d4e-4a68-b3ad-4098c1f1d246@apps_vw-dilab_com", "openid cars profile", "SKODA"),
            "audi", new Brand("cc29b87a-5e9a-4362-aecf-5adea6b01bbb@apps_vw-dilab_com", "openid cars profile", "AUDI"),
            "seat", new Brand(SEAT_CUPRA_CLIENT, "openid profile cars", "SEAT"),
            "cupra", new Brand(SEAT_CUPRA_CLIENT, "openid profile cars", "CUPRA"));

    // Marker der IDP-Seiten (templateModel.template bzw. URL-Fragmente)
    private static final List<String> CREDENTIAL_ERROR_CODES = List.of(
            "login.error.password_invalid", "login.errors.password_invalid",
            "incorrect_credentials", "invalid_credentials", "log.in.error.wrong.credentials");
    private static final Pattern TEMPLATE_MODEL = Pattern.compile("templateModel\\s*:\\s*\\{");
    private static final Pattern CSRF_TOKEN = Pattern.compile("csrf_token\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern FORM = Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_ACTION = Pattern.compile("action\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT = Pattern.compile("<input[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_NAME = Pattern.compile("name\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_VALUE = Pattern.compile("value\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_TYPE = Pattern.compile("type\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

    private final EudaHttp http;
    private final Brand brand;
    private final String state;
    private final CookieJar cookies = new CookieJar();
    private final ObjectMapper json = new ObjectMapper();

    public EudaLoginClient(EudaHttp http, String brandKey) {
        this.http = http;
        this.brand = BRANDS.get(brandKey.toLowerCase(Locale.ROOT));
        if (brand == null) throw new IllegalArgumentException("Marke nicht unterstützt: " + brandKey);
        this.state = "de__de__" + brand.stateBrand();
    }

    /**
     * Login mit E-Mail und Passwort. Liefert die VW-ID-SSO-Cookies, mit denen der
     * Connectors-Service die Portal-Session ohne Passwort erneuern kann.
     *
     * @throws EudaAuthException.InvalidCredentials   E-Mail oder Passwort falsch
     * @throws EudaAuthException.InteractionRequired  VW verlangt eine Aktion im Browser
     * @throws EudaAuthException.PortalUnavailable    VW-ID oder Portal nicht erreichbar
     */
    public Map<String, String> login(String email, String password) {
        follow(EudaHttp.Request.get(PORTAL + "/", baseHeaders(null)));

        Landing identifierPage = follow(EudaHttp.Request.get(authorizeUrl(), baseHeaders(null)));
        failIfNotLoginStep(identifierPage, "loginIdentifier");

        Map<String, String> form = hiddenInputs(identifierPage.html());
        form.put("email", email);
        String identifierAction = resolve(identifierPage.url(), formAction(identifierPage.html()));
        Landing passwordPage = follow(EudaHttp.Request.postForm(identifierAction,
                baseHeaders(identifierPage.url()), encode(form)));
        failIfNotLoginStep(passwordPage, "loginAuthenticate");

        // Die Passwortseite ist clientseitig gerendert: kein <form>, Felder stehen in window._IDK
        JsonNode model = templateModel(passwordPage.html());
        Map<String, String> pwForm = new LinkedHashMap<>();
        pwForm.put("_csrf", csrfToken(passwordPage.html()));
        pwForm.put("relayState", text(model, "relayState"));
        pwForm.put("hmac", text(model, "hmac"));
        pwForm.put("email", email);
        pwForm.put("password", password);
        // postAction ("login/authenticate") ist relativ zum Client-Pfad, nicht zur Seite - die Seite
        // liegt bereits unter .../login/authenticate?relayState=..., also nur den Query abschneiden
        String postAction = model.path("postAction").asText("");
        String authenticateUrl = stripQuery(passwordPage.url());
        if (!postAction.isEmpty() && !authenticateUrl.endsWith("/" + postAction)) {
            authenticateUrl = resolve(passwordPage.url(), postAction).replace("/login/login/", "/login/");
        }
        Landing result = follow(EudaHttp.Request.postForm(authenticateUrl,
                baseHeaders(passwordPage.url()), encode(pwForm)));

        ensurePortalSession(result);
        return exportIdpCookies();
    }

    /** Nur die VW-ID-Cookies, die eine passwortfreie Erneuerung erlauben (s_/d_) - nichts vom Portal. */
    private Map<String, String> exportIdpCookies() {
        return cookies.cookiesOf(IDP_HOST).entrySet().stream()
                .filter(e -> e.getKey().startsWith("s_") || e.getKey().startsWith("d_"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private void ensurePortalSession(Landing result) {
        if (cookies.has(PORTAL_HOST, PORTAL_SESSION_COOKIE)) return;
        String template = templateName(result.html());
        JsonNode model = templateModelOrNull(result.html());
        String errorCode = model == null ? "" : model.path("error").path("errorCode").asText("");
        if ("loginAuthenticate".equals(template)) {
            if (errorCode.isEmpty() || CREDENTIAL_ERROR_CODES.contains(errorCode)) {
                throw new EudaAuthException.InvalidCredentials();
            }
            throw new EudaAuthException.InteractionRequired(errorCode);
        }
        if (!template.isEmpty()) throw new EudaAuthException.InteractionRequired(template);
        String url = result.url().toLowerCase(Locale.ROOT);
        if (url.contains("terms") || url.contains("consent") || url.contains("mfa") || url.contains("onboarding")) {
            throw new EudaAuthException.InteractionRequired(URI.create(result.url()).getPath());
        }
        if (result.status() >= 500) throw new EudaAuthException.PortalUnavailable("Portal-Login HTTP " + result.status());
        throw new EudaAuthException.InteractionRequired("Login endete ohne Portal-Session (HTTP " + result.status() + ")");
    }

    private void failIfNotLoginStep(Landing page, String expectedTemplate) {
        if (page.status() >= 500) throw new EudaAuthException.PortalUnavailable("VW-Login HTTP " + page.status());
        String template = templateName(page.html());
        if (!expectedTemplate.equals(template)) {
            throw new EudaAuthException.InteractionRequired(template.isEmpty() ? "unerwartete Seite" : template);
        }
    }

    // ── HTTP ─────────────────────────────────────────────────────────────────────

    private record Landing(int status, String url, String html) {}

    private EudaHttp.Response send(EudaHttp.Request request) {
        Map<String, String> headers = new LinkedHashMap<>(request.headers());
        String cookie = cookies.headerFor(request.url());
        if (cookie != null) headers.put("Cookie", cookie);
        EudaHttp.Response r = http.send(new EudaHttp.Request(request.method(), request.url(), headers, request.formBody()));
        cookies.addFromResponse(request.url(), r);
        return r;
    }

    /** Folgt Redirects manuell (POST wird zu GET), sammelt unterwegs alle Cookies ein. */
    private Landing follow(EudaHttp.Request request) {
        EudaHttp.Request current = request;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            EudaHttp.Response r = send(current);
            String location = r.header("Location");
            if (r.status() / 100 == 3 && location != null) {
                current = EudaHttp.Request.get(resolve(current.url(), location), baseHeaders(current.url()));
                continue;
            }
            return new Landing(r.status(), current.url(), r.text());
        }
        throw new EudaAuthException.PortalUnavailable("Redirect-Schleife beim Login");
    }

    private String authorizeUrl() {
        return IDP + "/oidc/v1/authorize?client_id=" + enc(brand.clientId())
                + "&response_type=code&scope=" + enc(brand.scope()) + "&state=" + enc(state)
                + "&redirect_uri=" + enc(PORTAL + "/login") + "&prompt=login";
    }

    private static Map<String, String> baseHeaders(String referer) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", USER_AGENT);
        if (referer != null) h.put("Referer", referer);
        return h;
    }

    // ── HTML-Parsing ─────────────────────────────────────────────────────────────

    static Map<String, String> hiddenInputs(String html) {
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher m = INPUT.matcher(html);
        while (m.find()) {
            String tag = m.group();
            Matcher type = ATTR_TYPE.matcher(tag);
            if (type.find() && !"hidden".equalsIgnoreCase(type.group(1))) continue;
            Matcher name = ATTR_NAME.matcher(tag);
            Matcher value = ATTR_VALUE.matcher(tag);
            if (name.find()) fields.put(name.group(1), value.find() ? value.group(1) : "");
        }
        return fields;
    }

    static String formAction(String html) {
        Matcher form = FORM.matcher(html);
        if (!form.find()) return "";
        Matcher action = ATTR_ACTION.matcher(form.group());
        return action.find() ? action.group(1) : "";
    }

    static String csrfToken(String html) {
        Matcher m = CSRF_TOKEN.matcher(html);
        if (!m.find()) throw new EudaAuthException.InteractionRequired("Login-Seite ohne CSRF-Token");
        return m.group(1);
    }

    private JsonNode templateModel(String html) {
        JsonNode model = templateModelOrNull(html);
        if (model == null) throw new EudaAuthException.InteractionRequired("Login-Seite ohne templateModel");
        return model;
    }

    /** {@code window._IDK = { templateModel: {...} }} - das JSON-Objekt per Klammerzaehlung ausschneiden. */
    private JsonNode templateModelOrNull(String html) {
        if (html == null) return null;
        Matcher m = TEMPLATE_MODEL.matcher(html);
        if (!m.find()) return null;
        int start = m.end() - 1;
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) {
                try {
                    return json.readTree(html.substring(start, i + 1));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private String templateName(String html) {
        JsonNode model = templateModelOrNull(html);
        return model == null ? "" : model.path("template").asText("");
    }

    private static String text(JsonNode node, String field) {
        String v = node.path(field).asText("");
        if (v.isEmpty()) throw new EudaAuthException.InteractionRequired("Login-Seite ohne Feld " + field);
        return v;
    }

    // ── URL-Helfer ───────────────────────────────────────────────────────────────

    static String resolve(String base, String location) {
        return URI.create(base).resolve(location.replace(" ", "%20")).toString();
    }

    private static String stripQuery(String url) {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }

    private static String encode(Map<String, String> form) {
        return form.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
