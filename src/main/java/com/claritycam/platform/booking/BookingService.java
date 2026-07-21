package com.claritycam.platform.booking;

import com.claritycam.platform.audit.AuditService;
import com.claritycam.platform.catalog.Product;
import com.claritycam.platform.catalog.ProductRepository;
import com.claritycam.platform.catalog.BundleRepository;
import com.claritycam.platform.catalog.RentalBundle;
import com.claritycam.platform.common.ApiException;
import com.claritycam.platform.common.RateLimitService;
import com.claritycam.platform.inventory.InventoryAssetRepository;
import com.claritycam.platform.inventory.StockItemRepository;
import com.claritycam.platform.customer.CustomerAccountService;
import com.claritycam.platform.customer.IdentityDocumentService;
import com.claritycam.platform.otp.OtpService;
import com.claritycam.platform.promotion.PromotionService;
import com.claritycam.platform.finance.FinanceSettlementService;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
  private static final Duration TEMPORARY_HOLD_DURATION = Duration.ofMinutes(5);
  private static final Duration PREPARATION_BUFFER = Duration.ofMinutes(30);

  private final BookingRepository bookings;
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
  private final Map<String, TemporaryHold> temporaryHolds = new ConcurrentHashMap<>();

  public BookingService(
      BookingRepository bookings,
      ProductRepository products,
      InventoryAssetRepository assets,
      StockItemRepository stock,
      RateLimitService rateLimit,
      AuditService audit,
      CustomerAccountService customerAccounts, BundleRepository bundles, PromotionService promotionService,
      IdentityDocumentService identityDocuments, BookingOperationsService operations,
      FinanceSettlementService financeSettlement) {
    this.bookings = bookings;
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
    List<QuoteLine> lines = new ArrayList<>();
    List<String> unavailable = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    BigDecimal equipmentDeposit = BigDecimal.ZERO;
    BigDecimal bookingDeposit = BigDecimal.ZERO;
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
          request.pickupTime(), request.returnTime());
      BigDecimal lineTotal = charge.total().multiply(BigDecimal.valueOf(quantity));
      total = total.add(lineTotal);
      equipmentDeposit = equipmentDeposit.add(product.getEquipmentDeposit().multiply(BigDecimal.valueOf(quantity)));
      bookingDeposit = bookingDeposit.add(product.getBookingDeposit().multiply(BigDecimal.valueOf(quantity)));
      BigDecimal productIdentityFee = configuredOr(product.getIdentityViolationFee(), product.getBookingDeposit());
      BigDecimal productTransferFee = configuredOr(product.getUnauthorizedTransferFee(),
          product.getDailyPrice().multiply(BigDecimal.valueOf(0.30)));
      BigDecimal productImpactPercent = configuredOr(product.getImpactPenaltyPercent(), BigDecimal.valueOf(100));
      BigDecimal productDamageLimit = configuredOr(product.getDamageLiabilityLimit(),
          product.getEquipmentDeposit().max(product.getDailyPrice().multiply(BigDecimal.TEN)));
      identityViolationFee = identityViolationFee.add(productIdentityFee.multiply(BigDecimal.valueOf(quantity)));
      unauthorizedTransferFee = unauthorizedTransferFee.add(productTransferFee.multiply(BigDecimal.valueOf(quantity)));
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
            includedProduct.getMultiDayPrice(), includedProduct.getExtraDayPrice(), request.pickupTime(), request.returnTime());
        includedRetail = includedRetail.add(includedCharge.total().multiply(BigDecimal.valueOf(bundleItem.getQuantity())));
      }
      bundleCharge = RentalPricing.calculate(bundle.getHourlyPrice(), bundle.getDailyPrice(), bundle.getMultiDayPrice(),
          bundle.getMultiDayDays(), request.pickupTime(), request.returnTime());
      total = total.subtract(includedRetail).add(bundleCharge.total());
      bundleName = bundle.getName();
    }
    BigDecimal subtotal = total;
    PromotionService.Application promotion = promotionService.apply(request.promotionCode(), request.pickupTime(),
        request.returnTime(), subtotal);
    BigDecimal discountAmount = promotion.discountAmount();
    total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
    BigDecimal deposit = equipmentDeposit.add(bookingDeposit).setScale(0, RoundingMode.HALF_UP);
    BigDecimal amountDueNow = total.add(deposit).setScale(0, RoundingMode.HALF_UP);
    return new Quote(rentalMinutes, rentalHours, rentalDays, subtotal, discountAmount, total, deposit,
        equipmentDeposit, bookingDeposit, amountDueNow, identityViolationFee, unauthorizedTransferFee,
        lateFeePerHour, impactPenaltyPercent, damageLiabilityLimit,
        unavailable.isEmpty(), unavailable, lines, request.bundleId(), bundleName,
        bundleCharge == null ? null : bundleCharge.pricingMode(),
        bundleCharge == null ? null : bundleCharge.unitPrice(),
        bundleCharge == null ? 0 : bundleCharge.billableUnits(),
        bundleCharge == null ? 0 : bundleCharge.extraDays(),
        promotion.code(), promotion.name(), promotion.discountPercent(),
        promotion.breakdown());
  }

  public synchronized HoldResponse hold(QuoteRequest request, String ownerPhone, String remoteAddress) {
    purgeExpiredHolds();
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    String requestedToken = normalizeToken(request.holdToken());
    TemporaryHold existing = requestedToken == null ? null : temporaryHolds.get(requestedToken);
    if (requestedToken != null && (existing == null || !existing.ownerPhone().equals(normalizedOwner))) {
      throw ApiException.badRequest("Phiên giữ máy đã hết hạn. Vui lòng bắt đầu lại.");
    }
    if (existing == null && requestedToken == null) {
      var ownerHold = temporaryHolds.entrySet().stream()
          .filter(entry -> entry.getValue().ownerPhone().equals(normalizedOwner))
          .findFirst()
          .orElse(null);
      if (ownerHold != null) {
        requestedToken = ownerHold.getKey();
        existing = ownerHold.getValue();
      }
    }
    if (existing == null) {
      rateLimit.check("hold:phone:" + normalizedOwner, 8, Duration.ofMinutes(15));
      rateLimit.check("hold:ip:" + remoteAddress, 30, Duration.ofMinutes(15));
      temporaryHolds.entrySet().removeIf(entry -> entry.getValue().ownerPhone().equals(normalizedOwner));
    } else {
      rateLimit.check("hold-update:phone:" + normalizedOwner, 120, Duration.ofMinutes(5));
    }

    QuoteRequest effectiveRequest = existing == null || request.holdToken() != null
        ? request
        : new QuoteRequest(request.pickupTime(), request.returnTime(), request.items(), request.bundleId(),
            requestedToken, request.promotionCode());
    Quote quote = quote(effectiveRequest, null);
    if (!quote.available()) {
      return new HoldResponse(requestedToken, existing == null ? null : existing.expiresAt(), quote);
    }

    String token = existing == null ? UUID.randomUUID().toString() : requestedToken;
    Instant expiresAt = existing == null
        ? Instant.now().plus(TEMPORARY_HOLD_DURATION)
        : existing.expiresAt();
    TemporaryHold hold = new TemporaryHold(
        token,
        request.pickupTime(),
        request.returnTime(),
        Map.copyOf(normalizeItems(request.items())),
        normalizeBundleId(request.bundleId()),
        normalizePromotionCode(request.promotionCode()),
        normalizedOwner,
        expiresAt);
    temporaryHolds.put(token, hold);
    return new HoldResponse(token, expiresAt, quote);
  }

  public void releaseHold(String holdToken, String ownerPhone) {
    String token = normalizeToken(holdToken);
    String normalizedOwner = OtpService.normalizePhone(ownerPhone);
    if (token != null) temporaryHolds.computeIfPresent(token,
        (ignored, hold) -> hold.ownerPhone().equals(normalizedOwner) ? null : hold);
  }

  @Transactional
  public synchronized Booking submit(SubmitRequest request, String sessionPhone, String remoteAddress) {
    String normalizedPhone = OtpService.normalizePhone(request.phone());
    if (!normalizedPhone.equals(OtpService.normalizePhone(sessionPhone))) {
      throw ApiException.forbidden("Tài khoản đăng nhập không khớp với số điện thoại đặt thuê.");
    }
    if (request.earlyPickupTime() != null && !request.earlyPickupTime().isBefore(request.pickupTime())) {
      throw ApiException.badRequest("Thời gian nhận sớm phải trước thời gian nhận máy chính thức.");
    }
    purgeExpiredHolds();
    String holdToken = normalizeToken(request.holdToken());
    TemporaryHold hold = holdToken == null ? null : temporaryHolds.get(holdToken);
    Map<String, Integer> requestedItems = normalizeItems(request.items());
    if (hold == null || !hold.ownerPhone().equals(normalizedPhone)
        || !hold.matches(request.pickupTime(), request.returnTime(), requestedItems, request.bundleId(), request.promotionCode())) {
      throw ApiException.badRequest("Phiên giữ máy không hợp lệ hoặc đã hết hạn. Vui lòng bắt đầu lại.");
    }
    Quote quote = quote(new QuoteRequest(request.pickupTime(), request.returnTime(), request.items(),
        request.bundleId(), holdToken, request.promotionCode()));
    if (!quote.available()) {
      throw ApiException.badRequest("Một hoặc nhiều thiết bị hiện không còn sẵn sàng: " + String.join(", ", quote.unavailableProducts()));
    }
    rateLimit.check("booking:" + normalizedPhone, 5, Duration.ofHours(1));
    rateLimit.check("booking:ip:" + remoteAddress, 20, Duration.ofHours(1));
    IdentityDocumentService.ClaimedDocuments claimedDocuments =
        identityDocuments.claim(request.identityUploadToken(), normalizedPhone);

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
    if (request.earlyPickupTime() != null && !request.earlyPickupTime().isBefore(request.pickupTime())) {
      throw ApiException.badRequest("Thời gian nhận sớm phải trước thời gian nhận máy chính thức.");
    }
    booking.requestEarlyPickup(request.earlyPickupTime());
    booking.applyPromotion(quote.subtotalAmount(), quote.discountAmount(), quote.promotionCode());
    booking.applyPaymentBreakdown(quote.equipmentDeposit(), quote.bookingDeposit(), quote.amountDueNow());
    booking.attachIdentityDocuments(claimedDocuments.frontStorageKey(), claimedDocuments.backStorageKey());
    Booking saved = bookings.save(booking);
    operations.replaceReservations(saved, ReservationType.SOFT, "PUBLIC");
    temporaryHolds.remove(holdToken);
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
          .toList(), booking.getBundleId(), null, booking.getPromotionCode()), booking.getId());
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

  @Transactional
  public Booking track(String bookingId, String phone) {
    String normalizedPhone = OtpService.normalizePhone(phone);
    Booking booking = bookings.findById(bookingId).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
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
    temporaryHolds.values().stream()
        .filter(hold -> hold.overlaps(from, to))
        .map(hold -> new ScheduleBlock(hold.pickupTime(), hold.returnTime(),
            hold.items().getOrDefault(productId, 0)))
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
    int temporarilyHeld = temporaryHolds.values().stream()
        .filter(hold -> excludedHoldToken == null || !hold.token().equals(excludedHoldToken))
        .filter(hold -> hold.overlaps(effectiveFrom, effectiveTo))
        .mapToInt(hold -> hold.items().getOrDefault(product.getId(), 0))
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

  private void purgeExpiredHolds() {
    Instant now = Instant.now();
    temporaryHolds.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
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
                             String bundleId, String holdToken, String promotionCode) {}
  public record QuoteLine(String productId, String name, BigDecimal dailyPrice, BigDecimal unitPrice,
                          String pricingMode, long billableUnits, int extraDays, int quantity,
                          BigDecimal lineTotal, boolean available) {}
  public record Quote(long rentalMinutes, long rentalHours, long rentalDays, BigDecimal subtotalAmount,
                      BigDecimal discountAmount, BigDecimal totalAmount,
                      BigDecimal depositRequired, BigDecimal equipmentDeposit, BigDecimal bookingDeposit,
                      BigDecimal amountDueNow, BigDecimal identityViolationFee, BigDecimal unauthorizedTransferFee,
                      BigDecimal lateFeePerHour, BigDecimal impactPenaltyPercent, BigDecimal damageLiabilityLimit,
                      boolean available, List<String> unavailableProducts,
                      List<QuoteLine> lines, String bundleId, String bundleName, String bundlePricingMode,
                      BigDecimal bundleUnitPrice, long bundleBillableUnits, int bundleExtraDays, String promotionCode,
                      String promotionName, BigDecimal discountPercent,
                      List<PromotionService.DailyDiscount> promotionBreakdown) {}
  public record HoldResponse(String holdToken, Instant expiresAt, Quote quote) {}
  public record SubmitRequest(String customerName, String phone, String bundleId, LocalDateTime pickupTime,
                              LocalDateTime returnTime, String note, List<ItemRequest> items,
                              LocalDateTime earlyPickupTime, String identityUploadToken, String holdToken,
                              String promotionCode) {}
  public record ScheduleBlock(LocalDateTime pickupTime, LocalDateTime returnTime, int reservedQuantity) {}

  private record TemporaryHold(String token, LocalDateTime pickupTime, LocalDateTime returnTime,
                               Map<String, Integer> items, String bundleId, String promotionCode, String ownerPhone,
                               Instant expiresAt) {
    boolean overlaps(LocalDateTime from, LocalDateTime to) {
      return pickupTime.isBefore(to) && returnTime.isAfter(from);
    }

    boolean matches(LocalDateTime pickup, LocalDateTime returned, Map<String, Integer> requestedItems,
                    String requestedBundleId, String requestedPromotionCode) {
      return pickupTime.equals(pickup)
          && returnTime.equals(returned)
          && items.equals(requestedItems)
          && Objects.equals(bundleId, normalizeBundleId(requestedBundleId))
          && Objects.equals(promotionCode, normalizePromotionCode(requestedPromotionCode));
    }
  }
}
