package com.claritycam.platform.customer;

import com.claritycam.platform.booking.Booking;
import com.claritycam.platform.booking.BookingRepository;
import com.claritycam.platform.common.ApiException;
import com.claritycam.platform.otp.OtpService;
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
    CustomerAccount account = ensure(normalized, name);
    account.login(name);
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
    if (phone == null) throw ApiException.unauthorized("Vui lòng đăng nhập tài khoản khách hàng.");
    return accounts.findByPhoneNormalized(phone).orElseThrow(() -> ApiException.unauthorized("Phiên khách hàng không hợp lệ."));
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
