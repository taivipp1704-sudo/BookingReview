package com.claritycam.platform.otp;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {
  List<OtpChallenge> findTop10ByPhoneHashAndPurposeAndVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfterOrderByVerifiedAtDesc(
      String phoneHash, OtpPurpose purpose, LocalDateTime now);
  long deleteByExpiresAtBefore(LocalDateTime threshold);
}
