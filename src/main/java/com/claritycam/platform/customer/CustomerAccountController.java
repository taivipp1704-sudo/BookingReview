package com.claritycam.platform.customer;

import com.claritycam.platform.booking.Booking;
import com.claritycam.platform.booking.BookingLine;
import com.claritycam.platform.booking.BookingState;
import com.claritycam.platform.common.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/customer/account")
public class CustomerAccountController {
  private final CustomerAccountService service;
  private final IdentityDocumentService identityDocuments;
  private final RateLimitService rateLimit;
  public CustomerAccountController(CustomerAccountService service, IdentityDocumentService identityDocuments,
      RateLimitService rateLimit) {
    this.service = service;
    this.identityDocuments = identityDocuments;
    this.rateLimit = rateLimit;
  }

  @PostMapping(value = "/identity-documents", consumes = "multipart/form-data")
  IdentityDocumentResponse uploadIdentity(@RequestPart("front") MultipartFile front, @RequestPart("back") MultipartFile back,
      HttpServletRequest request) {
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    rateLimit.check("identity:phone:" + phone, 5, Duration.ofHours(1));
    rateLimit.check("identity:ip:" + request.getRemoteAddr(), 15, Duration.ofHours(1));
    IdentityDocumentService.UploadReceipt receipt = identityDocuments.storePair(front, back, phone);
    return new IdentityDocumentResponse(receipt.uploadToken(), receipt.expiresAt());
  }

  @PostMapping(value = "/payment-proof", consumes = "multipart/form-data")
  IdentityDocumentResponse uploadPaymentProof(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    rateLimit.check("payment-proof:phone:" + phone, 8, Duration.ofHours(1));
    rateLimit.check("payment-proof:ip:" + request.getRemoteAddr(), 20, Duration.ofHours(1));
    IdentityDocumentService.UploadReceipt receipt = identityDocuments.storeSingle(file, phone);
    return new IdentityDocumentResponse(receipt.uploadToken(), receipt.expiresAt());
  }

  @PostMapping("/login")
  AccountResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    CustomerAccount account = service.login(request.phone(), request.name());
    servletRequest.getSession(true);
    servletRequest.changeSessionId();
    servletRequest.getSession(false).setAttribute(CustomerAccountService.SESSION_PHONE, account.getPhoneNormalized());
    return AccountResponse.from(account);
  }

  @GetMapping("/me")
  AccountResponse me(HttpServletRequest request) {
    return AccountResponse.from(service.require(sessionPhone(request)));
  }

  @GetMapping("/bookings")
  List<AccountBookingResponse> bookings(HttpServletRequest request) {
    return service.bookings(sessionPhone(request)).stream().map(AccountBookingResponse::from).toList();
  }

  @PostMapping("/onboarding/complete")
  AccountResponse completeOnboarding(HttpServletRequest request) {
    return AccountResponse.from(service.completeOnboarding(sessionPhone(request), 1));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(HttpServletRequest request) {
    if (request.getSession(false) != null) request.getSession(false).removeAttribute(CustomerAccountService.SESSION_PHONE);
  }

  private String sessionPhone(HttpServletRequest request) {
    return request.getSession(false) == null ? null : (String) request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);
  }

  public record LoginRequest(@NotBlank String phone, @Size(max = 180) String name) {}
  public record IdentityDocumentResponse(String uploadToken, LocalDateTime expiresAt) {}
  public record AccountResponse(String id, String name, String phone, int onboardingVersion,
                                LocalDateTime onboardingCompletedAt) {
    static AccountResponse from(CustomerAccount account) {
      return new AccountResponse(account.getId(), account.getName(), account.getPhoneNormalized(),
          account.getOnboardingVersion(), account.getOnboardingCompletedAt());
    }
  }
  public record AccountBookingResponse(String id, BookingState state, BigDecimal subtotalAmount, BigDecimal discountAmount,
      BigDecimal totalAmount, BigDecimal depositRequired, BigDecimal equipmentDeposit, BigDecimal bookingDeposit,
      BigDecimal amountDueNow, String promotionCode, LocalDateTime pickupTime,
      LocalDateTime returnTime, boolean earlyPickupRequested, LocalDateTime earlyPickupTime,
      boolean earlyPickupApproved, BigDecimal earlyPickupFee, LocalDateTime holdExpiresAt, List<BookingLine> items) {
    static AccountBookingResponse from(Booking booking) { return new AccountBookingResponse(booking.getId(), booking.getState(),
        booking.getSubtotalAmount(), booking.getDiscountAmount(), booking.getTotalAmount(), booking.getDepositRequired(),
        booking.getEquipmentDeposit(), booking.getBookingDeposit(), booking.getAmountDueNow(), booking.getPromotionCode(),
        booking.getPickupTime(), booking.getReturnTime(),
        booking.isEarlyPickupRequested(), booking.getEarlyPickupTime(), booking.isEarlyPickupApproved(),
        booking.getEarlyPickupFee(), booking.getHoldExpiresAt(), booking.getItems()); }
  }
}
