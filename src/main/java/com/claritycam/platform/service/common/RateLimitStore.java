package com.claritycam.platform.service.common;

import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RateLimitStore {
  private final JdbcTemplate jdbc;

  public RateLimitStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int increment(String keyHash, Duration window) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime threshold = now.minus(window);
    LocalDateTime expiresAt = now.plus(window).plusMinutes(5);
    int updated = updateExisting(keyHash, now, threshold, expiresAt);
    if (updated == 0) {
      try {
        jdbc.update("""
            INSERT INTO api_rate_limit_buckets (key_hash, hits, window_started_at, expires_at)
            VALUES (?, 1, ?, ?)
            """, keyHash, now, expiresAt);
      } catch (DuplicateKeyException ignored) {
        // Another application instance created the same bucket first.
        updateExisting(keyHash, now, threshold, expiresAt);
      }
    }
    Integer hits = jdbc.queryForObject(
        "SELECT hits FROM api_rate_limit_buckets WHERE key_hash = ?", Integer.class, keyHash);
    return hits == null ? 1 : hits;
  }

  private int updateExisting(
      String keyHash, LocalDateTime now, LocalDateTime threshold, LocalDateTime expiresAt) {
    return jdbc.update("""
        UPDATE api_rate_limit_buckets
        SET hits = CASE WHEN window_started_at < ? THEN 1 ELSE hits + 1 END,
            window_started_at = CASE WHEN window_started_at < ? THEN ? ELSE window_started_at END,
            expires_at = ?
        WHERE key_hash = ?
        """, threshold, threshold, now, expiresAt, keyHash);
  }

  @Transactional
  public void deleteExpired() {
    jdbc.update("DELETE FROM api_rate_limit_buckets WHERE expires_at < ?", LocalDateTime.now());
  }
}
