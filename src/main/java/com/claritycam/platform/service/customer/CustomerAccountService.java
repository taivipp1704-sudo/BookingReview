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
    if (!account.isActive()) {
      throw ApiException.unauthorized("Số điện thoại hoặc mật khẩu không đúng.");
    }
    account.login(null);
    if (account.getOnboardingVersion() < 1 && bookings.existsByPhoneNormalized(normalized)) {
      account.completeOnboarding(1);
    }
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount loginWithPin(String phone, String pin) {
    String normalized = OtpService.normalizePhone(phone);
    CustomerAccount account = accounts.findByPhoneNormalized(normalized)
        .orElseThrow(() -> ApiException.unauthorized("Số điện thoại hoặc mã PIN không đúng."));
    if (!account.isActive() || !account.hasPin()
        || !passwordEncoder.matches(pin, account.getPinHash())) {
      if (account.hasPin() && account.isActive()) {
        account.registerPinFailure();
        accounts.save(account);
      }
      throw ApiException.unauthorized("Số điện thoại hoặc mã PIN không đúng.");
    }
    account.registerPinSuccess();
    account.login(null);
    if (account.getOnboardingVersion() < 1 && bookings.existsByPhoneNormalized(normalized)) {
      account.completeOnboarding(1);
    }
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount setPin(String phone, String currentPassword, String pin) {
    CustomerAccount account = require(phone);
    if (account.getPasswordHash() == null
        || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
      throw ApiException.badRequest("Mật khẩu hiện tại không đúng.");
    }
    account.setPinHash(passwordEncoder.encode(pin));
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount disablePin(String phone, String currentPassword) {
    CustomerAccount account = require(phone);
    if (account.getPasswordHash() == null
        || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
      throw ApiException.badRequest("Mật khẩu hiện tại không đúng.");
    }
    account.disablePin();
    return accounts.save(account);
  }

  @Transactional
  public CustomerAccount register(String phone, String name, String password) {
    return register(phone, name, null, password);
  }

  @Transactional
  public CustomerAccount register(String phone, String name, String email, String password) {
    String normalized = OtpService.normalizePhone(phone);
    String normalizedEmail = normalizeEmail(email);
    if (name == null || name.isBlank()) {
      throw ApiException.badRequest("Vui lòng nhập họ và tên để đăng ký.");
    }
    if (accounts.findByPhoneNormalized(normalized).isPresent()) {
      throw ApiException.badRequest("Số điện thoại đã có tài khoản. Vui lòng đăng nhập.");
    }
    if (normalizedEmail != null && accounts.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
      throw ApiException.badRequest("Email đã được sử dụng cho một tài khoản khác.");
    }
    CustomerAccount account = new CustomerAccount(
        "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(),
        normalized,
        name.trim());
    account.updateProfile(name, normalizedEmail);
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
    CustomerAccount account = accounts.findByPhoneNormalized(phone)
        .orElseThrow(() -> ApiException.unauthorized("Phiên khách hàng không hợp lệ."));
    if (!account.isActive()) {
      throw ApiException.unauthorized("Tài khoản đã bị khóa. Vui lòng liên hệ AMY Digital.");
    }
    return account;
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

  @Transactional
  public CustomerAccount changePassword(String phone, String currentPassword, String newPassword) {
    CustomerAccount account = require(phone);
    if (account.getPasswordHash() == null
        || !passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
      throw ApiException.badRequest("Mật khẩu hiện tại không đúng.");
    }
    account.changePassword(passwordEncoder.encode(newPassword));
    return accounts.save(account);
  }

  private String normalizeEmail(String email) {
    return email == null || email.isBlank() ? null : email.trim().toLowerCase();
  }
}
