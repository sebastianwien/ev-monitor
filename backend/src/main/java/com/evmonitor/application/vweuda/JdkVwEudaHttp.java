package com.evmonitor.application.vweuda;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** {@link VwEudaHttp} auf java.net.http - ohne Redirects und ohne Cookie-Handler (siehe Interface). */
@Component
public class JdkVwEudaHttp implements VwEudaHttp {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    public Response send(Request request) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(request.url())).timeout(TIMEOUT);
        request.headers().forEach(b::header);
        b.method(request.method(), HttpRequest.BodyPublishers.ofString(request.formBody() == null ? "" : request.formBody()));
        try {
            HttpResponse<byte[]> r = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(r.statusCode(), r.headers().map(), r.body());
        } catch (IOException e) {
            throw new VwEudaAuthException.PortalUnavailable("Portal nicht erreichbar: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VwEudaAuthException.PortalUnavailable("unterbrochen");
        }
    }
}
