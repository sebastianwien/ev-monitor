package com.evmonitor.application.publicapi;

public record ImportApiResult(int imported, int skipped, int errors) {}
