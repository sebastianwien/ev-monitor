package com.evmonitor.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FrontendErrorRequest(
        @NotBlank @Size(max = 500) String message,
        @Size(max = 3000) String stack,
        @Size(max = 500) String url,
        @Size(max = 200) String info
) {}
