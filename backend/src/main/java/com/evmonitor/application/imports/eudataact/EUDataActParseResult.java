package com.evmonitor.application.imports.eudataact;

import java.util.List;

record EUDataActParseResult(
        String vin,
        List<EUDataActSession> sessions
) {}
