package com.evmonitor.application.vweuda;

import org.springframework.stereotype.Component;

/** Ein Login-Client pro Versuch (eigenes Cookie-Jar) - als Factory, damit der Service testbar bleibt. */
@Component
public class VwEudaLoginClientFactory {

    private final VwEudaHttp http;

    public VwEudaLoginClientFactory(VwEudaHttp http) {
        this.http = http;
    }

    public VwEudaLoginClient create(String brand) {
        return new VwEudaLoginClient(http, brand);
    }
}
