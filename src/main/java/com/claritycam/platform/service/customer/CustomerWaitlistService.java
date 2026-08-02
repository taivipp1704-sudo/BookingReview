package com.claritycam.platform.service.customer;

import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.model.customer.CustomerWaitlistEntry;
import com.claritycam.platform.repository.customer.CustomerAccountRepository;
import com.claritycam.platform.repository.customer.CustomerWaitlistRepository;
import com.claritycam.platform.service.otp.OtpService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerWaitlistService {
  public static final String CONSENT_VERSION = "early-access-v1";
  private final CustomerAccountRepository accounts;
  private final CustomerWaitlistRepository waitlist;

  public CustomerWaitlistService(CustomerAccountRepository accounts, CustomerWaitlistRepository waitlist) {
    this.accounts = accounts;
    this.waitlist = waitlist;
  }

  @Transactional
  public RegistrationResult register(String phone, String name, boolean consentAccepted) {
    if (!consentAccepted) {
      throw ApiException.badRequest(
          "Bạn cần đồng ý cho AMY Digital lưu họ tên và số điện thoại để giữ chỗ.");
    }
    if (name == null || name.isBlank()) {
      throw ApiException.badRequest("Vui lòng nhập họ và tên.");
    }
    String normalizedPhone = OtpService.normalizePhone(phone);
    String normalizedName = name.trim();
    CustomerWaitlistEntry existing = waitlist.findByPhoneNormalized(normalizedPhone).orElse(null);
    if (existing != null) return new RegistrationResult(existing, false);

    CustomerAccount account = accounts.findByPhoneNormalized(normalizedPhone).orElseGet(() -> accounts.save(
        new CustomerAccount(newAccountId(), normalizedPhone, normalizedName)));
    CustomerWaitlistEntry entry = waitlist.save(new CustomerWaitlistEntry(
        account.getId(), normalizedPhone, normalizedName, "WEB_EARLY_ACCESS", CONSENT_VERSION));
    return new RegistrationResult(entry, true);
  }

  @Transactional
  public RegistrationResult recordRegisteredAccount(CustomerAccount account) {
    CustomerWaitlistEntry existing = waitlist.findByPhoneNormalized(account.getPhoneNormalized()).orElse(null);
    if (existing != null) return new RegistrationResult(existing, false);
    CustomerWaitlistEntry entry = waitlist.save(new CustomerWaitlistEntry(
        account.getId(), account.getPhoneNormalized(), account.getName(),
        "WEB_ACCOUNT_PREVIEW", CONSENT_VERSION));
    return new RegistrationResult(entry, true);
  }

  @Transactional(readOnly = true)
  public List<CustomerWaitlistEntry> list() {
    return waitlist.findAllByOrderByCreatedAtDesc();
  }

  private static String newAccountId() {
    return "CUS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
  }

  public record RegistrationResult(CustomerWaitlistEntry entry, boolean newlyCreated) {}
}
