package com.claritycam.platform.repository.otp;

import com.claritycam.platform.model.otp.OtpChallenge;
import com.claritycam.platform.model.otp.OtpPurpose;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {
  List<OtpChallenge> findTop10ByPhoneHashAndPurposeAndVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfterOrderByVerifiedAtDesc(
      String phoneHash, OtpPurpose purpose, LocalDateTime now);
  long deleteByExpiresAtBefore(LocalDateTime threshold);
}
