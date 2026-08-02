package com.claritycam.platform.model.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_rate_limit_buckets")
public class RateLimitBucket {
  @Id
  @Column(length = 64)
  private String keyHash;
  private int hits;
  private LocalDateTime windowStartedAt;
  private LocalDateTime expiresAt;

  protected RateLimitBucket() {}
}
