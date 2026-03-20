package com.evmonitor.application;

import java.math.BigDecimal;

public record GeohashResponse(String geohash, BigDecimal kwhCharged) {}
