package com.payrollportability.application.service;

import java.util.concurrent.ThreadLocalRandom;

public record RetryPolicy(long baseMs, long maxMs, int maxAttempts) {
  public RetryPolicy {
    if (baseMs <= 0 || maxMs < baseMs || maxAttempts < 1)
      throw new IllegalArgumentException("Invalid retry configuration");
  }

  public long delay(int attempt, long retryAfter) {
    long exponential = Math.min(maxMs, baseMs * (1L << Math.min(20, Math.max(0, attempt - 1))));
    long jitter = ThreadLocalRandom.current().nextLong(Math.max(1, exponential / 4));
    return Math.max(retryAfter, Math.min(maxMs, exponential + jitter));
  }
}
