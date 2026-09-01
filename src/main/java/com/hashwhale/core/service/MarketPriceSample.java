package com.hashwhale.core.service;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceSample(Instant timestamp, BigDecimal price) {}
