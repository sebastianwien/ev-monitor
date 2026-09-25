package com.evmonitor.application.vweuda;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Domain-Attribut wie im Browser: nur der eigene Host oder eine Elterndomain, nie eine fremde. */
class CookieJarTest {

    private static VwEudaHttp.Response response(String... setCookies) {
        return new VwEudaHttp.Response(200, Map.of("Set-Cookie", List.of(setCookies)), new byte[0]);
    }

    @Test
    void domainAttribute_forParentDomain_isAccepted() {
        CookieJar jar = new CookieJar();
        jar.addFromResponse("https://identity.vwgroup.io/x", response("d_1=dev; Domain=.vwgroup.io; Path=/"));
        assertEquals("d_1=dev", jar.headerFor("https://consent.vwgroup.io/y"));
    }

    @Test
    void domainAttribute_forForeignDomain_fallsBackToRequestHost() {
        CookieJar jar = new CookieJar();
        jar.addFromResponse("https://evil.example.org/x",
                response("s_1=hijack; Domain=identity.vwgroup.io; Path=/", "s_2=hijack2; Domain=.vwgroup.io"));
        assertNull(jar.headerFor("https://identity.vwgroup.io/y"));
        assertFalse(jar.cookiesOf("identity.vwgroup.io").containsKey("s_1"));
        assertEquals("s_1=hijack; s_2=hijack2", jar.headerFor("https://evil.example.org/z"));
    }

    @Test
    void domainAttribute_forSubdomainOfRequestHost_isNotAccepted() {
        CookieJar jar = new CookieJar();
        jar.addFromResponse("https://vwgroup.io/x", response("a=1; Domain=identity.vwgroup.io"));
        assertTrue(jar.cookiesOf("identity.vwgroup.io").isEmpty());
        assertEquals(Map.of("a", "1"), jar.cookiesOf("vwgroup.io"));
    }
}
