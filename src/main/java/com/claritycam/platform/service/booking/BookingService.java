package com.claritycam.platform.service.booking;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingLine;
import com.claritycam.platform.model.booking.BookingState;
import com.claritycam.platform.model.booking.CheckoutHoldReservation;
import com.claritycam.platform.model.booking.ReservationType;
import com.claritycam.platform.model.promotion.Promotion;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.booking.CheckoutHoldReservationRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.repository.inventory.StockItemRepository;
import com.claritycam.platform.service.customer.CustomerAccountService;
import com.claritycam.platform.service.promotion.PromotionService;
import com.claritycam.platform.service.store.StoreBranchService;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.repository.catalog.BundleRepository;
import com.claritycam.platform.model.catalog.RentalBundle;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.service.common.RateLimitService;
import com.claritycam.platform.service.customer.IdentityDocumentService;
import com.claritycam.platform.service.otp.OtpService;
import com.claritycam.platform.service.finance.FinanceSettlementService;
import com.claritycam.platform.model.store.StoreBranch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private static final Duration TEMPORARY_HOLD_DURATION = Duration.ofMinutes(5);
  private static final Duration PREPARATION_BUFFER = Duration.ofMinutes(30);
  private static final BigDecimal MANDATORY_RESERVATION_DEPOSIT = BigDecimal.valueOf(50_000);
  private static final Set<BookingState> AUTO_CONFIRMABLE_STATES = Set.of(
      BookingState.PENDING_REVIEW, BookingState.NEGOTIATION, BookingState.CONDITIONAL, BookingState.TEMP_HOLD);

  private final BookingRepository bookings;
  private final CheckoutHoldReservationRepository checkoutHolds;
  private final ProductRepository products;
  private final InventoryAssetRepository assets;
  private final StockItemRepository stock;
  private final RateLimitService rateLimit;
  private final AuditService audit;
  private final CustomerAccountService customerAccounts;
  private final BundleRepository bundles;
  private final PromotionService promotionService;
  private final IdentityDocumentService identityDocuments;
  private final BookingOperationsService operations;
  private final FinanceSettlementService financeSettlement;
  private final StoreBranchService storeBranches;

  public BookingService(
      BookingRepository bookings,
      CheckoutHoldReservationRepository checkoutHolds,
      ProductRepository products,
      InventoryAssetRepository assets,
      StockItemRepository stock,
      RateLimitService rateLimit,
      AuditService audit,
      CustomerAccountService customerAccounts, BundleRepository bundles, PromotionService promotionService,
      IdentityDocumentService identityDocuments, BookingOperationsService operations,
      FinanceSettlementService financeSettlement, StoreBranchService storeBranches) {
    this.bookings = bookings;
    this.checkoutHolds = checkoutHolds;
    this.products = products;
    this.assets = assets;
    this.stock = stock;
    this.rateLimit = rateLimit;
    this.audit = audit;
    this.customerAccounts = customerAccounts;
    this.bundles = bundles;
    this.promotionService = promotionService;
    this.identityDocuments = identityDocuments;
    this.operations = operations;
    this.financeSettlement = financeSettlement;
    this.storeBranches = storeBranches;
  }

  public Quote quote(QuoteRequest request) {
    return quote(request, null);
  }

  private Quote quote(QuoteRequest request, String excludedBookingId) {
    purgeExpiredHolds();
    validatePeriod(request.pickupTime(), request.returnTime());
    Map<String, Integer> quantities = normalizeItems(request.items());
    long rentalMinutes = Math.max(1, Duration.between(request.pickupTime(), request.returnTime()).toMinutes());
    long rentalHours = Math.max(1, (rentalMinutes + 59) / 60);
    long rentalDays = rentalDays(request.pickupTime(), request.returnTime());
    String rentalRate = normalizeRentalRate(request.rentalRate());
    List<QuoteLine> lines = new ArrayList<>();
    List<String> unavailable = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal equipmentDeposit = BigDecimal.ZERO;
    BigDecimal bookingDeposit = MANDATORY_RESERVATION_DEPOSIT;
    BigDecimal identityViolationFee = BigDecimal.ZERO;
    BigDecimal unauthorizedTransferFee = BigDecimal.ZERO;
    BigDecimal lateFeePerHour = BigDecimal.ZERO;
    BigDecimal impactPenaltyPercent = BigDecimal.ZERO;
    BigDecimal damageLiabilityLimit = BigDecimal.ZERO;

    for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
      Product product = products.findById(entry.getKey())
          .filter(Product::isActive)
          .orElseThrow(() -> ApiException.badRequest("Thiết bị không còn được cung cấp: " + entry.getKey()));
      int quantity = entry.getValue();
      boolean available = isAvailable(product, quantity, request.pickupTime(), request.returnTime(),
          excludedBookingId, request.holdToken());
      if (!available) {
        unavailable.add(product.getName());
      }
      RentalPricing.Charge charge = RentalPricing.calculateProduct(product.getHourlyPrice(), product.getHalfDayPrice(),
          product.getDailyPrice(), product.getTwoDayPrice(), product.getMultiDayPrice(), product.getExtraDayPrice(),
          product.getMultiDayDays(), request.pickupTime(), request.returnTime(), rentalRate);
      BigDecimal lineTotal = charge.total().multiply(BigDecimal.valueOf(quantity));
      total = total.add(lineTotal);
      equipmentDeposit = equipmentDeposit.add(product.getEquipmentDeposit().multiply(BigDecimal.valueOf(quantity)));
      BigDecimal productIdentityFee = configuredOr(product.getIdentityViolationFee(), product.getBookingDeposit());
      BigDecimal productImpactPercent = configuredOr(product.getImpactPenaltyPercent(), BigDecimal.valueOf(100));
      BigDecimal productDamageLimit = configuredOr(product.getDamageLiabilityLimit(),
          product.getEquipmentDeposit().max(product.getDailyPrice().multiply(BigDecimal.TEN)));
      identityViolationFee = identityViolationFee.add(productIdentityFee.multiply(BigDecimal.valueOf(quantity)));
      lateFeePerHour = lateFeePerHour.add(product.getLateFeePerHour().multiply(BigDecimal.valueOf(quantity)));
      impactPenaltyPercent = impactPenaltyPercent.max(productImpactPercent);
      damageLiabilityLimit = damageLiabilityLimit.add(productDamageLimit.multiply(BigDecimal.valueOf(quantity)));
      lines.add(new QuoteLine(product.getId(), product.getName(), product.getDailyPrice(), charge.unitPrice(),
          charge.pricingMode(), charge.billableUnits(), charge.extraDays(), quantity, lineTotal, available));
    }

    String bundleName = null;
    RentalPricing.Charge bundleCharge = null;
    if (request.bundleId() != null && !request.bundleId().isBlank()) {
      RentalBundle bundle = bundles.findByIdWithItems(request.bundleId()).filter(RentalBundle::isActive)
          .orElseThrow(() -> ApiException.badRequest("Combo không còn được cung cấp."));
      BigDecimal includedRetail = BigDecimal.ZERO;
      for (var bundleItem : bundle.getItems()) {
        Product includedProduct = products.findById(bundleItem.getProductId()).orElseThrow(() -> ApiException.badRequest("Combo chứa sản phẩm không tồn tại."));
        int requested = quantities.getOrDefault(bundleItem.getProductId(), 0);
        if (requested < bundleItem.getQuantity()) throw ApiException.badRequest("Chưa chọn đủ thiết bị trong combo " + bundle.getName() + ".");
        RentalPricing.Charge includedCharge = RentalPricing.calculateProduct(includedProduct.getHourlyPrice(),
            includedProduct.getHalfDayPrice(), includedProduct.getDailyPrice(), includedProduct.getTwoDayPrice(),
            includedProduct.getMultiDayPrice(), includedProduct.getExtraDayPrice(), includedProduct.getMultiDayDays(),
            request.pickupTime(), request.returnTime(), rentalRate);
        includedRetail = includedRetail.add(includedCharge.total().multiply(BigDecimal.valueOf(bundleItem.getQuantity())));
      }
      bundleCharge = RentalPricing.calculate(bundle.getHourlyPrice(), bundle.getDailyPrice(), bundle.getMultiDayPrice(),
          bundle.getMultiDayDays(), request.pickupTime(), request.returnTime(), rentalRate);
      total = total.subtract(includedRetail).add(bundleCharge.total());
      bundleName = bundle.getName();
    }
    BigDecimal subtotal = total;
    PromotionService.Application promotion = promotionService.apply(request.promotionCode(), request.pickupTime(),
        request.returnTime(), subtotal);
    BigDecimal discountAmount = promotion.discountAmount();
    total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
    BigDecimal deposit = equipmentDeposit.add(bookingDeposit).setScale(0, RoundingMode.HALF_UP);
    BigDecimal amountDueNow = bookingDeposit.setScale(0, RoundingMode.HALF_UP);
    BigDecimal amountDueBeforeHandover = total.add(deposit).setScale(0, RoundingMode.HALF_UP);
    return new Quote(rentalMinutes, rentalHours, rentalDays, subtotal, discountAmount, total, deposit,
        equipmentDeposit, bookingDeposit, amountDueNow, amountDueBeforeHandover,
        identityViolationFee, unauthorizedTransferFee,
        lateFeePerHour, impactPenaltyPercent, damageLiabilityLimit,
        unavailable.isEmpty(), unavailable, lines, request.bundleId(), bundleName,
        bundleCharge == null ? null : bundleCharge.pricingMode(),
        bundleCharge == null ? null : bundleCharge.unitPrice(),
        bundleCharge == null ? 0 : bundleCharge.billableUnits(),
        bundleCharge == null ? 0 : bundleCharge.extraDays(),
        promotion.code(), promotion.name(), promotion.discountPercent(),
        promotion.breakdown());
  }

  public synchronized HoldResponse hold(QuoteRequest request, String ownerPhone, String remoteAddress,
      String identityUploadToken) {
    purgeExpiredHolds();
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    String requestedToken = normalizeToken(request.holdToken());
    CheckoutHoldReservation existing = requestedToken == null ? null : checkoutHolds.findById(requestedToken).orElse(null);
    if (requestedToken != null && (existing == null || !existing.getOwnerPhone().equals(normalizedOwner)
        || !existing.getExpiresAt().isAfter(Instant.now()))) {
      throw ApiException.badRequest("Phiên giữ máy đã hết hạn. Vui lòng bắt đầu lại.");
    }
    if (existing == null && requestedToken == null) {
      var ownerHold = checkoutHolds.findFirstByOwnerPhoneAndExpiresAtAfterOrderByExpiresAtAsc(
          normalizedOwner, Instant.now()).orElse(null);
      if (ownerHold != null) {
        requestedToken = ownerHold.getToken();
        existing = ownerHold;
      }
    }
    if (existing == null) {
      rateLimit.check("hold:phone:" + normalizedOwner, 8, Duration.ofMinutes(15));
      rateLimit.check("hold:ip:" + remoteAddress, 30, Duration.ofMinutes(15));
      checkoutHolds.findByOwnerPhoneAndExpiresAtAfterOrderByExpiresAtAsc(normalizedOwner, Instant.now())
          .forEach(checkoutHolds::delete);
    } else {
      rateLimit.check("hold-update:phone:" + normalizedOwner, 120, Duration.ofMinutes(5));
    }

    QuoteRequest effectiveRequest = existing == null || request.holdToken() != null
        ? request
        : new QuoteRequest(request.pickupTime(), request.returnTime(), request.items(), request.bundleId(),
            requestedToken, request.promotionCode(), request.rentalRate());
    Quote quote = quote(effectiveRequest, null);
    if (!quote.available()) {
      return new HoldResponse(requestedToken, existing == null ? null : existing.getExpiresAt(), quote);
    }

    String token = existing == null ? UUID.randomUUID().toString() : requestedToken;
    Instant expiresAt = existing == null
        ? Instant.now().plus(TEMPORARY_HOLD_DURATION)
        : existing.getExpiresAt();
    String normalizedIdentityUploadToken = normalizeToken(identityUploadToken);
    String effectiveIdentityUploadToken = normalizedIdentityUploadToken != null
        ? normalizedIdentityUploadToken
        : (existing == null ? null : existing.getIdentityUploadToken());
    CheckoutHoldReservation hold = new CheckoutHoldReservation(
        token,
        request.pickupTime(),
        request.returnTime(),
        Map.copyOf(normalizeItems(request.items())),
        normalizeBundleId(request.bundleId()),
        normalizePromotionCode(request.promotionCode()),
        normalizeRentalRate(request.rentalRate()),
        normalizedOwner,
        effectiveIdentityUploadToken,
        existing == null ? null : existing.getPaymentProofUploadToken(),
        expiresAt);
    checkoutHolds.save(hold);
    return new HoldResponse(token, expiresAt, quote);
  }

  public void releaseHold(String holdToken, String ownerPhone) {
    String token = normalizeToken(holdToken);
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    if (token == null) return;
    checkoutHolds.findById(token)
        .filter(hold -> hold.getOwnerPhone().equals(normalizedOwner))
        .ifPresent(checkoutHolds::delete);
  }

  public List<CheckoutHold> listCheckoutHolds(String ownerPhone) {
    purgeExpiredHolds();
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    return checkoutHolds.findByOwnerPhoneAndExpiresAtAfterOrderByExpiresAtAsc(normalizedOwner, Instant.now()).stream()
        .map(this::toCheckoutHold)
        .toList();
  }

  public CheckoutHold checkoutHold(String holdToken, String ownerPhone) {
    purgeExpiredHolds();
    String token = normalizeToken(holdToken);
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    CheckoutHoldReservation hold = token == null ? null : checkoutHolds.findById(token).orElse(null);
    if (hold == null || !hold.getOwnerPhone().equals(normalizedOwner)
        || !hold.getExpiresAt().isAfter(Instant.now())) {
      throw ApiException.notFound("Phiên thanh toán không còn hiệu lực.");
    }
    return toCheckoutHold(hold);
  }

  public synchronized CheckoutHold attachPaymentProof(String holdToken, String paymentProofUploadToken,
      String ownerPhone) {
    purgeExpiredHolds();
    String token = normalizeToken(holdToken);
    String proofToken = normalizeToken(paymentProofUploadToken);
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    CheckoutHoldReservation hold = token == null ? null : checkoutHolds.findById(token).orElse(null);
    if (hold == null || !hold.getOwnerPhone().equals(normalizedOwner)
        || !hold.getExpiresAt().isAfter(Instant.now())) {
      throw ApiException.notFound("Phiên thanh toán không còn hiệu lực.");
    }
    if (proofToken == null) {
      throw ApiException.badRequest("Thiếu ảnh chuyển khoản cho phiên thanh toán.");
    }
    identityDocuments.validateUpload(proofToken, normalizedOwner);
    hold.attachPaymentProof(proofToken);
    return toCheckoutHold(checkoutHolds.save(hold));
  }

  private CheckoutHold toCheckoutHold(CheckoutHoldReservation hold) {
    List<ItemRequest> itemRequests = hold.getItems().entrySet().stream()
        .map(entry -> new ItemRequest(entry.getKey(), entry.getValue()))
        .toList();
    Quote quote = quote(new QuoteRequest(hold.getPickupTime(), hold.getReturnTime(), itemRequests,
        hold.getBundleId(), hold.getToken(), hold.getPromotionCode(), hold.getRentalRate()));
    List<CheckoutHoldItem> items = hold.getItems().entrySet().stream().map(entry -> {
      Product product = products.findById(entry.getKey())
          .orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị trong phiên thanh toán."));
      return new CheckoutHoldItem(product.getId(), product.getName(), product.getLevelCode(),
          product.getCategory(), entry.getValue());
    }).toList();
    String primaryProductId = items.stream()
        .filter(item -> "L1".equalsIgnoreCase(item.levelCode()))
        .map(CheckoutHoldItem::productId)
        .findFirst()
        .orElse(items.isEmpty() ? null : items.get(0).productId());
    return new CheckoutHold(hold.getToken(), hold.getExpiresAt(), hold.getPickupTime(), hold.getReturnTime(),
        items, primaryProductId, hold.getBundleId(), hold.getPromotionCode(), hold.getRentalRate(),
        hold.getIdentityUploadToken(), hold.getPaymentProofUploadToken(), quote);
  }

  @Transactional
  public synchronized Booking submit(SubmitRequest request, String sessionPhone, String remoteAddress) {
    String normalizedPhone = OtpService.normalizePhone(request.phone());
    if (!normalizedPhone.equals(OtpService.normalizePhone(sessionPhone))) {
      throw ApiException.forbidden("Tài khoản đăng nhập không khớp với số điện thoại đặt thuê.");
    }
    if (request.earlyPickupTime() != null) {
      LocalDateTime earliestEarlyPickup =
          request.pickupTime().toLocalDate().minusDays(1).atTime(21, 0);
      if (request.earlyPickupTime().isBefore(earliestEarlyPickup)
          || !request.earlyPickupTime().isBefore(request.pickupTime())) {
        throw ApiException.badRequest(
            "Thời gian nhận sớm phải từ 21:00 ngày trước và trước thời gian nhận máy chính thức.");
      }
    }
    purgeExpiredHolds();
    String holdToken = normalizeToken(request.holdToken());
    CheckoutHoldReservation hold = holdToken == null ? null : checkoutHolds.findById(holdToken).orElse(null);
    Map<String, Integer> requestedItems = normalizeItems(request.items());
    if (hold == null || !hold.getExpiresAt().isAfter(Instant.now())
        || !hold.getOwnerPhone().equals(normalizedPhone)
        || !holdMatches(hold, request.pickupTime(), request.returnTime(), requestedItems, request.bundleId(),
            request.promotionCode(), request.rentalRate())) {
      throw ApiException.badRequest("Phiên giữ máy không hợp lệ hoặc đã hết hạn. Vui lòng bắt đầu lại.");
    }
    if (hold.getPaymentProofUploadToken() != null
        && !hold.getPaymentProofUploadToken().equals(normalizeToken(request.paymentProofUploadToken()))) {
      throw ApiException.badRequest("Ảnh chuyển khoản không khớp với phiên thanh toán đang giữ.");
    }
    Quote quote = quote(new QuoteRequest(request.pickupTime(), request.returnTime(), request.items(),
        request.bundleId(), holdToken, request.promotionCode(), request.rentalRate()));
    if (!quote.available()) {
      throw ApiException.badRequest("Một hoặc nhiều thiết bị hiện không còn sẵn sàng: " + String.join(", ", quote.unavailableProducts()));
    }
    rateLimit.check("booking:" + normalizedPhone, 5, Duration.ofHours(1));
    rateLimit.check("booking:ip:" + remoteAddress, 20, Duration.ofHours(1));
    IdentityDocumentService.ClaimedDocuments claimedDocuments =
        identityDocuments.claim(request.identityUploadToken(), normalizedPhone);
    IdentityDocumentService.ClaimedDocuments claimedPaymentProof =
        identityDocuments.claim(request.paymentProofUploadToken(), normalizedPhone);
    IdentityDocumentService.ClaimedDocuments claimedBankAccount =
        identityDocuments.claim(request.bankAccountUploadToken(), normalizedPhone);
    StoreBranch storeBranch = storeBranches.requireForBooking(request.storeBranchId());

    List<BookingLine> lines = quote.lines().stream()
        .map(line -> new BookingLine(line.productId(), null, line.quantity(), line.dailyPrice(), line.unitPrice(),
            line.lineTotal(), line.pricingMode(), (int) Math.min(Integer.MAX_VALUE, line.billableUnits()), "RENTAL_V1"))
        .toList();
    Booking booking = new Booking(
        "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
        request.customerName().trim(),
        normalizedPhone,
        normalizedPhone,
        BookingState.PENDING_REVIEW,
        quote.totalAmount(),
        quote.depositRequired(),
        request.pickupTime(),
        request.returnTime(),
        request.bundleId(),
        request.note(),
        lines);
    booking.assignStoreBranch(storeBranch.getId(), storeBranch.getCode(), storeBranch.getName(),
        storeBranch.getAddress());
    booking.requestEarlyPickup(request.earlyPickupTime());
    booking.applyPromotion(quote.subtotalAmount(), quote.discountAmount(), quote.promotionCode());
    booking.applyPaymentBreakdown(quote.equipmentDeposit(), quote.bookingDeposit(), quote.amountDueNow());
    booking.attachIdentityDocuments(claimedDocuments.frontStorageKey(), claimedDocuments.backStorageKey());
    booking.attachPaymentProof(claimedPaymentProof.frontStorageKey());
    booking.attachBankAccount(claimedBankAccount.frontStorageKey());
    Booking saved = bookings.save(booking);
    operations.replaceReservations(saved, ReservationType.SOFT, "PUBLIC");
    checkoutHolds.deleteById(holdToken);
    customerAccounts.ensure(normalizedPhone, request.customerName());
    audit.record("PUBLIC", "BOOKING_CREATED", "BOOKING", saved.getId(), "Customer session from " + remoteAddress);
    return saved;
  }

  public List<Booking> listForAdmin(String query, String state) {
    return bookings.findAllWithItemsOrderByCreatedAtDesc().stream()
        .filter(booking -> state == null || state.isBlank() || "ALL".equals(state) || booking.getState().name().equals(state))
        .filter(booking -> matches(booking, query))
        .toList();
  }

  public IdentityDocumentService.StoredImage identityDocument(String bookingId, String side) {
    Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    return identityDocument(booking, side);
  }

  public IdentityDocumentService.StoredImage identityDocumentForCustomer(
      String bookingId, String phoneNormalized, String side) {
    return identityDocument(requireCustomerBooking(bookingId, phoneNormalized), side);
  }

  private IdentityDocumentService.StoredImage identityDocument(Booking booking, String side) {
    String reference = switch (side.toLowerCase()) {
      case "front" -> booking.getIdentityFrontReference();
      case "back" -> booking.getIdentityBackReference();
      default -> throw ApiException.badRequest("Mặt CCCD không hợp lệ.");
    };
    return identityDocuments.read(reference);
  }

  @Transactional
  public Booking transition(String id, BookingState nextState, String reason, String actor) {
    Booking booking = bookings.findByIdWithItemsForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    requireTransition(booking.getState(), nextState);
    if (requiresReason(nextState) && (reason == null || reason.isBlank())) {
      throw ApiException.badRequest("Vui lòng ghi lý do cho thao tác này.");
    }
    if (nextState == BookingState.TEMP_HOLD || nextState == BookingState.CONDITIONAL
        || nextState == BookingState.CONFIRMED || nextState == BookingState.READY_FOR_PICKUP) {
      Quote quote = quote(new QuoteRequest(booking.getPickupTime(), booking.getReturnTime(), booking.getItems().stream()
          .map(item -> new ItemRequest(item.getProductId(), item.getQuantity()))
          .toList(), booking.getBundleId(), null, booking.getPromotionCode(), null), booking.getId());
      if (!quote.available()) {
        throw ApiException.badRequest("Không thể giữ/duyệt đơn vì kho không sẵn sàng: " + String.join(", ", quote.unavailableProducts()));
      }
    }
    BookingState previousState = booking.getState();
    if (nextState == BookingState.CONFIRMED || nextState == BookingState.READY_FOR_PICKUP) {
      financeSettlement.initializeCommercialSnapshot(booking, actor);
    }
    if (nextState == BookingState.IN_USE) financeSettlement.assertCheckoutReady(booking);
    if (nextState == BookingState.IN_USE) operations.startUse(booking, actor);
    if (nextState == BookingState.COMPLETED) operations.completeUse(booking, actor);
    if (nextState == BookingState.REJECTED) operations.cancel(booking);
    booking.changeState(nextState, reason);
    Booking saved = bookings.save(booking);
    if (nextState == BookingState.COMPLETED) financeSettlement.completeService(saved, actor);
    if (nextState == BookingState.NEGOTIATION || nextState == BookingState.CONDITIONAL
        || nextState == BookingState.TEMP_HOLD) {
      operations.replaceReservations(saved, ReservationType.SOFT, actor);
    } else if (nextState == BookingState.CONFIRMED || nextState == BookingState.READY_FOR_PICKUP) {
      operations.replaceReservations(saved, ReservationType.HARD, actor);
      if (nextState == BookingState.READY_FOR_PICKUP) operations.autoAllocate(saved, actor);
    }
    audit.record(actor, "BOOKING_" + previousState + "_TO_" + nextState, "BOOKING", saved.getId(), reason);
    return saved;
  }

  /**
   * Sau khi kế toán ghi nhận một khoản thanh toán, nếu tiền giữ lịch (cọc 50.000đ) đã
   * đủ thì tự động duyệt đơn sang "Đã xác nhận" thay vì bắt admin bấm tay. Chỉ áp dụng
   * cho các đơn còn đang ở nhánh chờ duyệt; đơn đã xác nhận, đã giao hoặc đã từ chối
   * giữ nguyên trạng thái. Mọi lỗi nghiệp vụ (kho hết hàng, chuyển trạng thái không
   * hợp lệ) ném ra ApiException để lớp gọi tự quyết định — giao dịch ghi nhận tiền đã
   * commit ở transaction trước nên không bị ảnh hưởng.
   */
  @Transactional
  public void autoConfirmAfterDeposit(String bookingId, String actor) {
    Booking booking = bookings.findByIdWithItemsForUpdate(bookingId).orElse(null);
    if (booking == null) return;
    if (!AUTO_CONFIRMABLE_STATES.contains(booking.getState())) return;
    BigDecimal required = booking.getAmountDueNow();
    if (required == null || required.signum() <= 0) return;
    if (financeSettlement.reservationDepositPaid(bookingId).compareTo(required) < 0) return;
    transition(bookingId, BookingState.CONFIRMED,
        "Tự động xác nhận: đã ghi nhận đủ tiền giữ lịch " + required.toPlainString() + "đ.", actor);
  }

  @Transactional
  public Booking track(String bookingId, String phone) {
    String normalizedPhone = OtpService.normalizePhone(phone);
    Booking booking = bookings.findByIdWithItems(bookingId)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    if (!booking.getPhoneNormalized().equals(normalizedPhone)) {
      throw ApiException.forbidden("Thông tin tra cứu không khớp.");
    }
    return booking;
  }

  public List<ScheduleBlock> schedule(String productId, LocalDateTime from, LocalDateTime to) {
    products.findById(productId).filter(Product::isActive)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị."));
    List<ScheduleBlock> result = new ArrayList<>(overlapping(from, to).stream().map(booking -> {
      int quantity = booking.getItems().stream().filter(item -> productId.equals(item.getProductId()))
          .mapToInt(BookingLine::getQuantity).sum();
      return quantity == 0 ? null : new ScheduleBlock(booking.getPickupTime(), booking.getReturnTime(), quantity);
    }).filter(Objects::nonNull).toList());
    purgeExpiredHolds();
    checkoutHolds.findActiveOverlapping(Instant.now(), from, to).stream()
        .map(hold -> new ScheduleBlock(hold.getPickupTime(), hold.getReturnTime(),
            hold.getItems().getOrDefault(productId, 0)))
        .filter(block -> block.reservedQuantity() > 0)
        .forEach(result::add);
    return result;
  }

  @Transactional
  public Booking reviewEarlyPickup(String id, boolean approved, BigDecimal fee, String reason, String actor) {
    Booking booking = bookings.findByIdWithItemsForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    if (!booking.isEarlyPickupRequested()) throw ApiException.badRequest("Đơn không có yêu cầu nhận máy sớm.");
    booking.reviewEarlyPickup(approved, fee, reason);
    Booking saved = bookings.save(booking);
    String note = (reason == null ? "" : reason.trim()) + (approved ? " | Phí: " + booking.getEarlyPickupFee() : "");
    audit.record(actor, approved ? "EARLY_PICKUP_APPROVED" : "EARLY_PICKUP_REJECTED", "BOOKING", id, note);
    return saved;
  }

  public BookingOperationsService.OperationsSnapshot operations(String id) {
    if (!bookings.existsById(id)) throw ApiException.notFound("Không tìm thấy booking.");
    return operations.snapshot(id);
  }

  @Transactional
  public BookingOperationsService.OperationsSnapshot autoAllocate(String id, String actor) {
    Booking booking = bookings.findByIdWithItemsForUpdate(id)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    if (!List.of(BookingState.CONFIRMED, BookingState.READY_FOR_PICKUP).contains(booking.getState())) {
      throw ApiException.badRequest("Chỉ phân bổ thiết bị cho đơn đã xác nhận hoặc sẵn sàng bàn giao.");
    }
    operations.autoAllocate(booking, actor);
    audit.record(actor, "BOOKING_AUTO_ALLOCATED", "BOOKING", id, "Phân bổ primary theo tồn khả dụng");
    return operations.snapshot(id);
  }

  private boolean isAvailable(Product product, int quantity, LocalDateTime from, LocalDateTime to,
      String excludedBookingId, String excludedHoldToken) {
    LocalDateTime effectiveFrom = from.minus(PREPARATION_BUFFER);
    LocalDateTime effectiveTo = to.plus(PREPARATION_BUFFER);
    long capacity = "SERIALIZED".equals(product.getTrackingMode())
        ? assets.countByProductIdAndStatusIn(product.getId(), List.of("AVAILABLE", "IN_USE"))
        : stock.findById(product.getId()).map(item -> (long) item.getTotalQty()).orElse(0L);
    int reserved = overlapping(effectiveFrom, effectiveTo).stream()
        .filter(booking -> excludedBookingId == null || !booking.getId().equals(excludedBookingId))
        .filter(booking -> !operations.hasActiveReservation(booking.getId()))
        .flatMap(booking -> booking.getItems().stream())
        .filter(item -> product.getId().equals(item.getProductId()))
        .mapToInt(BookingLine::getQuantity).sum();
    reserved += operations.activeReservedQuantity(product.getId(), effectiveFrom, effectiveTo, excludedBookingId);
    int temporarilyHeld = checkoutHolds.findActiveOverlapping(Instant.now(), effectiveFrom, effectiveTo).stream()
        .filter(hold -> excludedHoldToken == null || !hold.getToken().equals(excludedHoldToken))
        .mapToInt(hold -> hold.getItems().getOrDefault(product.getId(), 0))
        .sum();
    return capacity - reserved - temporarilyHeld >= quantity;
  }

  private List<Booking> overlapping(LocalDateTime from, LocalDateTime to) {
    LocalDateTime now = LocalDateTime.now();
    return bookings.findOverlappingWithItems(from, to, List.of(
        BookingState.PENDING_REVIEW, BookingState.NEGOTIATION, BookingState.CONDITIONAL,
        BookingState.TEMP_HOLD, BookingState.CONFIRMED, BookingState.READY_FOR_PICKUP,
        BookingState.IN_USE, BookingState.INCIDENT))
        .stream().filter(booking -> booking.getState() != BookingState.PENDING_REVIEW || booking.hasActiveHold(now)).toList();
  }

  private static void validatePeriod(LocalDateTime pickupTime, LocalDateTime returnTime) {
    if (pickupTime == null || returnTime == null || !returnTime.isAfter(pickupTime)) {
      throw ApiException.badRequest("Thời gian trả máy phải sau thời gian nhận máy.");
    }
  }

  private static long rentalDays(LocalDateTime pickupTime, LocalDateTime returnTime) {
    long minutes = Duration.between(pickupTime, returnTime).toMinutes();
    return Math.max(1, (minutes + 1439) / 1440);
  }

  private static Map<String, Integer> normalizeItems(List<ItemRequest> items) {
    if (items == null || items.isEmpty()) {
      throw ApiException.badRequest("Bạn chưa chọn thiết bị.");
    }
    Map<String, Integer> quantities = new LinkedHashMap<>();
    for (ItemRequest item : items) {
      if (item.productId() == null || item.productId().isBlank() || item.quantity() < 1 || item.quantity() > 10) {
        throw ApiException.badRequest("Thiết bị hoặc số lượng không hợp lệ.");
      }
      quantities.merge(item.productId(), item.quantity(), Integer::sum);
    }
    return quantities;
  }

  @Transactional
  public void purgeExpiredHolds() {
    checkoutHolds.deleteByExpiresAtLessThanEqual(Instant.now());
  }

  private static boolean holdMatches(CheckoutHoldReservation hold, LocalDateTime pickup,
      LocalDateTime returned, Map<String, Integer> requestedItems, String requestedBundleId,
      String requestedPromotionCode, String requestedRentalRate) {
    return hold.getPickupTime().equals(pickup)
        && hold.getReturnTime().equals(returned)
        && hold.getItems().equals(requestedItems)
        && Objects.equals(hold.getBundleId(), normalizeBundleId(requestedBundleId))
        && Objects.equals(hold.getPromotionCode(), normalizePromotionCode(requestedPromotionCode))
        && Objects.equals(hold.getRentalRate(), normalizeRentalRate(requestedRentalRate));
  }

  public IdentityDocumentService.StoredImage paymentProof(String bookingId) {
    Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    return identityDocuments.read(booking.getPaymentProofReference());
  }

  public IdentityDocumentService.StoredImage paymentProofForCustomer(
      String bookingId, String phoneNormalized) {
    Booking booking = requireCustomerBooking(bookingId, phoneNormalized);
    return identityDocuments.read(booking.getPaymentProofReference());
  }

  public IdentityDocumentService.StoredImage bankAccountProof(String bookingId) {
    Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    if (booking.getBankAccountReference() == null) {
      throw ApiException.notFound("Đơn này chưa có ảnh tài khoản ngân hàng.");
    }
    return identityDocuments.read(booking.getBankAccountReference());
  }

  private Booking requireCustomerBooking(String bookingId, String phoneNormalized) {
    Booking booking = bookings.findById(bookingId)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
    if (phoneNormalized == null || !phoneNormalized.equals(booking.getPhoneNormalized())) {
      throw ApiException.forbidden("Bạn không có quyền xem tài liệu của booking này.");
    }
    return booking;
  }

  private static String normalizeToken(String token) {
    return token == null || token.isBlank() ? null : token.trim();
  }

  private static String normalizeBundleId(String bundleId) {
    return bundleId == null || bundleId.isBlank() ? null : bundleId.trim();
  }

  private static String normalizePromotionCode(String promotionCode) {
    return promotionCode == null || promotionCode.isBlank() ? null : promotionCode.trim().toUpperCase();
  }

  private static String normalizeRentalRate(String rentalRate) {
    if (rentalRate == null || rentalRate.isBlank()) return null;
    String normalized = rentalRate.trim().toUpperCase();
    if (!Set.of("HOURLY", "HALF_DAY", "DAILY", "TWO_DAY", "MULTI_DAY").contains(normalized)) {
      throw ApiException.badRequest("Gói giá thuê không hợp lệ.");
    }
    return normalized;
  }

  private static boolean matches(Booking booking, String query) {
    if (query == null || query.isBlank()) {
      return true;
    }
    String needle = query.trim().toLowerCase();
    return (booking.getId() + " " + booking.getCustomerName() + " " + booking.getPhone()).toLowerCase().contains(needle);
  }

  private static void requireTransition(BookingState current, BookingState next) {
    boolean allowed = switch (current) {
      case PENDING_REVIEW -> next == BookingState.NEGOTIATION || next == BookingState.CONDITIONAL
          || next == BookingState.TEMP_HOLD || next == BookingState.CONFIRMED || next == BookingState.REJECTED;
      case NEGOTIATION -> next == BookingState.CONDITIONAL || next == BookingState.TEMP_HOLD
          || next == BookingState.CONFIRMED || next == BookingState.REJECTED;
      case CONDITIONAL -> next == BookingState.NEGOTIATION || next == BookingState.TEMP_HOLD
          || next == BookingState.CONFIRMED || next == BookingState.REJECTED;
      case TEMP_HOLD -> next == BookingState.NEGOTIATION || next == BookingState.CONFIRMED
          || next == BookingState.REJECTED;
      case CONFIRMED -> next == BookingState.READY_FOR_PICKUP || next == BookingState.IN_USE
          || next == BookingState.REJECTED;
      case READY_FOR_PICKUP -> next == BookingState.IN_USE || next == BookingState.REJECTED;
      case IN_USE -> next == BookingState.COMPLETED || next == BookingState.INCIDENT;
      case INCIDENT -> next == BookingState.IN_USE || next == BookingState.COMPLETED;
      case COMPLETED, REJECTED -> false;
    };
    if (!allowed) {
      throw ApiException.badRequest("Không thể chuyển trạng thái từ " + current + " sang " + next + ".");
    }
  }

  private static boolean requiresReason(BookingState nextState) {
    return nextState == BookingState.NEGOTIATION || nextState == BookingState.CONDITIONAL
        || nextState == BookingState.TEMP_HOLD || nextState == BookingState.REJECTED
        || nextState == BookingState.INCIDENT;
  }

  private static BigDecimal configuredOr(BigDecimal configured, BigDecimal fallback) {
    return configured != null && configured.signum() > 0 ? configured : fallback.max(BigDecimal.ZERO);
  }

  public record ItemRequest(String productId, int quantity) {}
  public record QuoteRequest(LocalDateTime pickupTime, LocalDateTime returnTime, List<ItemRequest> items,
                             String bundleId, String holdToken, String promotionCode, String rentalRate) {}
  public record QuoteLine(String productId, String name, BigDecimal dailyPrice, BigDecimal unitPrice,
                          String pricingMode, long billableUnits, int extraDays, int quantity,
                          BigDecimal lineTotal, boolean available) {}
  public record Quote(long rentalMinutes, long rentalHours, long rentalDays, BigDecimal subtotalAmount,
                      BigDecimal discountAmount, BigDecimal totalAmount,
                      BigDecimal depositRequired, BigDecimal equipmentDeposit, BigDecimal bookingDeposit,
                      BigDecimal amountDueNow, BigDecimal amountDueBeforeHandover,
                      BigDecimal identityViolationFee, BigDecimal unauthorizedTransferFee,
                      BigDecimal lateFeePerHour, BigDecimal impactPenaltyPercent, BigDecimal damageLiabilityLimit,
                      boolean available, List<String> unavailableProducts,
                      List<QuoteLine> lines, String bundleId, String bundleName, String bundlePricingMode,
                      BigDecimal bundleUnitPrice, long bundleBillableUnits, int bundleExtraDays, String promotionCode,
                      String promotionName, BigDecimal discountPercent,
                      List<PromotionService.DailyDiscount> promotionBreakdown) {}
  public record HoldResponse(String holdToken, Instant expiresAt, Quote quote) {}
  public record CheckoutHoldItem(String productId, String productName, String levelCode,
                                 String category, int quantity) {}
  public record CheckoutHold(String holdToken, Instant expiresAt, LocalDateTime pickupTime,
                             LocalDateTime returnTime, List<CheckoutHoldItem> items,
                             String primaryProductId, String bundleId, String promotionCode,
                             String rentalRate, String identityUploadToken,
                             String paymentProofUploadToken, Quote quote) {}
  public record SubmitRequest(String customerName, String phone, String bundleId, LocalDateTime pickupTime,
                              LocalDateTime returnTime, String note, List<ItemRequest> items,
                               LocalDateTime earlyPickupTime, String identityUploadToken, String paymentProofUploadToken,
                               String bankAccountUploadToken,
                               String holdToken, String promotionCode, String storeBranchId, String rentalRate) {}
  public record ScheduleBlock(LocalDateTime pickupTime, LocalDateTime returnTime, int reservedQuantity) {}

}
