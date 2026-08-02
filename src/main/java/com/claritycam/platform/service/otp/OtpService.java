package com.claritycam.platform.service.otp;

import com.claritycam.platform.model.otp.OtpChallenge;
import com.claritycam.platform.model.otp.OtpPurpose;
import com.claritycam.platform.repository.otp.OtpChallengeRepository;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.service.common.RateLimitService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final OtpChallengeRepository challenges;
  private final PasswordEncoder passwordEncoder;
  private final RateLimitService rateLimit;
  private final SmsDeliveryService smsDelivery;
  private final boolean exposeDemoCode;
  private final int expiryMinutes;

  public OtpService(
      OtpChallengeRepository challenges,
      PasswordEncoder passwordEncoder,
      RateLimitService rateLimit,
      SmsDeliveryService smsDelivery,
      @Value("${claritycam.otp.expose-demo-code:false}") boolean exposeDemoCode,
      @Value("${claritycam.otp.expiry-minutes:5}") int expiryMinutes) {
    this.challenges = challenges;
    this.passwordEncoder = passwordEncoder;
    this.rateLimit = rateLimit;
    this.smsDelivery = smsDelivery;
    this.exposeDemoCode = exposeDemoCode;
    this.expiryMinutes = expiryMinutes;
  }

  @Transactional
  public RequestedOtp request(String phone, OtpPurpose purpose, String remoteAddress) {
    String normalizedPhone = normalizePhone(phone);
    String phoneHash = sha256(normalizedPhone);
    rateLimit.check("otp:phone:" + phoneHash, 3, Duration.ofMinutes(15));
    rateLimit.check("otp:ip:" + remoteAddress, 12, Duration.ofMinutes(15));

    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);
    String challengeId = UUID.randomUUID().toString();
    OtpChallenge challenge = new OtpChallenge(
        challengeId,
        phoneHash,
        passwordEncoder.encode(code),
        purpose,
        expiresAt);
    challenges.save(challenge);
    if (smsDelivery.isEnabled()) {
      smsDelivery.sendOtp(normalizedPhone, code, expiryMinutes, challengeId);
    } else if (!exposeDemoCode) {
      throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ gÃƒÂ¡Ã‚Â»Ã‚Â­i SMS OTP chÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c cÃƒÂ¡Ã‚ÂºÃ‚Â¥u hÃƒÆ’Ã‚Â¬nh.");
    }
    return new RequestedOtp(challenge.getId(), expiresAt, exposeDemoCode ? code : null);
  }

  public VerifiedOtp verify(String challengeId, String phone, String code, OtpPurpose purpose, String remoteAddress) {
    OtpChallenge challenge = challenges.findById(challengeId)
        .orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c."));
    rateLimit.check("otp:verify:" + challengeId, 5, Duration.ofMinutes(15));
    rateLimit.check("otp:verify-ip:" + remoteAddress, 20, Duration.ofMinutes(15));
    if (challenge.getPurpose() != purpose || !challenge.getPhoneHash().equals(sha256(normalizePhone(phone)))) {
      throw ApiException.forbidden("ThÃƒÆ’Ã‚Â´ng tin xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c khÃƒÆ’Ã‚Â´ng khÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp.");
    }
    if (challenge.isExpired()) {
      throw ApiException.badRequest("MÃƒÆ’Ã‚Â£ OTP Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ hÃƒÂ¡Ã‚ÂºÃ‚Â¿t hÃƒÂ¡Ã‚ÂºÃ‚Â¡n. Vui lÃƒÆ’Ã‚Â²ng yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u mÃƒÆ’Ã‚Â£ mÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi.");
    }
    if (challenge.getVerifiedAt() != null || challenge.getAttempts() >= 5) {
      throw ApiException.badRequest("MÃƒÆ’Ã‚Â£ OTP nÃƒÆ’Ã‚Â y khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â²n hiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡u lÃƒÂ¡Ã‚Â»Ã‚Â±c.");
    }
    if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
      challenge.incrementAttempts();
      challenges.save(challenge);
      throw ApiException.badRequest("MÃƒÆ’Ã‚Â£ OTP khÃƒÆ’Ã‚Â´ng chÃƒÆ’Ã‚Â­nh xÃƒÆ’Ã‚Â¡c.");
    }
    String verificationToken = randomToken();
    challenge.verify(passwordEncoder.encode(verificationToken));
    challenges.save(challenge);
    return new VerifiedOtp(verificationToken, challenge.getExpiresAt());
  }

  public void consume(String verificationToken, String phone, OtpPurpose purpose) {
    String phoneHash = sha256(normalizePhone(phone));
    OtpChallenge challenge = challenges
        .findTop10ByPhoneHashAndPurposeAndVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfterOrderByVerifiedAtDesc(
            phoneHash, purpose, LocalDateTime.now())
        .stream()
        .filter(candidate -> passwordEncoder.matches(verificationToken, candidate.getVerificationTokenHash()))
        .findFirst()
        .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "BÃƒÂ¡Ã‚ÂºÃ‚Â¡n cÃƒÂ¡Ã‚ÂºÃ‚Â§n xÃƒÆ’Ã‚Â¡c thÃƒÂ¡Ã‚Â»Ã‚Â±c OTP trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi tiÃƒÂ¡Ã‚ÂºÃ‚Â¿p tÃƒÂ¡Ã‚Â»Ã‚Â¥c."));
    challenge.consume();
    challenges.save(challenge);
  }

  @Scheduled(fixedDelay = 3_600_000)
  @Transactional
  void cleanupExpiredChallenges() {
    challenges.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(1));
  }

  public static String normalizePhone(String phone) {
    String digits = phone == null ? "" : phone.replaceAll("\\D", "");
    if (digits.startsWith("84") && digits.length() == 11) {
      digits = "0" + digits.substring(2);
    }
    if (!digits.matches("0\\d{9}")) {
      throw ApiException.badRequest("SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n thoÃƒÂ¡Ã‚ÂºÃ‚Â¡i ViÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t Nam khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
    }
    return digits;
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String randomToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public record RequestedOtp(String challengeId, LocalDateTime expiresAt, String demoCode) {}
  public record VerifiedOtp(String verificationToken, LocalDateTime expiresAt) {}
}
