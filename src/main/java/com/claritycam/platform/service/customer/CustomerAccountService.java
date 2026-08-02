package com.claritycam.platform.service.customer;

import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.customer.CustomerAccountRepository;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.service.otp.OtpService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAccountService {
  public static final String SESSION_PHONE = "CLARITYCAM_CUSTOMER_PHONE";
  private final CustomerAccountRepository accounts;
  private final BookingRepository bookings;

  public CustomerAccountService(CustomerAccountRepository accounts, BookingRepository bookings) {
    this.accounts = accounts; this.bookings = bookings;
  }

  @Transactional
  public CustomerAccount login(String phone, String name) {
    String normalized = OtpService.normalizePhone(phone);
    CustomerAccount account = accounts.findByPhoneNormalized(normalized)
        .orElseThrow(() -> ApiException.unauthorized(
            "KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y tÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n. Vui lÃƒÆ’Ã‚Â²ng chuyÃƒÂ¡Ã‚Â»Ã†â€™n sang Ãƒâ€žÃ‚ÂÃƒâ€žÃ†â€™ng kÃƒÆ’Ã‚Â½."));
    account.login(name);
    if (account.getOnboardingVersion() < 1 && bookings.existsByPhoneNormalized(normalized)) {
      account.completeOnboarding(1);
    }
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount register(String phone, String name) {
    String normalized = OtpService.normalizePhone(phone);
    if (name == null || name.isBlank()) {
      throw ApiException.badRequest("Vui lÃƒÆ’Ã‚Â²ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p hÃƒÂ¡Ã‚Â»Ã‚Â vÃƒÆ’Ã‚Â  tÃƒÆ’Ã‚Âªn Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng kÃƒÆ’Ã‚Â½.");
    }
    if (accounts.findByPhoneNormalized(normalized).isPresent()) {
      throw ApiException.badRequest("SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n thoÃƒÂ¡Ã‚ÂºÃ‚Â¡i Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ cÃƒÆ’Ã‚Â³ tÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n. Vui lÃƒÆ’Ã‚Â²ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p.");
    }
    return accounts.save(new CustomerAccount(
        "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(),
        normalized,
        name.trim()));
  }

  @Transactional
  public CustomerAccount ensure(String phone, String name) {
    String normalized = OtpService.normalizePhone(phone);
    return accounts.findByPhoneNormalized(normalized).orElseGet(() -> accounts.save(new CustomerAccount(
        "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(), normalized,
        name == null || name.isBlank() ? normalized : name.trim())));
  }

  public CustomerAccount require(String phone) {
    if (phone == null) throw ApiException.unauthorized("Vui lÃƒÆ’Ã‚Â²ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€žÃ†â€™ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p tÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n khÃƒÆ’Ã‚Â¡ch hÃƒÆ’Ã‚Â ng.");
    return accounts.findByPhoneNormalized(phone).orElseThrow(() -> ApiException.unauthorized("PhiÃƒÆ’Ã‚Âªn khÃƒÆ’Ã‚Â¡ch hÃƒÆ’Ã‚Â ng khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡."));
  }

  public List<Booking> bookings(String phone) {
    require(phone);
    return bookings.findByPhoneWithItems(phone);
  }

  @Transactional
  public CustomerAccount completeOnboarding(String phone, int version) {
    CustomerAccount account = require(phone);
    account.completeOnboarding(Math.max(1, version));
    return accounts.save(account);
  }
}
