package com.claritycam.platform.service.finance;

import com.claritycam.platform.model.catalog.Product;
import com.claritycam.platform.model.finance.AssetCostAllocation;
import com.claritycam.platform.model.finance.AssetRevenueAllocation;
import com.claritycam.platform.model.finance.BookingCharge;
import com.claritycam.platform.model.finance.BookingSettlement;
import com.claritycam.platform.model.finance.CommercialSnapshot;
import com.claritycam.platform.model.finance.CommercialSnapshotLine;
import com.claritycam.platform.model.finance.CustomerReceivable;
import com.claritycam.platform.model.finance.FinanceOutboxEvent;
import com.claritycam.platform.model.finance.FinancialDocument;
import com.claritycam.platform.model.finance.FinancialLedgerEntry;
import com.claritycam.platform.model.finance.FinancialPeriod;
import com.claritycam.platform.model.finance.OperationalExpense;
import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.model.finance.PaymentAllocation;
import com.claritycam.platform.model.finance.ReconciliationFinding;
import com.claritycam.platform.model.finance.RefundRequest;
import com.claritycam.platform.repository.booking.BookingAllocationRepository;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.catalog.ProductRepository;
import com.claritycam.platform.repository.finance.AssetCostAllocationRepository;
import com.claritycam.platform.repository.finance.AssetRevenueAllocationRepository;
import com.claritycam.platform.repository.finance.BookingChargeRepository;
import com.claritycam.platform.repository.finance.BookingSettlementRepository;
import com.claritycam.platform.repository.finance.CommercialSnapshotLineRepository;
import com.claritycam.platform.repository.finance.CommercialSnapshotRepository;
import com.claritycam.platform.repository.finance.CustomerReceivableRepository;
import com.claritycam.platform.repository.finance.FinanceOutboxEventRepository;
import com.claritycam.platform.repository.finance.FinancialDocumentRepository;
import com.claritycam.platform.repository.finance.FinancialLedgerEntryRepository;
import com.claritycam.platform.repository.finance.FinancialPeriodRepository;
import com.claritycam.platform.repository.finance.OperationalExpenseRepository;
import com.claritycam.platform.repository.finance.PaymentAllocationRepository;
import com.claritycam.platform.repository.finance.PaymentRepository;
import com.claritycam.platform.repository.finance.ReconciliationFindingRepository;
import com.claritycam.platform.repository.finance.RefundRequestRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.service.audit.AuditService;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingAllocation;
import com.claritycam.platform.model.booking.BookingLine;
import com.claritycam.platform.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceSettlementService {
  private static final String RESERVATION_DEPOSIT = "RESERVATION_DEPOSIT";
  private static final String SECURITY_DEPOSIT = "SECURITY_DEPOSIT";
  private static final String RENTAL_PREPAYMENT = "RENTAL_PREPAYMENT";
  private static final String EXTENSION_CHARGE = "EXTENSION_CHARGE";
  private static final String CUSTOMER_RECOVERY = "CUSTOMER_RECOVERY";
  private static final String CUSTOMER_OVERPAYMENT = "CUSTOMER_OVERPAYMENT";

  private final BookingRepository bookings;
  private final ProductRepository products;
  private final BookingAllocationRepository bookingAllocations;
  private final InventoryAssetRepository inventoryAssets;
  private final CommercialSnapshotRepository snapshots;
  private final CommercialSnapshotLineRepository snapshotLines;
  private final FinancialDocumentRepository documents;
  private final FinancialLedgerEntryRepository ledger;
  private final PaymentRepository payments;
  private final PaymentAllocationRepository paymentAllocations;
  private final BookingChargeRepository charges;
  private final BookingSettlementRepository settlements;
  private final RefundRequestRepository refunds;
  private final CustomerReceivableRepository receivables;
  private final AssetRevenueAllocationRepository assetAllocations;
  private final FinanceOutboxEventRepository outbox;
  private final ReconciliationFindingRepository findings;
  private final FinancialPeriodRepository periods;
  private final OperationalExpenseRepository expenses;
  private final AssetCostAllocationRepository assetCosts;
  private final AuditService audit;

  public FinanceSettlementService(BookingRepository bookings, ProductRepository products,
      BookingAllocationRepository bookingAllocations, InventoryAssetRepository inventoryAssets,
      CommercialSnapshotRepository snapshots,
      CommercialSnapshotLineRepository snapshotLines, FinancialDocumentRepository documents,
      FinancialLedgerEntryRepository ledger, PaymentRepository payments,
      PaymentAllocationRepository paymentAllocations, BookingChargeRepository charges,
      BookingSettlementRepository settlements, RefundRequestRepository refunds,
      CustomerReceivableRepository receivables, AssetRevenueAllocationRepository assetAllocations,
      FinanceOutboxEventRepository outbox, ReconciliationFindingRepository findings,
      FinancialPeriodRepository periods, OperationalExpenseRepository expenses,
      AssetCostAllocationRepository assetCosts, AuditService audit) {
    this.bookings = bookings;
    this.products = products;
    this.bookingAllocations = bookingAllocations;
    this.inventoryAssets = inventoryAssets;
    this.snapshots = snapshots;
    this.snapshotLines = snapshotLines;
    this.documents = documents;
    this.ledger = ledger;
    this.payments = payments;
    this.paymentAllocations = paymentAllocations;
    this.charges = charges;
    this.settlements = settlements;
    this.refunds = refunds;
    this.receivables = receivables;
    this.assetAllocations = assetAllocations;
    this.outbox = outbox;
    this.findings = findings;
    this.periods = periods;
    this.expenses = expenses;
    this.assetCosts = assetCosts;
    this.audit = audit;
  }

  @Transactional
  public CommercialSnapshot initializeCommercialSnapshot(Booking booking, String actor) {
    CommercialSnapshot existing = snapshots.findById(booking.getId()).orElse(null);
    if (existing != null) return existing;
    CommercialSnapshot snapshot = snapshots.save(new CommercialSnapshot(booking.getId(), booking.getSubtotalAmount(),
        booking.getDiscountAmount(), booking.getTotalAmount(), booking.getEquipmentDeposit(),
        booking.getBookingDeposit(), booking.getPickupTime(), booking.getReturnTime(), actor));
    List<CommercialSnapshotLine> lines = booking.getItems().stream().map(line -> snapshotLine(booking, line)).toList();
    snapshotLines.saveAll(lines);
    outbox.save(event("BOOKING", booking.getId(), "COMMERCIAL_SNAPSHOT_FROZEN", booking.getId(),
        "{\"pricingRuleVersion\":\"RENTAL_V1\"}"));
    return snapshot;
  }

  private CommercialSnapshotLine snapshotLine(Booking booking, BookingLine line) {
    BigDecimal listed = line.getListedUnitPriceSnapshot();
    if (listed.signum() == 0) {
      listed = products.findById(line.getProductId()).map(product -> product.getDailyPrice()).orElse(BigDecimal.ZERO);
    }
    BigDecimal chargeAmount = line.getChargeAmountSnapshot();
    if (chargeAmount.signum() == 0 && booking.getItems().size() == 1) chargeAmount = booking.getSubtotalAmount();
    return new CommercialSnapshotLine(booking.getId(), line.getProductId(), line.getQuantity(), listed,
        line.getChargeUnitPriceSnapshot(), chargeAmount, line.getPricingModeSnapshot(),
        line.getBillableUnitsSnapshot(), line.getPricingRuleVersion());
  }

  @Transactional
  public Payment recordPayment(String bookingId, BigDecimal amount, String method, String providerReference,
      String idempotencyKey, String note, String actor) {
    String key = requiredKey(idempotencyKey);
    Payment duplicate = payments.findByIdempotencyKey(key).orElse(null);
    if (duplicate != null) return duplicate;
    if (providerReference != null && !providerReference.isBlank()) {
      duplicate = payments.findByProviderTransactionId(providerReference.trim()).orElse(null);
      if (duplicate != null) return duplicate;
    }
    Booking booking = bookingForUpdate(bookingId);
    CommercialSnapshot snapshot = initializeCommercialSnapshot(booking, actor);
    BigDecimal received = positive(amount, "Số tiền thanh toán phải lớn hơn 0.");
    Payment payment = new Payment("PAY-" + compactId(), bookingId, received, required(method, "Phương thức thanh toán"),
        normalize(providerReference, "MANUAL-" + compactId()), key, actor, normalize(note, ""));

    List<PaymentAllocation> allocations = new ArrayList<>();
    BigDecimal remaining = received;
    remaining = allocate(payment, allocations, remaining, RESERVATION_DEPOSIT, bookingId,
        snapshot.getReservationDeposit(), actor);
    remaining = allocate(payment, allocations, remaining, SECURITY_DEPOSIT, bookingId,
        snapshot.getEquipmentDeposit(), actor);
    remaining = allocate(payment, allocations, remaining, RENTAL_PREPAYMENT, bookingId,
        snapshot.getNetRentalAmount(), actor);
    remaining = allocate(payment, allocations, remaining, EXTENSION_CHARGE, bookingId,
        confirmedChargeTotal(bookingId, "EXTENSION"), actor);
    remaining = allocateConfirmedCharges(payment, allocations, remaining, actor);
    if (remaining.signum() > 0) {
      PaymentAllocation allocation = new PaymentAllocation(payment.getId(), bookingId, CUSTOMER_OVERPAYMENT,
          bookingId, remaining, actor);
      allocations.add(allocation);
      payment.allocate(remaining);
    }

    payments.save(payment);
    paymentAllocations.saveAll(allocations);
    postPaymentDocument(payment, allocations, actor);
    outbox.save(event("PAYMENT", payment.getId(), "PAYMENT_RECEIVED", bookingId,
        "{\"amount\":" + received.toPlainString() + "}"));
    audit.record(actor, "PAYMENT_RECORDED", "BOOKING", bookingId, payment.getId());
    return payment;
  }

  private BigDecimal allocate(Payment payment, List<PaymentAllocation> result, BigDecimal remaining, String type,
      String obligationId, BigDecimal required, String actor) {
    BigDecimal alreadyAllocated = value(paymentAllocations.sumByBookingAndType(payment.getBookingId(), type));
    BigDecimal needed = value(required).subtract(alreadyAllocated).max(BigDecimal.ZERO);
    BigDecimal allocated = remaining.min(needed);
    if (allocated.signum() == 0) return remaining;
    result.add(new PaymentAllocation(payment.getId(), payment.getBookingId(), type, obligationId, allocated, actor));
    payment.allocate(allocated);
    return remaining.subtract(allocated);
  }

  private void postPaymentDocument(Payment payment, List<PaymentAllocation> allocations, String actor) {
    assertPostingPeriodOpen(LocalDateTime.now());
    String documentId = "DOC-" + compactId();
    String key = "document:" + payment.getIdempotencyKey();
    FinancialDocument document = new FinancialDocument(documentId, payment.getBookingId(), "PAYMENT_RECEIPT",
        payment.getAmount(), payment.getBookingId(), key, actor, payment.getNote());
    documents.save(document);
    List<FinancialLedgerEntry> entries = new ArrayList<>();
    entries.add(new FinancialLedgerEntry(documentId, payment.getBookingId(), "CASH_MAIN", "DEBIT",
        payment.getAmount(), null, payment.getBookingId(), key, actor));
    Map<String, BigDecimal> credits = new LinkedHashMap<>();
    for (PaymentAllocation allocation : allocations) {
      String account = switch (allocation.getObligationType()) {
        case RESERVATION_DEPOSIT, SECURITY_DEPOSIT -> "CUSTOMER_DEPOSIT_LIABILITY";
        case RENTAL_PREPAYMENT, EXTENSION_CHARGE -> "UNEARNED_RENTAL_REVENUE";
        case CUSTOMER_RECOVERY -> "CUSTOMER_RECOVERY";
        default -> "CUSTOMER_OVERPAYMENT_LIABILITY";
      };
      credits.merge(account, allocation.getAmount(), BigDecimal::add);
    }
    credits.forEach((account, credit) -> entries.add(new FinancialLedgerEntry(documentId, payment.getBookingId(),
        account, "CREDIT", credit, null, payment.getBookingId(), key, actor)));
    assertBalanced(entries);
    ledger.saveAll(entries);
  }

  private BigDecimal allocateConfirmedCharges(Payment payment, List<PaymentAllocation> result,
      BigDecimal remaining, String actor) {
    for (BookingCharge charge : charges.findByBookingIdOrderByCreatedAtAsc(payment.getBookingId())) {
      if (remaining.signum() == 0) break;
      if (!"CONFIRMED".equals(charge.getStatus())
          || !List.of("LATE_FEE", "MISSING", "DAMAGE", "CUSTOMER_COMPENSATION").contains(charge.getType())) continue;
      BigDecimal already = value(paymentAllocations.sumByObligationId(charge.getId()));
      BigDecimal allocated = remaining.min(charge.getConfirmedAmount().subtract(already).max(BigDecimal.ZERO));
      if (allocated.signum() == 0) continue;
      result.add(new PaymentAllocation(payment.getId(), payment.getBookingId(), CUSTOMER_RECOVERY,
          charge.getId(), allocated, actor));
      payment.allocate(allocated);
      remaining = remaining.subtract(allocated);
    }
    return remaining;
  }

  @Transactional(readOnly = true)
  public void assertCheckoutReady(Booking booking) {
    BigDecimal paid = value(paymentAllocations.sumActiveByBooking(booking.getId()));
    if (paid.compareTo(booking.getAmountDueBeforeHandover()) < 0) {
      throw ApiException.badRequest("Chưa đủ tiền thuê và tiền cọc thực nhận. Còn thiếu "
          + booking.getAmountDueBeforeHandover().subtract(paid).setScale(0, RoundingMode.HALF_UP).toPlainString() + "đ.");
    }
  }

  @Transactional
  public BookingCharge proposeCharge(String bookingId, String type, BigDecimal amount, BigDecimal holdAmount,
      String assetId, String reason, String evidenceReference, LocalDateTime expectedResolutionAt, String actor) {
    bookingForUpdate(bookingId);
    BookingSettlement settlement = settlements.findById(bookingId).orElse(null);
    if (settlement != null && List.of("APPROVED", "REFUND_PENDING", "CLOSED").contains(settlement.getState())) {
      throw ApiException.badRequest("Settlement đã duyệt hoặc đóng. Phải mở lại có kiểm soát trước khi tạo điều chỉnh.");
    }
    String normalizedType = required(type, "Loại phí").toUpperCase();
    if (!List.of("EXTENSION", "LATE_FEE", "MISSING", "DAMAGE", "CUSTOMER_COMPENSATION", "REFUND_ADJUSTMENT")
        .contains(normalizedType)) throw ApiException.badRequest("Loại phí không hợp lệ.");
    BigDecimal hold = value(holdAmount);
    if (hold.signum() > 0 && expectedResolutionAt == null) {
      throw ApiException.badRequest("Khoản tạm giữ phải có hạn xử lý dự kiến.");
    }
    if (List.of("MISSING", "DAMAGE").contains(normalizedType)
        && (evidenceReference == null || evidenceReference.isBlank())) {
      throw ApiException.badRequest("Phí thiếu/hư hỏng phải có bằng chứng hoặc biên bản tham chiếu.");
    }
    BookingCharge charge = charges.save(new BookingCharge(bookingId, normalize(assetId, null), normalizedType,
        positive(amount, "Số tiền đề xuất phải lớn hơn 0."), hold, required(reason, "Lý do"),
        normalize(evidenceReference, ""), expectedResolutionAt, actor));
    outbox.save(event("CHARGE", charge.getId(), "CHARGE_PROPOSED", bookingId, "{}"));
    audit.record(actor, "FINANCE_CHARGE_PROPOSED", "BOOKING", bookingId, charge.getId());
    if (settlement != null) recalculateSettlement(bookingId, actor);
    return charge;
  }

  @Transactional
  public BookingCharge reviewCharge(String chargeId, boolean approved, BigDecimal confirmedAmount, String reason,
      String actor) {
    BookingCharge charge = charges.findById(chargeId).orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản phí."));
    if (!"PROPOSED".equals(charge.getStatus())) throw ApiException.badRequest("Khoản phí đã được xử lý.");
    BookingSettlement currentSettlement = settlements.findById(charge.getBookingId()).orElse(null);
    if (currentSettlement != null && List.of("APPROVED", "REFUND_PENDING", "CLOSED").contains(currentSettlement.getState())) {
      throw ApiException.badRequest("Settlement đã duyệt hoặc đóng; không thể duyệt thêm khoản phí trực tiếp.");
    }
    charge.review(approved, confirmedAmount, actor, required(reason, "Lý do duyệt"));
    charges.save(charge);
    outbox.save(event("CHARGE", charge.getId(), approved ? "CHARGE_CONFIRMED" : "CHARGE_CANCELLED",
        charge.getBookingId(), "{}"));
    audit.record(actor, approved ? "FINANCE_CHARGE_CONFIRMED" : "FINANCE_CHARGE_CANCELLED", "BOOKING",
        charge.getBookingId(), charge.getId());
    if (settlements.existsById(charge.getBookingId())) recalculateSettlement(charge.getBookingId(), actor);
    return charge;
  }

  @Transactional
  public BookingSettlement completeService(Booking booking, String actor) {
    initializeCommercialSnapshot(booking, actor);
    BookingSettlement settlement = settlements.findForUpdate(booking.getId()).orElseGet(() -> new BookingSettlement(booking.getId()));
    if (!settlement.isRevenueRecognized()) {
      BigDecimal recognizedRevenue = grossRentalRevenue(booking.getId(), booking.getTotalAmount());
      postSimpleDocument(booking.getId(), "RENTAL_REVENUE_RECOGNITION", recognizedRevenue,
          "UNEARNED_RENTAL_REVENUE", "RENTAL_REVENUE", "recognize:" + booking.getId(), actor,
          "Ghi nhận doanh thu sau hoàn tất trả máy và kiểm tra");
      allocateAssetRevenue(booking, recognizedRevenue, actor);
      outbox.save(event("BOOKING", booking.getId(), "RENTAL_REVENUE_RECOGNIZED", booking.getId(), "{}"));
    }
    settlements.save(settlement);
    return recalculateSettlement(booking.getId(), actor);
  }

  @Transactional
  public BookingSettlement calculateSettlement(String bookingId, String actor) {
    Booking booking = bookingForUpdate(bookingId);
    if (!"COMPLETED".equals(booking.getState().name())) {
      throw ApiException.badRequest("Chỉ quyết toán sau khi hoàn tất trả máy và kiểm tra.");
    }
    return completeService(booking, actor);
  }

  private BookingSettlement recalculateSettlement(String bookingId, String actor) {
    Booking booking = bookingForUpdate(bookingId);
    BookingSettlement settlement = settlements.findForUpdate(bookingId).orElseGet(() -> new BookingSettlement(bookingId));
    BigDecimal deposits = sumAllocation(bookingId, RESERVATION_DEPOSIT).add(sumAllocation(bookingId, SECURITY_DEPOSIT));
    BigDecimal deductions = charges.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .filter(charge -> "CONFIRMED".equals(charge.getStatus()))
        .filter(charge -> List.of("LATE_FEE", "MISSING", "DAMAGE", "CUSTOMER_COMPENSATION").contains(charge.getType()))
        .map(charge -> charge.getConfirmedAmount().subtract(
            value(paymentAllocations.sumByObligationId(charge.getId()))).max(BigDecimal.ZERO))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal extensionDue = confirmedChargeTotal(bookingId, "EXTENSION")
        .subtract(sumAllocation(bookingId, EXTENSION_CHARGE)).max(BigDecimal.ZERO);
    BigDecimal settlementDeductions = deductions.add(extensionDue);
    BigDecimal hold = charges.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .filter(charge -> "PROPOSED".equals(charge.getStatus()))
        .map(BookingCharge::getTemporaryHoldAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal successfulRefund = value(refunds.sumSucceeded(bookingId));
    settlement.calculate(grossRentalRevenue(bookingId, booking.getTotalAmount()), deposits,
        settlementDeductions, hold, successfulRefund);
    settlements.save(settlement);
    syncReceivable(settlement, actor);
    outbox.save(event("SETTLEMENT", bookingId, "SETTLEMENT_READY", bookingId,
        "{\"refundDueNow\":" + settlement.getRefundDueNow().toPlainString() + "}"));
    return settlement;
  }

  private void syncReceivable(BookingSettlement settlement, String actor) {
    if (settlement.getReceivableAmount().signum() == 0) return;
    List<CustomerReceivable> existing = receivables.findByBookingIdOrderByCreatedAtAsc(settlement.getBookingId());
    if (existing.isEmpty()) receivables.save(new CustomerReceivable(settlement.getBookingId(),
        settlement.getReceivableAmount(), actor));
  }

  @Transactional
  public RefundRequest approveSettlement(String bookingId, String method, String actor) {
    BookingSettlement settlement = recalculateSettlement(bookingId, actor);
    if (!settlement.isRevenueRecognized()) throw ApiException.badRequest("Chưa đủ điều kiện ghi nhận doanh thu.");
    settlement.approve(actor);
    settlements.save(settlement);
    receivables.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .filter(item -> "OPEN".equals(item.getState())).forEach(item -> { item.formalize(); receivables.save(item); });
    RefundRequest refund = null;
    if (settlement.getRefundDueNow().signum() > 0) {
      refund = refunds.findByBookingIdOrderByRequestedAtAsc(bookingId).stream()
          .filter(item -> List.of("APPROVED", "PROCESSING", "FAILED").contains(item.getState()))
          .findFirst().orElse(null);
      if (refund == null) refund = refunds.save(new RefundRequest(bookingId, settlement.getRefundDueNow(),
          required(method, "Phương thức hoàn"), "refund:" + bookingId + ":" + settlement.getVersion(), actor));
      outbox.save(event("REFUND", refund.getId(), "REFUND_APPROVED", bookingId, "{}"));
    }
    audit.record(actor, "SETTLEMENT_APPROVED", "BOOKING", bookingId, refund == null ? "Không phát sinh hoàn" : refund.getId());
    return refund;
  }

  @Transactional
  public RefundRequest executeRefund(String refundId, String payoutReference, String idempotencyKey, String actor) {
    RefundRequest refund = refunds.findForUpdate(refundId).orElseThrow(() -> ApiException.notFound("Không tìm thấy yêu cầu hoàn."));
    if ("SUCCEEDED".equals(refund.getState())) return refund;
    String key = requiredKey(idempotencyKey);
    FinancialDocument duplicate = documents.findByIdempotencyKey("document:" + key).orElse(null);
    if (duplicate != null) return refund;
    BookingSettlement settlement = settlements.findForUpdate(refund.getBookingId())
        .orElseThrow(() -> ApiException.badRequest("Booking chưa có settlement."));
    if (refund.getAmount().compareTo(settlement.getRefundDueNow()) > 0) {
      throw ApiException.badRequest("Số tiền hoàn vượt số dư được phép hoàn.");
    }
    refund.succeed(required(payoutReference, "Mã chi tiền/biên nhận"));
    refunds.save(refund);
    settlement.markRefunded(refund.getAmount());
    settlements.save(settlement);
    postSimpleDocument(refund.getBookingId(), "REFUND_PAYOUT", refund.getAmount(),
        "CUSTOMER_DEPOSIT_LIABILITY", "CASH_MAIN", key, actor, "Hoàn cọc " + refund.getId());
    outbox.save(event("REFUND", refund.getId(), "REFUND_SUCCEEDED", refund.getBookingId(), "{}"));
    audit.record(actor, "REFUND_SUCCEEDED", "BOOKING", refund.getBookingId(), refund.getId());
    return refund;
  }

  @Transactional
  public BookingSettlement closeSettlement(String bookingId, String actor) {
    BookingSettlement settlement = settlements.findForUpdate(bookingId)
        .orElseThrow(() -> ApiException.badRequest("Booking chưa có settlement."));
    if (settlement.getTemporaryHoldAmount().signum() > 0) throw ApiException.badRequest("Còn khoản tạm giữ chưa xử lý.");
    if (settlement.getRefundDueNow().signum() > 0) throw ApiException.badRequest("Còn số tiền phải hoàn cho khách.");
    boolean pendingRefund = refunds.findByBookingIdOrderByRequestedAtAsc(bookingId).stream()
        .anyMatch(item -> !List.of("SUCCEEDED", "CANCELLED", "RETURNED").contains(item.getState()));
    if (pendingRefund) throw ApiException.badRequest("Còn yêu cầu hoàn tiền chưa hoàn tất.");
    boolean unresolvedCharge = charges.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .anyMatch(item -> "PROPOSED".equals(item.getStatus()));
    if (unresolvedCharge) throw ApiException.badRequest("Còn khoản phí đề xuất chưa duyệt.");
    boolean unformalizedDebt = receivables.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .anyMatch(item -> "OPEN".equals(item.getState()));
    if (unformalizedDebt) throw ApiException.badRequest("Công nợ phải được chuyển thành hồ sơ nợ chính thức.");
    if (findings.existsByBookingIdAndCodeAndState(bookingId, "CRITICAL_INVARIANT", "OPEN")) {
      throw ApiException.badRequest("Booking còn lỗi đối soát mức Critical.");
    }
    assertAssetAllocationBalanced(bookingId, settlement.getRecognizedRevenue());
    settlement.close();
    settlements.save(settlement);
    outbox.save(event("SETTLEMENT", bookingId, "FINANCIAL_CLOSED", bookingId, "{}"));
    audit.record(actor, "FINANCIAL_CLOSED", "BOOKING", bookingId, "Close checklist passed");
    return settlement;
  }

  private void allocateAssetRevenue(Booking booking, BigDecimal recognizedRevenue, String actor) {
    if (!assetAllocations.findByBookingIdOrderByProductIdAscAssetIdAsc(booking.getId()).isEmpty()) return;
    List<CommercialSnapshotLine> lines = snapshotLines.findByBookingIdOrderByIdAsc(booking.getId());
    List<BookingAllocation> operational = bookingAllocations.findByBookingIdOrderByCreatedAtAsc(booking.getId());
    List<AllocationBasis> bases = new ArrayList<>();
    for (CommercialSnapshotLine line : lines) {
      List<BookingAllocation> matches = operational.stream()
          .filter(item -> item.getProductId().equals(line.getProductId())).toList();
      if (matches.isEmpty()) {
        bases.add(new AllocationBasis(line.getProductId(), null,
            line.getListedUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()))));
      } else {
        for (BookingAllocation allocation : matches) {
          bases.add(new AllocationBasis(line.getProductId(), allocation.getSerialId(),
              line.getListedUnitPrice().multiply(BigDecimal.valueOf(allocation.getQuantity()))));
        }
      }
    }
    bases.sort(Comparator.comparing(AllocationBasis::stableKey));
    BigDecimal denominator = bases.stream().map(AllocationBasis::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (denominator.signum() == 0) {
      createFinding(booking.getId(), "ZERO_ALLOCATION_BASIS", "CRITICAL",
          "Không thể phân bổ doanh thu vì tổng giá niêm yết snapshot bằng 0.");
      throw ApiException.badRequest("Snapshot giá niêm yết bằng 0; không thể phân bổ doanh thu tự động.");
    }
    BigDecimal remaining = recognizedRevenue;
    List<AssetRevenueAllocation> result = new ArrayList<>();
    for (int index = 0; index < bases.size(); index++) {
      AllocationBasis basis = bases.get(index);
      BigDecimal rate = basis.weight().divide(denominator, 10, RoundingMode.HALF_UP);
      BigDecimal allocated = index == bases.size() - 1
          ? remaining
          : recognizedRevenue.multiply(rate).setScale(0, RoundingMode.HALF_UP).min(remaining);
      remaining = remaining.subtract(allocated);
      result.add(new AssetRevenueAllocation(booking.getId(), basis.productId(), basis.assetId(), basis.weight(),
          denominator, rate, allocated));
    }
    assetAllocations.saveAll(result);
    assertAssetAllocationBalanced(booking.getId(), recognizedRevenue);
    outbox.save(event("BOOKING", booking.getId(), "ASSET_REVENUE_ALLOCATED", booking.getId(),
        "{\"ruleVersion\":\"LIST_PRICE_V1\"}"));
  }

  private void assertAssetAllocationBalanced(String bookingId, BigDecimal expected) {
    BigDecimal actual = assetAllocations.findByBookingIdOrderByProductIdAscAssetIdAsc(bookingId).stream()
        .map(AssetRevenueAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (actual.compareTo(value(expected)) != 0) {
      createFinding(bookingId, "ASSET_ALLOCATION_IMBALANCE", "CRITICAL",
          "Tổng phân bổ " + actual + " không bằng doanh thu " + expected + ".");
      throw ApiException.badRequest("Phân bổ doanh thu theo thiết bị chưa cân.");
    }
  }

  @Transactional
  public OperationalExpense submitExpense(String bookingId, String assetId, String category, BigDecimal amount,
      String vendorName, String invoiceReference, String sourceFingerprint, String reason,
      String evidenceReference, String actor) {
    String fingerprint = requiredKey(sourceFingerprint);
    OperationalExpense duplicate = expenses.findBySourceFingerprint(fingerprint).orElse(null);
    if (duplicate != null) return duplicate;
    if (bookingId != null && !bookingId.isBlank() && !bookings.existsById(bookingId.trim())) {
      throw ApiException.notFound("Không tìm thấy booking gắn với chi phí.");
    }
    if (assetId != null && !assetId.isBlank() && !inventoryAssets.existsById(assetId.trim())) {
      throw ApiException.notFound("Không tìm thấy serial máy gắn với chi phí.");
    }
    OperationalExpense expense = expenses.save(new OperationalExpense(normalize(bookingId, null),
        normalize(assetId, null), "MAIN", required(category, "Nhóm chi phí").toUpperCase(),
        positive(amount, "Số tiền chi phí phải lớn hơn 0."), required(vendorName, "Nhà cung cấp"),
        required(invoiceReference, "Số hóa đơn/chứng từ"), fingerprint, required(reason, "Lý do"),
        required(evidenceReference, "Bằng chứng"), actor));
    outbox.save(event("EXPENSE", expense.getId(), "EXPENSE_SUBMITTED", expense.getId(), "{}"));
    audit.record(actor, "EXPENSE_SUBMITTED", "EXPENSE", expense.getId(), expense.getSourceFingerprint());
    return expense;
  }

  @Transactional
  public OperationalExpense approveExpense(String expenseId, String actor) {
    OperationalExpense expense = expenses.findById(expenseId)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi phí."));
    if (!"SUBMITTED".equals(expense.getState())) return expense;
    assertPostingPeriodOpen(LocalDateTime.now());
    expense.approve(actor);
    expenses.save(expense);
    postExpenseDocument(expense, actor);
    if (expense.getAssetId() != null) {
      assetCosts.save(new AssetCostAllocation(expense.getId(), expense.getBookingId(), expense.getAssetId(),
          expense.getAmount()));
    }
    outbox.save(event("EXPENSE", expense.getId(), "EXPENSE_APPROVED", expense.getId(), "{}"));
    audit.record(actor, "EXPENSE_APPROVED", "EXPENSE", expense.getId(), expense.getInvoiceReference());
    return expense;
  }

  @Transactional
  public OperationalExpense payExpense(String expenseId, BigDecimal amount, String payoutReference,
      String idempotencyKey, String actor) {
    String key = requiredKey(idempotencyKey);
    FinancialDocument duplicate = documents.findByIdempotencyKey("document:" + key).orElse(null);
    OperationalExpense expense = expenses.findById(expenseId)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy chi phí."));
    if (duplicate != null) return expense;
    if (!List.of("APPROVED", "PARTIALLY_PAID").contains(expense.getState())) {
      throw ApiException.badRequest("Chi phí phải được duyệt trước khi chi tiền.");
    }
    BigDecimal paid = positive(amount, "Số tiền chi phải lớn hơn 0.");
    BigDecimal outstanding = expense.getAmount().subtract(expense.getPaidAmount());
    if (paid.compareTo(outstanding) > 0) throw ApiException.badRequest("Số tiền chi vượt công nợ nhà cung cấp.");
    postSimpleDocument(expense.getBookingId(), "EXPENSE_PAYMENT", paid, "VENDOR_PAYABLE", "CASH_MAIN",
        key, actor, "Chi nhà cung cấp " + required(payoutReference, "Mã chi tiền"));
    expense.pay(paid);
    expenses.save(expense);
    outbox.save(event("EXPENSE", expense.getId(), "EXPENSE_PAID", expense.getId(),
        "{\"amount\":" + paid.toPlainString() + "}"));
    audit.record(actor, "EXPENSE_PAID", "EXPENSE", expense.getId(), payoutReference);
    return expense;
  }

  private void postExpenseDocument(OperationalExpense expense, String actor) {
    String key = "document:expense:" + expense.getId();
    if (documents.findByIdempotencyKey(key).isPresent()) return;
    String documentId = "DOC-" + compactId();
    documents.save(new FinancialDocument(documentId, expense.getBookingId(), "EXPENSE_RECOGNITION",
        expense.getAmount(), expense.getId(), key, actor, expense.getReason()));
    List<FinancialLedgerEntry> entries = List.of(
        new FinancialLedgerEntry(documentId, expense.getBookingId(), "OPERATING_EXPENSE", "DEBIT",
            expense.getAmount(), expense.getAssetId(), expense.getId(), key, actor),
        new FinancialLedgerEntry(documentId, expense.getBookingId(), "VENDOR_PAYABLE", "CREDIT",
            expense.getAmount(), expense.getAssetId(), expense.getId(), key, actor));
    assertBalanced(entries);
    ledger.saveAll(entries);
  }

  @Transactional(readOnly = true)
  public List<OperationalExpense> expenses() {
    return expenses.findAllByOrderByCreatedAtDesc();
  }

  @Transactional
  public FinancialDocument reverseDocument(String documentId, String reason, String idempotencyKey, String actor) {
    String key = "document:" + requiredKey(idempotencyKey);
    FinancialDocument duplicate = documents.findByIdempotencyKey(key).orElse(null);
    if (duplicate != null) return duplicate;
    FinancialDocument original = documents.findById(documentId)
        .orElseThrow(() -> ApiException.notFound("Không tìm thấy chứng từ."));
    if (!"POSTED".equals(original.getStatus()) || documents.existsByReversalOfDocumentId(documentId)) {
      throw ApiException.badRequest("Chứng từ đã được đảo hoặc không còn ở trạng thái POSTED.");
    }
    assertPostingPeriodOpen(LocalDateTime.now());
    FinancialDocument reversal = new FinancialDocument("DOC-" + compactId(), original.getBookingId(),
        "REVERSAL_" + original.getType(), original.getTotalDebit(), original.getCorrelationId(), key, actor,
        required(reason, "Lý do đảo chứng từ"));
    reversal.linkReversal(original.getId());
    documents.save(reversal);
    List<FinancialLedgerEntry> reversedEntries = ledger.findByDocumentIdOrderByIdAsc(documentId).stream()
        .map(entry -> {
          FinancialLedgerEntry reversed = new FinancialLedgerEntry(reversal.getId(), entry.getBookingId(),
              entry.getAccountCode(), "DEBIT".equals(entry.getDirection()) ? "CREDIT" : "DEBIT",
              entry.getAmount(), entry.getAssetId(), original.getCorrelationId(), key, actor);
          reversed.linkReversal(entry.getId());
          return reversed;
        }).toList();
    assertBalanced(reversedEntries);
    ledger.saveAll(reversedEntries);
    original.reverse(actor);
    documents.save(original);
    // Chứng từ ghi nhận tiền (PAYMENT_RECEIPT) có một Payment gốc đứng sau —
    // đảo chứng từ kế toán không tự động đảo Payment đó, nên nếu bỏ qua bước này
    // "Đã thực nhận" trên đơn vẫn coi như đã đủ tiền từ lần ghi nhầm và admin sẽ
    // không thấy ô để ghi nhận lại số tiền đúng.
    if ("PAYMENT_RECEIPT".equals(original.getType()) && original.getIdempotencyKey() != null
        && original.getIdempotencyKey().startsWith("document:")) {
      String paymentKey = original.getIdempotencyKey().substring("document:".length());
      payments.findByIdempotencyKey(paymentKey).ifPresent(payment -> {
        payment.markReversed();
        payments.save(payment);
      });
    }
    outbox.save(event("DOCUMENT", reversal.getId(), "FINANCIAL_DOCUMENT_REVERSED",
        original.getCorrelationId(), "{\"originalDocumentId\":\"" + original.getId() + "\"}"));
    audit.record(actor, "FINANCIAL_DOCUMENT_REVERSED", "FINANCIAL_DOCUMENT", original.getId(), reversal.getId());
    return reversal;
  }

  @Transactional(readOnly = true)
  public List<FinancialDocument> documents() {
    return documents.findAll().stream().sorted(Comparator.comparing(FinancialDocument::getPostedAt).reversed()).toList();
  }

  @Transactional
  public FinancialPeriod updatePeriod(String periodId, String state, String actor) {
    YearMonth month;
    try { month = YearMonth.parse(periodId); }
    catch (Exception exception) { throw ApiException.badRequest("Kỳ tài chính phải có dạng YYYY-MM."); }
    FinancialPeriod period = periods.findById(periodId).orElseGet(() -> new FinancialPeriod(periodId,
        month.atDay(1), month.atEndOfMonth()));
    switch (required(state, "Trạng thái kỳ").toUpperCase()) {
      case "OPEN" -> period.reopen();
      case "SOFT_LOCKED" -> period.softLock(actor);
      case "HARD_LOCKED" -> {
        if (findings.countBySeverityAndState("CRITICAL", "OPEN") > 0) {
          throw ApiException.badRequest("Không thể khóa cứng khi còn lỗi đối soát Critical.");
        }
        period.hardLock(actor);
      }
      default -> throw ApiException.badRequest("Trạng thái kỳ không hợp lệ.");
    }
    periods.save(period);
    audit.record(actor, "FINANCIAL_PERIOD_" + period.getState(), "FINANCIAL_PERIOD", periodId, "");
    return period;
  }

  @Transactional(readOnly = true)
  public List<FinancialPeriod> periods() {
    return periods.findAll().stream().sorted(Comparator.comparing(FinancialPeriod::getId).reversed()).toList();
  }

  private void assertPostingPeriodOpen(LocalDateTime effectiveAt) {
    YearMonth month = YearMonth.from(effectiveAt);
    String id = month.toString();
    FinancialPeriod period = periods.findById(id).orElseGet(() -> periods.save(new FinancialPeriod(id,
        month.atDay(1), month.atEndOfMonth())));
    if (!"OPEN".equals(period.getState())) {
      throw ApiException.badRequest("Kỳ tài chính " + id + " đã khóa; hãy ghi điều chỉnh ở kỳ đang mở.");
    }
  }

  @Transactional(readOnly = true)
  public List<AssetProfitability> assetProfitability() {
    Map<String, BigDecimal> revenue = new HashMap<>();
    assetAllocations.findAll().forEach(item -> revenue.merge(
        normalize(item.getAssetId(), "UNALLOCATED:" + item.getProductId()), item.getAmount(), BigDecimal::add));
    Map<String, BigDecimal> cost = new HashMap<>();
    assetCosts.findAll().forEach(item -> cost.merge(item.getAssetId(), item.getAmount(), BigDecimal::add));
    Map<String, BigDecimal> recovery = new HashMap<>();
    Map<String, BookingCharge> chargeById = new HashMap<>();
    charges.findAll().forEach(item -> chargeById.put(item.getId(), item));
    List<String> reversedPaymentIdsForRecovery = payments.findByStatus("REVERSED").stream().map(Payment::getId).toList();
    paymentAllocations.findAll().stream()
        .filter(item -> !reversedPaymentIdsForRecovery.contains(item.getPaymentId()))
        .filter(item -> CUSTOMER_RECOVERY.equals(item.getObligationType()))
        .forEach(item -> {
          BookingCharge charge = chargeById.get(item.getObligationId());
          if (charge != null && charge.getAssetId() != null) {
            recovery.merge(charge.getAssetId(), item.getAmount(), BigDecimal::add);
          }
        });
    return java.util.stream.Stream.concat(java.util.stream.Stream.concat(revenue.keySet().stream(),
        cost.keySet().stream()), recovery.keySet().stream()).distinct().sorted()
        .map(assetId -> {
          BigDecimal allocatedRevenue = revenue.getOrDefault(assetId, BigDecimal.ZERO);
          BigDecimal lifecycleCost = cost.getOrDefault(assetId, BigDecimal.ZERO);
          BigDecimal recovered = recovery.getOrDefault(assetId, BigDecimal.ZERO);
          BigDecimal netCost = lifecycleCost.subtract(recovered).max(BigDecimal.ZERO);
          return new AssetProfitability(assetId, allocatedRevenue, lifecycleCost, recovered,
              netCost, allocatedRevenue.subtract(netCost));
        }).toList();
  }

  @Transactional(readOnly = true)
  public BookingFinanceView bookingView(String bookingId) {
    if (!bookings.existsById(bookingId)) throw ApiException.notFound("Không tìm thấy booking.");
    List<Payment> bookingPayments = payments.findByBookingIdOrderByReceivedAtAsc(bookingId);
    List<String> reversedPaymentIds = bookingPayments.stream()
        .filter(payment -> "REVERSED".equals(payment.getStatus())).map(Payment::getId).toList();
    List<PaymentAllocation> activeAllocations = paymentAllocations.findByBookingIdOrderByAllocatedAtAsc(bookingId).stream()
        .filter(allocation -> !reversedPaymentIds.contains(allocation.getPaymentId())).toList();
    return new BookingFinanceView(snapshots.findById(bookingId).orElse(null),
        snapshotLines.findByBookingIdOrderByIdAsc(bookingId), bookingPayments,
        activeAllocations, charges.findByBookingIdOrderByCreatedAtAsc(bookingId),
        settlements.findById(bookingId).orElse(null), refunds.findByBookingIdOrderByRequestedAtAsc(bookingId),
        receivables.findByBookingIdOrderByCreatedAtAsc(bookingId), assetAllocations.findByBookingIdOrderByProductIdAscAssetIdAsc(bookingId),
        ledger.findByBookingIdOrderByPostedAtAsc(bookingId), findings.findByBookingIdAndStateOrderByDetectedAtAsc(bookingId, "OPEN"),
        outbox.findByAggregateIdOrderByCreatedAtAsc(bookingId));
  }

  @Transactional(readOnly = true)
  public FinanceDashboard dashboard() {
    BigDecimal physicalCash = payments.findByStatus("SUCCEEDED").stream().map(Payment::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .subtract(refunds.findAll().stream().filter(item -> "SUCCEEDED".equals(item.getState()))
            .map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    List<String> reversedPaymentIds = payments.findByStatus("REVERSED").stream().map(Payment::getId).toList();
    BigDecimal depositsHeld = paymentAllocations.findAll().stream()
        .filter(item -> !reversedPaymentIds.contains(item.getPaymentId()))
        .filter(item -> List.of(RESERVATION_DEPOSIT, SECURITY_DEPOSIT).contains(item.getObligationType()))
        .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
        .subtract(refunds.findAll().stream().filter(item -> "SUCCEEDED".equals(item.getState()))
            .map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).max(BigDecimal.ZERO);
    BigDecimal unearned = paymentAllocations.findAll().stream()
        .filter(item -> !reversedPaymentIds.contains(item.getPaymentId()))
        .filter(item -> List.of(RENTAL_PREPAYMENT, EXTENSION_CHARGE).contains(item.getObligationType()))
        .filter(item -> settlements.findById(item.getBookingId()).map(value -> !value.isRevenueRecognized()).orElse(true))
        .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal committedRefunds = refunds.findByStateIn(List.of("APPROVED", "PROCESSING", "FAILED")).stream()
        .map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal paidExpenses = expenses.findByStateIn(List.of("PARTIALLY_PAID", "PAID")).stream()
        .map(OperationalExpense::getPaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    physicalCash = physicalCash.subtract(paidExpenses);
    BigDecimal unpaidExpenses = expenses.findByStateIn(List.of("APPROVED", "PARTIALLY_PAID")).stream()
        .map(item -> item.getAmount().subtract(item.getPaidAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal committedOutflows = committedRefunds.add(unpaidExpenses);
    BigDecimal revenue = settlements.findAll().stream().filter(BookingSettlement::isRevenueRecognized)
        .map(BookingSettlement::getRecognizedRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal outstandingDebt = receivables.findByStateIn(List.of("OPEN", "FORMALIZED", "OVERDUE")).stream()
        .map(CustomerReceivable::getOutstandingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal recognizedExpenses = expenses.findByStateIn(List.of("APPROVED", "PARTIALLY_PAID", "PAID")).stream()
        .map(OperationalExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal available = physicalCash.subtract(depositsHeld).subtract(unearned).subtract(committedOutflows);
    long overdueRefunds = refunds.findByStateIn(List.of("APPROVED", "PROCESSING", "FAILED")).stream()
        .filter(item -> item.getApprovedAt() != null && item.getApprovedAt().plusHours(24).isBefore(LocalDateTime.now())).count();
    return new FinanceDashboard(physicalCash, depositsHeld.add(unearned), committedOutflows, available,
        revenue, recognizedExpenses, revenue.subtract(recognizedExpenses), outstandingDebt, overdueRefunds,
        findings.countBySeverityAndState("CRITICAL", "OPEN"));
  }

  @Transactional(readOnly = true)
  public List<FinancialLedgerEntry> ledgerEntries() {
    return ledger.findAllByOrderByPostedAtDesc();
  }

  @Transactional
  public List<ReconciliationFinding> reconcile(String bookingId) {
    BookingSettlement settlement = settlements.findById(bookingId).orElse(null);
    if (settlement == null) return List.of();
    if ("CLOSED".equals(settlement.getState()) && (settlement.getRefundDueNow().signum() > 0
        || settlement.getTemporaryHoldAmount().signum() > 0)) {
      createFinding(bookingId, "CRITICAL_INVARIANT", "CRITICAL", "Settlement đã đóng nhưng còn nghĩa vụ tiền.");
    }
    BigDecimal allocated = assetAllocations.findByBookingIdOrderByProductIdAscAssetIdAsc(bookingId).stream()
        .map(AssetRevenueAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (settlement.isRevenueRecognized() && allocated.compareTo(settlement.getRecognizedRevenue()) != 0) {
      createFinding(bookingId, "ASSET_ALLOCATION_IMBALANCE", "CRITICAL", "Phân bổ doanh thu theo asset bị lệch.");
    }
    return findings.findByBookingIdAndStateOrderByDetectedAtAsc(bookingId, "OPEN");
  }

  private void createFinding(String bookingId, String code, String severity, String detail) {
    if (!findings.existsByBookingIdAndCodeAndState(bookingId, code, "OPEN")) {
      findings.save(new ReconciliationFinding(bookingId, code, severity, detail));
    }
  }

  private void postSimpleDocument(String bookingId, String type, BigDecimal amount, String debitAccount,
      String creditAccount, String idempotencyKey, String actor, String reason) {
    String key = "document:" + idempotencyKey;
    if (documents.findByIdempotencyKey(key).isPresent()) return;
    assertPostingPeriodOpen(LocalDateTime.now());
    String documentId = "DOC-" + compactId();
    documents.save(new FinancialDocument(documentId, bookingId, type, amount, bookingId, key, actor, reason));
    List<FinancialLedgerEntry> entries = List.of(
        new FinancialLedgerEntry(documentId, bookingId, debitAccount, "DEBIT", amount, null, bookingId, key, actor),
        new FinancialLedgerEntry(documentId, bookingId, creditAccount, "CREDIT", amount, null, bookingId, key, actor));
    assertBalanced(entries);
    ledger.saveAll(entries);
  }

  private void assertBalanced(List<FinancialLedgerEntry> entries) {
    BigDecimal debit = entries.stream().filter(item -> "DEBIT".equals(item.getDirection()))
        .map(FinancialLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal credit = entries.stream().filter(item -> "CREDIT".equals(item.getDirection()))
        .map(FinancialLedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (debit.compareTo(credit) != 0) throw new IllegalStateException("Financial document is not balanced");
  }

  private BigDecimal sumAllocation(String bookingId, String type) {
    return value(paymentAllocations.sumByBookingAndType(bookingId, type));
  }

  /** Tổng tiền giữ lịch (cọc 50.000đ) đã được ghi nhận và đối soát cho đơn này. */
  public BigDecimal reservationDepositPaid(String bookingId) {
    return sumAllocation(bookingId, RESERVATION_DEPOSIT);
  }
  private BigDecimal confirmedChargeTotal(String bookingId, String type) {
    return charges.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .filter(item -> "CONFIRMED".equals(item.getStatus()) && type.equals(item.getType()))
        .map(BookingCharge::getConfirmedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
  private BigDecimal grossRentalRevenue(String bookingId, BigDecimal baseRental) {
    return value(baseRental).add(confirmedChargeTotal(bookingId, "EXTENSION"))
        .subtract(confirmedChargeTotal(bookingId, "REFUND_ADJUSTMENT")).max(BigDecimal.ZERO);
  }
  private Booking bookingForUpdate(String id) {
    return bookings.findByIdWithItemsForUpdate(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy booking."));
  }
  private FinanceOutboxEvent event(String aggregateType, String aggregateId, String eventType,
      String correlationId, String payload) {
    return new FinanceOutboxEvent(aggregateType, aggregateId, eventType, correlationId, payload);
  }
  private static BigDecimal value(BigDecimal amount) { return amount == null ? BigDecimal.ZERO : amount; }
  private static BigDecimal positive(BigDecimal value, String message) {
    if (value == null || value.signum() <= 0) throw ApiException.badRequest(message);
    return value.setScale(0, RoundingMode.HALF_UP);
  }
  private static String required(String value, String label) {
    if (value == null || value.isBlank()) throw ApiException.badRequest(label + " là bắt buộc.");
    return value.trim();
  }
  private static String requiredKey(String value) {
    if (value == null || value.isBlank()) throw ApiException.badRequest("Idempotency key là bắt buộc.");
    return value.trim();
  }
  private static String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
  private static String compactId() { return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(); }

  private record AllocationBasis(String productId, String assetId, BigDecimal weight) {
    String stableKey() { return productId + ":" + Objects.toString(assetId, "~"); }
  }
  public record BookingFinanceView(CommercialSnapshot snapshot, List<CommercialSnapshotLine> snapshotLines,
      List<Payment> payments, List<PaymentAllocation> paymentAllocations, List<BookingCharge> charges,
      BookingSettlement settlement, List<RefundRequest> refunds, List<CustomerReceivable> receivables,
      List<AssetRevenueAllocation> assetRevenueAllocations, List<FinancialLedgerEntry> ledgerEntries,
      List<ReconciliationFinding> reconciliationFindings, List<FinanceOutboxEvent> events) {}
  public record FinanceDashboard(BigDecimal physicalCash, BigDecimal restrictedCustomerFunds,
      BigDecimal committedOutflows, BigDecimal availableCash, BigDecimal recognizedRevenue,
      BigDecimal recognizedExpenses, BigDecimal contributionMargin, BigDecimal outstandingReceivables,
      long overdueRefunds, long criticalFindings) {}
  public record AssetProfitability(String assetId, BigDecimal allocatedRevenue, BigDecimal grossLifecycleCost,
      BigDecimal recoveryReceived, BigDecimal netLifecycleCost, BigDecimal contributionMargin) {}
}
