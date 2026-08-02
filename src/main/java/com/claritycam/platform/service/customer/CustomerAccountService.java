package com.claritycam.platform.service.customer;

import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.customer.CustomerAccountRepository;
import com.claritycam.platform.service.otp.OtpService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerAccountService {
  public static final String SESSION_PHONE = "CLARITYCAM_CUSTOMER_PHONE";

  private final CustomerAccountRepository accounts;
  private final BookingRepository bookings;
  private final PasswordEncoder passwordEncoder;

  public CustomerAccountService(CustomerAccountRepository accounts, BookingRepository bookings,
      PasswordEncoder passwordEncoder) {
    this.accounts = accounts;
    this.bookings = bookings;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public CustomerAccount login(String phone, String password) {
    String normalized = OtpService.normalizePhone(phone);
    CustomerAccount account = accounts.findByPhoneNormalized(normalized)
        .orElseThrow(() -> ApiException.unauthorized("Số điện thoại hoặc mật khẩu không đúng."));
    if (account.getPasswordHash() == null || !passwordEncoder.matches(password, account.getPasswordHash())) {
      throw ApiException.unauthorized("Số điện thoại hoặc mật khẩu không đúng.");
    }
    account.login(null);
    if (account.getOnboardingVersion() < 1 && bookings.existsByPhoneNormalized(normalized)) {
      account.completeOnboarding(1);
    }
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount register(String phone, String name, String password) {
    String normalized = OtpService.normalizePhone(phone);
    if (name == null || name.isBlank()) {
      throw ApiException.badRequest("Vui lòng nhập họ và tên để đăng ký.");
    }
    if (accounts.findByPhoneNormalized(normalized).isPresent()) {
      throw ApiException.badRequest("Số điện thoại đã có tài khoản. Vui lòng đăng nhập.");
    }
    CustomerAccount account = new CustomerAccount(
        "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(),
        normalized,
        name.trim());
    account.setPasswordHash(passwordEncoder.encode(password));
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount ensure(String phone, String name) {
    String normalized = OtpService.normalizePhone(phone);
    return accounts.findByPhoneNormalized(normalized).orElseGet(() -> accounts.save(new CustomerAccount(
        "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(), normalized,
        name == null || name.isBlank() ? normalized : name.trim())));
  }

  public CustomerAccount require(String phone) {
    if (phone == null) {
      throw ApiException.unauthorized("Vui lòng đăng nhập tài khoản khách hàng.");
    }
    return accounts.findByPhoneNormalized(phone)
        .orElseThrow(() -> ApiException.unauthorized("Phiên khách hàng không hợp lệ."));
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
