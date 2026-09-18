package com.evmonitor.application.euda;

import org.springframework.stereotype.Component;

/** Ein Login-Client pro Versuch (eigenes Cookie-Jar) - als Factory, damit der Service testbar bleibt. */
@Component
public class EudaLoginClientFactory {

    private final EudaHttp http;

    public EudaLoginClientFactory(EudaHttp http) {
        this.http = http;
    }

    public EudaLoginClient create(String brand) {
        return new EudaLoginClient(http, brand);
    }
}
