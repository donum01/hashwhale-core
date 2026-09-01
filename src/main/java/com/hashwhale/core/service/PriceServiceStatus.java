package com.hashwhale.core.service;

import com.hashwhale.core.entity.PriceSource;
import java.time.Instant;

public record PriceServiceStatus(PriceSource source, Instant updatedAt, boolean stale) {}
