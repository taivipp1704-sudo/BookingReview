package com.claritycam.platform.service.common;

import com.claritycam.platform.exception.ApiException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class RateLimitService {
  private final RateLimitStore store;
  private final MeterRegistry metrics;
  private final OperationalAlertService alerts;

  public RateLimitService(RateLimitStore store, MeterRegistry metrics, OperationalAlertService alerts) {
    this.store = store;
    this.metrics = metrics;
    this.alerts = alerts;
  }

  public void check(String key, int maxAttempts, Duration window) {
    int hits = store.increment(hash(key), window);
    if (hits > maxAttempts) {
      String blockedScope = scope(key);
      metrics.counter("claritycam.rate_limit.blocked", "scope", blockedScope).increment();
      alerts.alert("RATE_LIMIT_" + blockedScope.toUpperCase().replaceAll("[^A-Z0-9]+", "_"),
          "Repeated requests were blocked in scope " + blockedScope + ".");
      throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
          "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
    }
  }

  @Scheduled(fixedDelayString = "${claritycam.rate-limit.cleanup-ms:600000}")
  void cleanupExpiredBuckets() {
    store.deleteExpired();
  }

  private static String hash(String value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is unavailable", impossible);
    }
  }

  private static String scope(String key) {
    int separator = key.indexOf(':');
    return separator < 0 ? "unknown" : key.substring(0, separator);
  }
}
