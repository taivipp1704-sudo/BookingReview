package com.claritycam.platform.controller.otp;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.otp.OtpPurpose;
import com.claritycam.platform.service.customer.CustomerAccountService;
import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.otp.OtpService;
import com.claritycam.platform.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/otp")
public class OtpController {
  private final OtpService otpService;
  private final CustomerAccountService customerAccounts;
  private final ClientAddressResolver clientAddressResolver;

  public OtpController(
      OtpService otpService,
      CustomerAccountService customerAccounts,
      ClientAddressResolver clientAddressResolver) {
    this.otpService = otpService;
    this.customerAccounts = customerAccounts;
    this.clientAddressResolver = clientAddressResolver;
  }

  @PostMapping("/request")
  OtpService.RequestedOtp request(@Valid @RequestBody OtpRequest request, HttpServletRequest servletRequest) {
    requireBookingSession(request.phone(), request.purpose(), servletRequest);
    return otpService.request(request.phone(), request.purpose(), clientAddressResolver.resolve(servletRequest));
  }

  @PostMapping("/verify")
  OtpService.VerifiedOtp verify(@Valid @RequestBody VerifyOtpRequest request, HttpServletRequest servletRequest) {
    requireBookingSession(request.phone(), request.purpose(), servletRequest);
    return otpService.verify(request.challengeId(), request.phone(), request.code(), request.purpose(), clientAddressResolver.resolve(servletRequest));
  }

  private void requireBookingSession(String phone, OtpPurpose purpose, HttpServletRequest request) {
    if (purpose != OtpPurpose.BOOKING) return;
    String sessionPhone = request.getSession(false) == null ? null
        : (String) request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);
    String owner = customerAccounts.require(sessionPhone).getPhoneNormalized();
    if (!owner.equals(OtpService.normalizePhone(phone))) {
      throw ApiException.forbidden("Tài khoản đăng nhập không khớp với số điện thoại nhận OTP.");
    }
  }

  public record OtpRequest(@NotBlank String phone, @NotNull OtpPurpose purpose) {}
  public record VerifyOtpRequest(
      @NotBlank String challengeId,
      @NotBlank String phone,
      @NotBlank @jakarta.validation.constraints.Pattern(regexp = "\\d{6}") String code,
      @NotNull OtpPurpose purpose) {}
}
