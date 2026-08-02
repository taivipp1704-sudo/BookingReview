package com.claritycam.platform.controller.customer;

import com.claritycam.platform.model.customer.CustomerAccount;
import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.service.booking.BookingService;
import com.claritycam.platform.service.customer.CustomerAccountService;
import com.claritycam.platform.service.customer.CustomerWaitlistService;
import com.claritycam.platform.service.customer.IdentityDocumentService;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingLine;
import com.claritycam.platform.model.booking.BookingState;
import com.claritycam.platform.service.common.RateLimitService;
import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.common.ReleaseFeatureService;
import com.claritycam.platform.service.otp.OtpService;
import com.claritycam.platform.config.PasswordPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  private final BookingService bookingService;
  private final AuditService auditService;
  private final RateLimitService rateLimit;
  private final ClientAddressResolver clientAddressResolver;
  private final ReleaseFeatureService releaseFeatures;
  private final CustomerWaitlistService waitlist;
  public CustomerAccountController(
      CustomerAccountService service,
      IdentityDocumentService identityDocuments,
      BookingService bookingService,
      AuditService auditService,
      RateLimitService rateLimit,
      ClientAddressResolver clientAddressResolver,
      ReleaseFeatureService releaseFeatures,
      CustomerWaitlistService waitlist) {
    this.service = service;
    this.identityDocuments = identityDocuments;
    this.bookingService = bookingService;
    this.auditService = auditService;
    this.rateLimit = rateLimit;
    this.clientAddressResolver = clientAddressResolver;
    this.releaseFeatures = releaseFeatures;
    this.waitlist = waitlist;
  }

  @PostMapping(value = "/identity-documents", consumes = "multipart/form-data")
  IdentityDocumentResponse uploadIdentity(@RequestPart("front") MultipartFile front, @RequestPart("back") MultipartFile back,
      HttpServletRequest request) {
    releaseFeatures.requireBookingEnabled();
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    rateLimit.check("identity:phone:" + phone, 5, Duration.ofHours(1));
    rateLimit.check("identity:ip:" + clientAddressResolver.resolve(request), 15, Duration.ofHours(1));
    IdentityDocumentService.UploadReceipt receipt = identityDocuments.storePair(front, back, phone);
    return new IdentityDocumentResponse(receipt.uploadToken(), receipt.expiresAt());
  }

  @PostMapping(value = "/payment-proof", consumes = "multipart/form-data")
  IdentityDocumentResponse uploadPaymentProof(@RequestPart("file") MultipartFile file, HttpServletRequest request) {
    releaseFeatures.requireBookingEnabled();
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    rateLimit.check("payment-proof:phone:" + phone, 8, Duration.ofHours(1));
    rateLimit.check("payment-proof:ip:" + clientAddressResolver.resolve(request), 20, Duration.ofHours(1));
    IdentityDocumentService.UploadReceipt receipt = identityDocuments.storeSingle(file, phone);
    return new IdentityDocumentResponse(receipt.uploadToken(), receipt.expiresAt());
  }

  @PostMapping("/login")
  AccountResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    String phone = OtpService.normalizePhone(request.phone());
    rateLimit.check("customer-login:phone:" + phone, 8, Duration.ofMinutes(15));
    rateLimit.check("customer-login:ip:" + clientAddressResolver.resolve(servletRequest), 20, Duration.ofMinutes(15));
    CustomerAccount account = service.login(request.phone(), request.password());
    establishSession(account, servletRequest);
    return AccountResponse.from(account);
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  AccountResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
    String phone = OtpService.normalizePhone(request.phone());
    rateLimit.check("customer-register:phone:" + phone, 5, Duration.ofHours(1));
    rateLimit.check("customer-register:ip:" + clientAddressResolver.resolve(servletRequest), 10, Duration.ofHours(1));
    releaseFeatures.requireEarlyAccessRegistrationEnabled();
    CustomerAccount account = service.register(request.phone(), request.name(), request.email(), request.password());
    waitlist.recordRegisteredAccount(account);
    establishSession(account, servletRequest);
    return AccountResponse.from(account);
  }

  private void establishSession(CustomerAccount account, HttpServletRequest servletRequest) {
    servletRequest.getSession(true);
    servletRequest.changeSessionId();
    servletRequest.getSession(false).setAttribute(CustomerAccountService.SESSION_PHONE, account.getPhoneNormalized());
  }

  @GetMapping("/me")
  AccountResponse me(HttpServletRequest request) {
    return AccountResponse.from(service.require(sessionPhone(request)));
  }

  @GetMapping("/bookings")
  List<AccountBookingResponse> bookings(HttpServletRequest request) {
    return service.bookings(sessionPhone(request)).stream().map(AccountBookingResponse::from).toList();
  }

  @GetMapping("/bookings/{id}/identity/{side}")
  ResponseEntity<byte[]> identityDocument(
      @PathVariable String id,
      @PathVariable String side,
      HttpServletRequest request) {
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    var image = bookingService.identityDocumentForCustomer(id, phone, side);
    auditService.record("customer:" + phone, "IDENTITY_DOCUMENT_VIEWED", "BOOKING", id, side.toLowerCase());
    return privateImage(image);
  }

  @GetMapping("/bookings/{id}/payment-proof")
  ResponseEntity<byte[]> paymentProof(@PathVariable String id, HttpServletRequest request) {
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    var image = bookingService.paymentProofForCustomer(id, phone);
    auditService.record("customer:" + phone, "PAYMENT_PROOF_VIEWED", "BOOKING", id, "BANK_TRANSFER");
    return privateImage(image);
  }

  @PostMapping("/onboarding/complete")
  AccountResponse completeOnboarding(HttpServletRequest request) {
    return AccountResponse.from(service.completeOnboarding(sessionPhone(request), 1));
  }

  @PostMapping("/password/change")
  AccountResponse changePassword(@Valid @RequestBody ChangePasswordRequest body,
      HttpServletRequest request) {
    String phone = service.require(sessionPhone(request)).getPhoneNormalized();
    rateLimit.check("customer-password-change:phone:" + phone, 5, Duration.ofHours(1));
    rateLimit.check("customer-password-change:ip:" + clientAddressResolver.resolve(request), 15,
        Duration.ofHours(1));
    CustomerAccount account = service.changePassword(phone, body.currentPassword(), body.newPassword());
    auditService.record("customer:" + phone, "CUSTOMER_PASSWORD_CHANGED", "CUSTOMER_ACCOUNT",
        account.getId(), "SELF_SERVICE");
    return AccountResponse.from(account);
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(HttpServletRequest request, HttpServletResponse response) {
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    response.setHeader("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"");
  }

  private String sessionPhone(HttpServletRequest request) {
    return request.getSession(false) == null ? null : (String) request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);
  }

  private static ResponseEntity<byte[]> privateImage(IdentityDocumentService.StoredImage image) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .header("Content-Disposition", "inline")
        .header("Cache-Control", "no-store, private, max-age=0")
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .body(image.bytes());
  }

  public record LoginRequest(
      @NotBlank @Size(max = 20) String phone,
      @NotBlank @Size(min = 8, max = 72) String password) {}
  public record RegisterRequest(
      @NotBlank @Size(max = 20) String phone,
      @NotBlank @Size(max = 180) String name,
      @NotBlank @Email @Size(max = 255) String email,
      @NotBlank @Size(min = 8, max = 72) String password,
      @AssertTrue boolean consentAccepted) {}
  public record ChangePasswordRequest(
      @NotBlank @Size(min = 8, max = 128) String currentPassword,
      @NotBlank @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
      String newPassword) {}
  public record IdentityDocumentResponse(String uploadToken, LocalDateTime expiresAt) {}
  public record AccountResponse(String id, String name, String email, String phone, boolean active,
                                boolean mustChangePassword, int onboardingVersion,
                                LocalDateTime onboardingCompletedAt) {
    static AccountResponse from(CustomerAccount account) {
      return new AccountResponse(account.getId(), account.getName(), account.getEmail(),
          account.getPhoneNormalized(), account.isActive(), account.isMustChangePassword(),
          account.getOnboardingVersion(), account.getOnboardingCompletedAt());
    }
  }
  public record AccountBookingResponse(String id, BookingState state, BigDecimal subtotalAmount, BigDecimal discountAmount,
      BigDecimal totalAmount, BigDecimal depositRequired, BigDecimal equipmentDeposit, BigDecimal bookingDeposit,
      BigDecimal amountDueNow, String promotionCode, LocalDateTime pickupTime,
      LocalDateTime returnTime, boolean earlyPickupRequested, LocalDateTime earlyPickupTime,
      boolean earlyPickupApproved, BigDecimal earlyPickupFee, LocalDateTime holdExpiresAt,
      boolean identityDocumentsAvailable, boolean paymentProofAvailable,
      String storeBranchId, String storeBranchCode, String storeBranchName, String storeBranchAddress,
      List<BookingLine> items) {
    static AccountBookingResponse from(Booking booking) { return new AccountBookingResponse(booking.getId(), booking.getState(),
        booking.getSubtotalAmount(), booking.getDiscountAmount(), booking.getTotalAmount(), booking.getDepositRequired(),
        booking.getEquipmentDeposit(), booking.getBookingDeposit(), booking.getAmountDueNow(), booking.getPromotionCode(),
        booking.getPickupTime(), booking.getReturnTime(),
        booking.isEarlyPickupRequested(), booking.getEarlyPickupTime(), booking.isEarlyPickupApproved(),
        booking.getEarlyPickupFee(), booking.getHoldExpiresAt(),
        booking.getIdentityFrontReference() != null && booking.getIdentityBackReference() != null,
        booking.getPaymentProofReference() != null,
        booking.getStoreBranchId(), booking.getStoreBranchCode(), booking.getStoreBranchName(),
        booking.getStoreBranchAddress(), booking.getItems()); }
  }
}
