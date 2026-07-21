package com.claritycam.platform.common;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class RateLimitService {
  private final ConcurrentHashMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

  public void check(String key, int maxAttempts, Duration window) {
    Deque<Instant> timestamps = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
    Instant threshold = Instant.now().minus(window);
    synchronized (timestamps) {
      while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) {
        timestamps.removeFirst();
      }
      if (timestamps.size() >= maxAttempts) {
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
      }
      timestamps.addLast(Instant.now());
    }
  }

  @Scheduled(fixedDelay = 600_000)
  void cleanupIdleKeys() {
    Instant threshold = Instant.now().minus(Duration.ofHours(24));
    attempts.entrySet().removeIf(entry -> {
      Deque<Instant> timestamps = entry.getValue();
      synchronized (timestamps) {
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(threshold)) timestamps.removeFirst();
        return timestamps.isEmpty();
      }
    });
  }
}
