package com.claritycam.platform.controller.booking;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingLine;
import com.claritycam.platform.model.booking.BookingState;
import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.service.booking.BookingOperationsService;
import com.claritycam.platform.service.booking.BookingService;
import com.claritycam.platform.service.customer.CustomerAccountService;
import com.claritycam.platform.model.audit.AuditLog;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.service.common.RateLimitService;
import com.claritycam.platform.service.common.ClientAddressResolver;
import com.claritycam.platform.service.common.ReleaseFeatureService;
import com.claritycam.platform.service.otp.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {
  private final BookingService bookingService;
  private final AuditService auditService;
  private final CustomerAccountService customerAccounts;
  private final RateLimitService rateLimit;
  private final ClientAddressResolver clientAddressResolver;
  private final ReleaseFeatureService releaseFeatures;

  public BookingController(BookingService bookingService, AuditService auditService,
      CustomerAccountService customerAccounts, RateLimitService rateLimit,
      ClientAddressResolver clientAddressResolver,
      ReleaseFeatureService releaseFeatures) {
    this.bookingService = bookingService;
    this.auditService = auditService;
    this.customerAccounts = customerAccounts;
    this.rateLimit = rateLimit;
    this.clientAddressResolver = clientAddressResolver;
    this.releaseFeatures = releaseFeatures;
  }

  @PostMapping("/api/bookings/quote")
  BookingService.Quote quote(@Valid @RequestBody QuoteRequest request, HttpServletRequest servletRequest) {
    releaseFeatures.requireBookingEnabled();
    rateLimit.check("quote:ip:" + clientAddressResolver.resolve(servletRequest), 120, Duration.ofMinutes(1));
    return bookingService.quote(new BookingService.QuoteRequest(request.pickupTime(), request.returnTime(),
        toItems(request.items()), request.bundleId(), request.holdToken(), request.promotionCode(),
        request.rentalRate()));
  }

  @PostMapping("/api/bookings/hold")
  BookingService.HoldResponse hold(@Valid @RequestBody QuoteRequest request, HttpServletRequest servletRequest) {
    releaseFeatures.requireBookingEnabled();
    String phone = requireCustomerPhone(servletRequest);
    return bookingService.hold(new BookingService.QuoteRequest(request.pickupTime(), request.returnTime(),
        toItems(request.items()), request.bundleId(), request.holdToken(), request.promotionCode(),
        request.rentalRate()),
        phone, clientAddressResolver.resolve(servletRequest));
  }

  @PostMapping("/api/bookings/hold/release")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void releaseHold(@Valid @RequestBody ReleaseHoldRequest request, HttpServletRequest servletRequest) {
    releaseFeatures.requireBookingEnabled();
    bookingService.releaseHold(request.holdToken(), requireCustomerPhone(servletRequest));
  }

  @PostMapping("/api/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  PublicBookingResponse submit(@Valid @RequestBody SubmitBookingRequest request, HttpServletRequest servletRequest) {
    releaseFeatures.requireBookingEnabled();
    String sessionPhone = requireCustomerPhone(servletRequest);
    Booking booking = bookingService.submit(new BookingService.SubmitRequest(
        request.customerName(), request.phone(), request.bundleId(), request.pickupTime(), request.returnTime(),
        request.note(), toItems(request.items()), request.earlyPickupTime(),
        request.identityUploadToken(), request.paymentProofUploadToken(), request.holdToken(), request.promotionCode(),
        request.storeBranchId(), request.rentalRate()),
        sessionPhone, clientAddressResolver.resolve(servletRequest));
    servletRequest.getSession(true).setAttribute(CustomerAccountService.SESSION_PHONE, booking.getPhoneNormalized());
    return PublicBookingResponse.from(booking);
  }

  @PostMapping("/api/bookings/track")
  PublicBookingResponse track(@Valid @RequestBody TrackBookingRequest request, HttpServletRequest servletRequest) {
    String phone = OtpService.normalizePhone(request.phone());
    rateLimit.check("track:phone:" + phone, 20, Duration.ofMinutes(15));
    rateLimit.check("track:ip:" + clientAddressResolver.resolve(servletRequest), 40, Duration.ofMinutes(15));
    return PublicBookingResponse.from(bookingService.track(request.bookingId(), phone));
  }

  @GetMapping("/api/admin/bookings")
  List<AdminBookingResponse> listForAdmin(
      @RequestParam(required = false) String query,
      @RequestParam(required = false, defaultValue = "ALL") String state) {
    return bookingService.listForAdmin(query, state).stream().map(AdminBookingResponse::from).toList();
  }

  @PatchMapping("/api/admin/bookings/{id}/state")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPS','WAREHOUSE')")
  AdminBookingResponse changeState(
      @PathVariable String id,
      @Valid @RequestBody ChangeStateRequest request,
      Authentication authentication) {
    return AdminBookingResponse.from(bookingService.transition(id, request.state(), request.reason(), authentication.getName()));
  }

  @GetMapping("/api/admin/bookings/{id}/audit")
  List<AuditLog> audit(@PathVariable String id) {
    return auditService.history("BOOKING", id);
  }

  @GetMapping("/api/admin/bookings/{id}/identity/{side}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  ResponseEntity<byte[]> identityDocument(@PathVariable String id, @PathVariable String side,
      Authentication authentication) {
    var image = bookingService.identityDocument(id, side);
    auditService.record(authentication.getName(), "IDENTITY_DOCUMENT_VIEWED", "BOOKING", id, side.toLowerCase());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .header("Cache-Control", "no-store, private, max-age=0")
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .body(image.bytes());
  }

  @GetMapping("/api/admin/bookings/{id}/payment-proof")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  ResponseEntity<byte[]> paymentProof(@PathVariable String id, Authentication authentication) {
    var image = bookingService.paymentProof(id);
    auditService.record(authentication.getName(), "PAYMENT_PROOF_VIEWED", "BOOKING", id, "BANK_TRANSFER");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.contentType()))
        .header("Cache-Control", "no-store, private, max-age=0")
        .header("Pragma", "no-cache")
        .header("X-Content-Type-Options", "nosniff")
        .body(image.bytes());
  }

  @GetMapping("/api/catalog/{productId}/schedule")
  List<BookingService.ScheduleBlock> schedule(@PathVariable String productId,
      @RequestParam LocalDateTime from, @RequestParam LocalDateTime to,
      HttpServletRequest servletRequest) {
    requireCustomerPhone(servletRequest);
    rateLimit.check("schedule:ip:" + clientAddressResolver.resolve(servletRequest), 120, Duration.ofMinutes(1));
    return bookingService.schedule(productId, from, to);
  }

  @PatchMapping("/api/admin/bookings/{id}/early-pickup")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPS','SALES')")
  AdminBookingResponse reviewEarlyPickup(@PathVariable String id, @Valid @RequestBody EarlyPickupReviewRequest request,
      Authentication authentication) {
    return AdminBookingResponse.from(bookingService.reviewEarlyPickup(
        id, request.approved(), request.fee(), request.reason(), authentication.getName()));
  }

  @GetMapping("/api/admin/bookings/{id}/operations")
  BookingOperationsService.OperationsSnapshot operations(@PathVariable String id) {
    return bookingService.operations(id);
  }

  @PostMapping("/api/admin/bookings/{id}/allocations/auto")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OPS','WAREHOUSE')")
  BookingOperationsService.OperationsSnapshot autoAllocate(@PathVariable String id,
      Authentication authentication) {
    return bookingService.autoAllocate(id, authentication.getName());
  }

  private static List<BookingService.ItemRequest> toItems(List<BookingItemRequest> items) {
    return items.stream().map(item -> new BookingService.ItemRequest(item.productId(), item.quantity())).toList();
  }

  private String requireCustomerPhone(HttpServletRequest request) {
    String phone = request.getSession(false) == null ? null
        : (String) request.getSession(false).getAttribute(CustomerAccountService.SESSION_PHONE);
    return customerAccounts.require(phone).getPhoneNormalized();
  }

  public record QuoteRequest(
      @NotNull LocalDateTime pickupTime,
      @NotNull LocalDateTime returnTime,
      @NotEmpty List<@Valid BookingItemRequest> items,
      String bundleId,
      String holdToken,
      String promotionCode,
      String rentalRate) {}

  public record SubmitBookingRequest(
      @NotBlank @Size(max = 180) String customerName,
      @NotBlank String phone,
      String bundleId,
      @NotNull LocalDateTime pickupTime,
      @NotNull LocalDateTime returnTime,
      @Size(max = 1000) String note,
      @NotEmpty List<@Valid BookingItemRequest> items,
      LocalDateTime earlyPickupTime,
      @NotBlank String identityUploadToken,
      @NotBlank String paymentProofUploadToken,
      @NotBlank String holdToken,
      String promotionCode,
      String storeBranchId,
      String rentalRate) {}

  public record BookingItemRequest(
      @NotBlank @Size(max = 64) String productId,
      @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int quantity) {}
  public record ReleaseHoldRequest(@NotBlank String holdToken) {}
  public record TrackBookingRequest(
      @NotBlank @Size(max = 64) String bookingId,
      @NotBlank @Size(max = 20) String phone) {}
  public record ChangeStateRequest(@NotNull BookingState state, @Size(max = 500) String reason) {}
  public record EarlyPickupReviewRequest(boolean approved, @DecimalMin("0") BigDecimal fee,
                                         @Size(max = 500) String reason) {}

  public record PublicBookingResponse(String id, BookingState state, BigDecimal subtotalAmount, BigDecimal discountAmount,
                                      BigDecimal totalAmount, BigDecimal depositRequired, BigDecimal equipmentDeposit,
                                      BigDecimal bookingDeposit, BigDecimal amountDueNow,
                                      BigDecimal amountDueBeforeHandover, String promotionCode,
                                      LocalDateTime pickupTime, LocalDateTime returnTime, String note,
                                      String storeBranchId, String storeBranchCode, String storeBranchName,
                                      String storeBranchAddress) {
    static PublicBookingResponse from(Booking booking) {
      return new PublicBookingResponse(booking.getId(), booking.getState(), booking.getSubtotalAmount(),
          booking.getDiscountAmount(), booking.getTotalAmount(), booking.getDepositRequired(), booking.getEquipmentDeposit(),
          booking.getBookingDeposit(), booking.getAmountDueNow(), booking.getAmountDueBeforeHandover(),
          booking.getPromotionCode(),
          booking.getPickupTime(), booking.getReturnTime(), booking.getNote(),
          booking.getStoreBranchId(), booking.getStoreBranchCode(), booking.getStoreBranchName(),
          booking.getStoreBranchAddress());
    }
  }

  public record AdminBookingResponse(
      String id,
      String customerName,
      String phone,
      String trustScore,
      BookingState state,
      BigDecimal subtotalAmount,
      BigDecimal discountAmount,
      BigDecimal totalAmount,
      BigDecimal depositRequired,
      BigDecimal depositPaid,
      BigDecimal equipmentDeposit,
      BigDecimal bookingDeposit,
      BigDecimal amountDueNow,
      BigDecimal amountDueBeforeHandover,
      LocalDateTime pickupTime,
      LocalDateTime returnTime,
      String promotionCode,
      String note,
      String lastActionReason,
      LocalDateTime createdAt,
      boolean earlyPickupRequested,
      LocalDateTime earlyPickupTime,
      boolean earlyPickupApproved,
      BigDecimal earlyPickupFee,
      boolean identityDocumentsAvailable,
      boolean paymentProofAvailable,
      String storeBranchId,
      String storeBranchCode,
      String storeBranchName,
      String storeBranchAddress,
      List<BookingLine> items) {
    static AdminBookingResponse from(Booking booking) {
      return new AdminBookingResponse(
          booking.getId(), booking.getCustomerName(), booking.getPhone(), booking.getTrustScore(), booking.getState(),
          booking.getSubtotalAmount(), booking.getDiscountAmount(), booking.getTotalAmount(), booking.getDepositRequired(),
          booking.getDepositPaid(), booking.getEquipmentDeposit(), booking.getBookingDeposit(), booking.getAmountDueNow(),
          booking.getAmountDueBeforeHandover(),
          booking.getPickupTime(), booking.getReturnTime(), booking.getPromotionCode(),
          booking.getNote(), booking.getLastActionReason(), booking.getCreatedAt(),
          booking.isEarlyPickupRequested(), booking.getEarlyPickupTime(), booking.isEarlyPickupApproved(),
          booking.getEarlyPickupFee(), booking.getIdentityFrontReference() != null && booking.getIdentityBackReference() != null,
          booking.getPaymentProofReference() != null,
          booking.getStoreBranchId(), booking.getStoreBranchCode(), booking.getStoreBranchName(),
          booking.getStoreBranchAddress(),
          booking.getItems());
    }
  }
}
