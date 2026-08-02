package com.claritycam.platform.controller.customer;

import com.claritycam.platform.model.customer.CustomerWaitlistEntry;
import com.claritycam.platform.model.customer.WaitlistStatus;
import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.common.RateLimitService;
import com.claritycam.platform.service.common.ReleaseFeatureService;
import com.claritycam.platform.service.customer.CustomerWaitlistService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerWaitlistController {
  private final CustomerWaitlistService waitlist;
  private final RateLimitService rateLimit;
  private final ClientAddressResolver clientAddressResolver;
  private final ReleaseFeatureService features;

  public CustomerWaitlistController(CustomerWaitlistService waitlist, RateLimitService rateLimit,
      ClientAddressResolver clientAddressResolver, ReleaseFeatureService features) {
    this.waitlist = waitlist;
    this.rateLimit = rateLimit;
    this.clientAddressResolver = clientAddressResolver;
    this.features = features;
  }

  @PostMapping("/api/customer/waitlist")
  @ResponseStatus(HttpStatus.CREATED)
  WaitlistResponse register(@Valid @RequestBody WaitlistRequest input, HttpServletRequest request) {
    features.requireEarlyAccessRegistrationEnabled();
    rateLimit.check("waitlist:ip:" + clientAddressResolver.resolve(request), 8, Duration.ofHours(1));
    CustomerWaitlistService.RegistrationResult result = waitlist.register(
        input.phone(), input.name(), input.consentAccepted());
    return WaitlistResponse.from(result.entry(), result.newlyCreated());
  }

  @GetMapping("/api/admin/customers/waitlist")
  List<AdminWaitlistResponse> list() {
    return waitlist.list().stream().map(AdminWaitlistResponse::from).toList();
  }

  public record WaitlistRequest(
      @NotBlank @Size(max = 180) String name,
      @NotBlank @Size(max = 20) String phone,
      @AssertTrue(message = "Bạn cần đồng ý điều khoản lưu thông tin để giữ chỗ")
      boolean consentAccepted) {}

  public record WaitlistResponse(String slotCode, String maskedPhone, WaitlistStatus status,
      LocalDateTime registeredAt, boolean newlyCreated) {
    static WaitlistResponse from(CustomerWaitlistEntry entry, boolean newlyCreated) {
      return new WaitlistResponse(entry.slotCode(), mask(entry.getPhoneNormalized()), entry.getStatus(),
          entry.getCreatedAt(), newlyCreated);
    }

    private static String mask(String phone) {
      if (phone == null || phone.length() < 7) return "***";
      return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
    }
  }

  public record AdminWaitlistResponse(String slotCode, String accountId, String name, String phone,
      WaitlistStatus status, String consentVersion, LocalDateTime consentedAt, LocalDateTime createdAt) {
    static AdminWaitlistResponse from(CustomerWaitlistEntry entry) {
      return new AdminWaitlistResponse(entry.slotCode(), entry.getAccountId(), entry.getName(),
          entry.getPhoneNormalized(), entry.getStatus(), entry.getConsentVersion(), entry.getConsentedAt(),
          entry.getCreatedAt());
    }
  }
}
