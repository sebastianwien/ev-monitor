package com.evmonitor.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Bean("spritMonitorRestTemplate")
    public RestTemplate spritMonitorRestTemplate(
        RestTemplateBuilder builder,
        @Value("${spritmonitor.proxy.enabled:false}") boolean proxyEnabled,
        @Value("${spritmonitor.proxy.host:localhost}") String proxyHost,
        @Value("${spritmonitor.proxy.port:1080}") int proxyPort
    ) {
        if (proxyEnabled) {
            log.info("Sprit-Monitor RestTemplate: using SOCKS5 proxy at {}:{}", proxyHost, proxyPort);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setProxy(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort)));
            factory.setConnectTimeout(10_000);
            factory.setReadTimeout(30_000);
            return new RestTemplate(factory);
        }

        log.info("Sprit-Monitor RestTemplate: direct connection (no proxy)");
        return builder
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .build();
    }
}
