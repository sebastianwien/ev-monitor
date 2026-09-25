package com.evmonitor.application.imports.vweuda;

import java.util.List;

record VwEudaParseResult(
        String vin,
        List<VwEudaSession> sessions
) {}
