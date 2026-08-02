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
    BigDecimal received = positive(amount, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân thanh toÃƒÆ’Ã‚Â¡n phÃƒÂ¡Ã‚ÂºÃ‚Â£i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºn hÃƒâ€ Ã‚Â¡n 0.");
    Payment payment = new Payment("PAY-" + compactId(), bookingId, received, required(method, "PhÃƒâ€ Ã‚Â°Ãƒâ€ Ã‚Â¡ng thÃƒÂ¡Ã‚Â»Ã‚Â©c thanh toÃƒÆ’Ã‚Â¡n"),
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
    BigDecimal paid = paymentAllocations.findByBookingIdOrderByAllocatedAtAsc(booking.getId()).stream()
        .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (paid.compareTo(booking.getAmountDueNow()) < 0) {
      throw ApiException.badRequest("ChÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â§ tiÃƒÂ¡Ã‚Â»Ã‚Ân thuÃƒÆ’Ã‚Âª vÃƒÆ’Ã‚Â  tiÃƒÂ¡Ã‚Â»Ã‚Ân cÃƒÂ¡Ã‚Â»Ã‚Âc thÃƒÂ¡Ã‚Â»Ã‚Â±c nhÃƒÂ¡Ã‚ÂºÃ‚Â­n. CÃƒÆ’Ã‚Â²n thiÃƒÂ¡Ã‚ÂºÃ‚Â¿u "
          + booking.getAmountDueNow().subtract(paid).setScale(0, RoundingMode.HALF_UP).toPlainString() + "Ãƒâ€žÃ¢â‚¬Ëœ.");
    }
  }

  @Transactional
  public BookingCharge proposeCharge(String bookingId, String type, BigDecimal amount, BigDecimal holdAmount,
      String assetId, String reason, String evidenceReference, LocalDateTime expectedResolutionAt, String actor) {
    bookingForUpdate(bookingId);
    BookingSettlement settlement = settlements.findById(bookingId).orElse(null);
    if (settlement != null && List.of("APPROVED", "REFUND_PENDING", "CLOSED").contains(settlement.getState())) {
      throw ApiException.badRequest("Settlement Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t hoÃƒÂ¡Ã‚ÂºÃ‚Â·c Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â³ng. PhÃƒÂ¡Ã‚ÂºÃ‚Â£i mÃƒÂ¡Ã‚Â»Ã…Â¸ lÃƒÂ¡Ã‚ÂºÃ‚Â¡i cÃƒÆ’Ã‚Â³ kiÃƒÂ¡Ã‚Â»Ã†â€™m soÃƒÆ’Ã‚Â¡t trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi tÃƒÂ¡Ã‚ÂºÃ‚Â¡o Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã‚Âu chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â°nh.");
    }
    String normalizedType = required(type, "LoÃƒÂ¡Ã‚ÂºÃ‚Â¡i phÃƒÆ’Ã‚Â­").toUpperCase();
    if (!List.of("EXTENSION", "LATE_FEE", "MISSING", "DAMAGE", "CUSTOMER_COMPENSATION", "REFUND_ADJUSTMENT")
        .contains(normalizedType)) throw ApiException.badRequest("LoÃƒÂ¡Ã‚ÂºÃ‚Â¡i phÃƒÆ’Ã‚Â­ khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
    BigDecimal hold = value(holdAmount);
    if (hold.signum() > 0 && expectedResolutionAt == null) {
      throw ApiException.badRequest("KhoÃƒÂ¡Ã‚ÂºÃ‚Â£n tÃƒÂ¡Ã‚ÂºÃ‚Â¡m giÃƒÂ¡Ã‚Â»Ã‚Â¯ phÃƒÂ¡Ã‚ÂºÃ‚Â£i cÃƒÆ’Ã‚Â³ hÃƒÂ¡Ã‚ÂºÃ‚Â¡n xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½ dÃƒÂ¡Ã‚Â»Ã‚Â± kiÃƒÂ¡Ã‚ÂºÃ‚Â¿n.");
    }
    if (List.of("MISSING", "DAMAGE").contains(normalizedType)
        && (evidenceReference == null || evidenceReference.isBlank())) {
      throw ApiException.badRequest("PhÃƒÆ’Ã‚Â­ thiÃƒÂ¡Ã‚ÂºÃ‚Â¿u/hÃƒâ€ Ã‚Â° hÃƒÂ¡Ã‚Â»Ã‚Âng phÃƒÂ¡Ã‚ÂºÃ‚Â£i cÃƒÆ’Ã‚Â³ bÃƒÂ¡Ã‚ÂºÃ‚Â±ng chÃƒÂ¡Ã‚Â»Ã‚Â©ng hoÃƒÂ¡Ã‚ÂºÃ‚Â·c biÃƒÆ’Ã‚Âªn bÃƒÂ¡Ã‚ÂºÃ‚Â£n tham chiÃƒÂ¡Ã‚ÂºÃ‚Â¿u.");
    }
    BookingCharge charge = charges.save(new BookingCharge(bookingId, normalize(assetId, null), normalizedType,
        positive(amount, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â xuÃƒÂ¡Ã‚ÂºÃ‚Â¥t phÃƒÂ¡Ã‚ÂºÃ‚Â£i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºn hÃƒâ€ Ã‚Â¡n 0."), hold, required(reason, "LÃƒÆ’Ã‚Â½ do"),
        normalize(evidenceReference, ""), expectedResolutionAt, actor));
    outbox.save(event("CHARGE", charge.getId(), "CHARGE_PROPOSED", bookingId, "{}"));
    audit.record(actor, "FINANCE_CHARGE_PROPOSED", "BOOKING", bookingId, charge.getId());
    if (settlement != null) recalculateSettlement(bookingId, actor);
    return charge;
  }

  @Transactional
  public BookingCharge reviewCharge(String chargeId, boolean approved, BigDecimal confirmedAmount, String reason,
      String actor) {
    BookingCharge charge = charges.findById(chargeId).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y khoÃƒÂ¡Ã‚ÂºÃ‚Â£n phÃƒÆ’Ã‚Â­."));
    if (!"PROPOSED".equals(charge.getStatus())) throw ApiException.badRequest("KhoÃƒÂ¡Ã‚ÂºÃ‚Â£n phÃƒÆ’Ã‚Â­ Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½.");
    BookingSettlement currentSettlement = settlements.findById(charge.getBookingId()).orElse(null);
    if (currentSettlement != null && List.of("APPROVED", "REFUND_PENDING", "CLOSED").contains(currentSettlement.getState())) {
      throw ApiException.badRequest("Settlement Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t hoÃƒÂ¡Ã‚ÂºÃ‚Â·c Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â³ng; khÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t thÃƒÆ’Ã‚Âªm khoÃƒÂ¡Ã‚ÂºÃ‚Â£n phÃƒÆ’Ã‚Â­ trÃƒÂ¡Ã‚Â»Ã‚Â±c tiÃƒÂ¡Ã‚ÂºÃ‚Â¿p.");
    }
    charge.review(approved, confirmedAmount, actor, required(reason, "LÃƒÆ’Ã‚Â½ do duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t"));
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
          "Ghi nhÃƒÂ¡Ã‚ÂºÃ‚Â­n doanh thu sau hoÃƒÆ’Ã‚Â n tÃƒÂ¡Ã‚ÂºÃ‚Â¥t trÃƒÂ¡Ã‚ÂºÃ‚Â£ mÃƒÆ’Ã‚Â¡y vÃƒÆ’Ã‚Â  kiÃƒÂ¡Ã‚Â»Ã†â€™m tra");
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
      throw ApiException.badRequest("ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° quyÃƒÂ¡Ã‚ÂºÃ‚Â¿t toÃƒÆ’Ã‚Â¡n sau khi hoÃƒÆ’Ã‚Â n tÃƒÂ¡Ã‚ÂºÃ‚Â¥t trÃƒÂ¡Ã‚ÂºÃ‚Â£ mÃƒÆ’Ã‚Â¡y vÃƒÆ’Ã‚Â  kiÃƒÂ¡Ã‚Â»Ã†â€™m tra.");
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
    if (!settlement.isRevenueRecognized()) throw ApiException.badRequest("ChÃƒâ€ Ã‚Â°a Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â§ Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã‚Âu kiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n ghi nhÃƒÂ¡Ã‚ÂºÃ‚Â­n doanh thu.");
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
          required(method, "PhÃƒâ€ Ã‚Â°Ãƒâ€ Ã‚Â¡ng thÃƒÂ¡Ã‚Â»Ã‚Â©c hoÃƒÆ’Ã‚Â n"), "refund:" + bookingId + ":" + settlement.getVersion(), actor));
      outbox.save(event("REFUND", refund.getId(), "REFUND_APPROVED", bookingId, "{}"));
    }
    audit.record(actor, "SETTLEMENT_APPROVED", "BOOKING", bookingId, refund == null ? "KhÃƒÆ’Ã‚Â´ng phÃƒÆ’Ã‚Â¡t sinh hoÃƒÆ’Ã‚Â n" : refund.getId());
    return refund;
  }

  @Transactional
  public RefundRequest executeRefund(String refundId, String payoutReference, String idempotencyKey, String actor) {
    RefundRequest refund = refunds.findForUpdate(refundId).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u hoÃƒÆ’Ã‚Â n."));
    if ("SUCCEEDED".equals(refund.getState())) return refund;
    String key = requiredKey(idempotencyKey);
    FinancialDocument duplicate = documents.findByIdempotencyKey("document:" + key).orElse(null);
    if (duplicate != null) return refund;
    BookingSettlement settlement = settlements.findForUpdate(refund.getBookingId())
        .orElseThrow(() -> ApiException.badRequest("Booking chÃƒâ€ Ã‚Â°a cÃƒÆ’Ã‚Â³ settlement."));
    if (refund.getAmount().compareTo(settlement.getRefundDueNow()) > 0) {
      throw ApiException.badRequest("SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân hoÃƒÆ’Ã‚Â n vÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£t sÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ dÃƒâ€ Ã‚Â° Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c phÃƒÆ’Ã‚Â©p hoÃƒÆ’Ã‚Â n.");
    }
    refund.succeed(required(payoutReference, "MÃƒÆ’Ã‚Â£ chi tiÃƒÂ¡Ã‚Â»Ã‚Ân/biÃƒÆ’Ã‚Âªn nhÃƒÂ¡Ã‚ÂºÃ‚Â­n"));
    refunds.save(refund);
    settlement.markRefunded(refund.getAmount());
    settlements.save(settlement);
    postSimpleDocument(refund.getBookingId(), "REFUND_PAYOUT", refund.getAmount(),
        "CUSTOMER_DEPOSIT_LIABILITY", "CASH_MAIN", key, actor, "HoÃƒÆ’Ã‚Â n cÃƒÂ¡Ã‚Â»Ã‚Âc " + refund.getId());
    outbox.save(event("REFUND", refund.getId(), "REFUND_SUCCEEDED", refund.getBookingId(), "{}"));
    audit.record(actor, "REFUND_SUCCEEDED", "BOOKING", refund.getBookingId(), refund.getId());
    return refund;
  }

  @Transactional
  public BookingSettlement closeSettlement(String bookingId, String actor) {
    BookingSettlement settlement = settlements.findForUpdate(bookingId)
        .orElseThrow(() -> ApiException.badRequest("Booking chÃƒâ€ Ã‚Â°a cÃƒÆ’Ã‚Â³ settlement."));
    if (settlement.getTemporaryHoldAmount().signum() > 0) throw ApiException.badRequest("CÃƒÆ’Ã‚Â²n khoÃƒÂ¡Ã‚ÂºÃ‚Â£n tÃƒÂ¡Ã‚ÂºÃ‚Â¡m giÃƒÂ¡Ã‚Â»Ã‚Â¯ chÃƒâ€ Ã‚Â°a xÃƒÂ¡Ã‚Â»Ã‚Â­ lÃƒÆ’Ã‚Â½.");
    if (settlement.getRefundDueNow().signum() > 0) throw ApiException.badRequest("CÃƒÆ’Ã‚Â²n sÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân phÃƒÂ¡Ã‚ÂºÃ‚Â£i hoÃƒÆ’Ã‚Â n cho khÃƒÆ’Ã‚Â¡ch.");
    boolean pendingRefund = refunds.findByBookingIdOrderByRequestedAtAsc(bookingId).stream()
        .anyMatch(item -> !List.of("SUCCEEDED", "CANCELLED", "RETURNED").contains(item.getState()));
    if (pendingRefund) throw ApiException.badRequest("CÃƒÆ’Ã‚Â²n yÃƒÆ’Ã‚Âªu cÃƒÂ¡Ã‚ÂºÃ‚Â§u hoÃƒÆ’Ã‚Â n tiÃƒÂ¡Ã‚Â»Ã‚Ân chÃƒâ€ Ã‚Â°a hoÃƒÆ’Ã‚Â n tÃƒÂ¡Ã‚ÂºÃ‚Â¥t.");
    boolean unresolvedCharge = charges.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .anyMatch(item -> "PROPOSED".equals(item.getStatus()));
    if (unresolvedCharge) throw ApiException.badRequest("CÃƒÆ’Ã‚Â²n khoÃƒÂ¡Ã‚ÂºÃ‚Â£n phÃƒÆ’Ã‚Â­ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã‚Â xuÃƒÂ¡Ã‚ÂºÃ‚Â¥t chÃƒâ€ Ã‚Â°a duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t.");
    boolean unformalizedDebt = receivables.findByBookingIdOrderByCreatedAtAsc(bookingId).stream()
        .anyMatch(item -> "OPEN".equals(item.getState()));
    if (unformalizedDebt) throw ApiException.badRequest("CÃƒÆ’Ã‚Â´ng nÃƒÂ¡Ã‚Â»Ã‚Â£ phÃƒÂ¡Ã‚ÂºÃ‚Â£i Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c chuyÃƒÂ¡Ã‚Â»Ã†â€™n thÃƒÆ’Ã‚Â nh hÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“ sÃƒâ€ Ã‚Â¡ nÃƒÂ¡Ã‚Â»Ã‚Â£ chÃƒÆ’Ã‚Â­nh thÃƒÂ¡Ã‚Â»Ã‚Â©c.");
    if (findings.existsByBookingIdAndCodeAndState(bookingId, "CRITICAL_INVARIANT", "OPEN")) {
      throw ApiException.badRequest("Booking cÃƒÆ’Ã‚Â²n lÃƒÂ¡Ã‚Â»Ã¢â‚¬â€i Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœi soÃƒÆ’Ã‚Â¡t mÃƒÂ¡Ã‚Â»Ã‚Â©c Critical.");
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
          "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ phÃƒÆ’Ã‚Â¢n bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ doanh thu vÃƒÆ’Ã‚Â¬ tÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ng giÃƒÆ’Ã‚Â¡ niÃƒÆ’Ã‚Âªm yÃƒÂ¡Ã‚ÂºÃ‚Â¿t snapshot bÃƒÂ¡Ã‚ÂºÃ‚Â±ng 0.");
      throw ApiException.badRequest("Snapshot giÃƒÆ’Ã‚Â¡ niÃƒÆ’Ã‚Âªm yÃƒÂ¡Ã‚ÂºÃ‚Â¿t bÃƒÂ¡Ã‚ÂºÃ‚Â±ng 0; khÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ phÃƒÆ’Ã‚Â¢n bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ doanh thu tÃƒÂ¡Ã‚Â»Ã‚Â± Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢ng.");
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
          "TÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ng phÃƒÆ’Ã‚Â¢n bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ " + actual + " khÃƒÆ’Ã‚Â´ng bÃƒÂ¡Ã‚ÂºÃ‚Â±ng doanh thu " + expected + ".");
      throw ApiException.badRequest("PhÃƒÆ’Ã‚Â¢n bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ doanh thu theo thiÃƒÂ¡Ã‚ÂºÃ‚Â¿t bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ chÃƒâ€ Ã‚Â°a cÃƒÆ’Ã‚Â¢n.");
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
      throw ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y booking gÃƒÂ¡Ã‚ÂºÃ‚Â¯n vÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi chi phÃƒÆ’Ã‚Â­.");
    }
    if (assetId != null && !assetId.isBlank() && !inventoryAssets.existsById(assetId.trim())) {
      throw ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y serial mÃƒÆ’Ã‚Â¡y gÃƒÂ¡Ã‚ÂºÃ‚Â¯n vÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi chi phÃƒÆ’Ã‚Â­.");
    }
    OperationalExpense expense = expenses.save(new OperationalExpense(normalize(bookingId, null),
        normalize(assetId, null), "MAIN", required(category, "NhÃƒÆ’Ã‚Â³m chi phÃƒÆ’Ã‚Â­").toUpperCase(),
        positive(amount, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân chi phÃƒÆ’Ã‚Â­ phÃƒÂ¡Ã‚ÂºÃ‚Â£i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºn hÃƒâ€ Ã‚Â¡n 0."), required(vendorName, "NhÃƒÆ’Ã‚Â  cung cÃƒÂ¡Ã‚ÂºÃ‚Â¥p"),
        required(invoiceReference, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ hÃƒÆ’Ã‚Â³a Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â¡n/chÃƒÂ¡Ã‚Â»Ã‚Â©ng tÃƒÂ¡Ã‚Â»Ã‚Â«"), fingerprint, required(reason, "LÃƒÆ’Ã‚Â½ do"),
        required(evidenceReference, "BÃƒÂ¡Ã‚ÂºÃ‚Â±ng chÃƒÂ¡Ã‚Â»Ã‚Â©ng"), actor));
    outbox.save(event("EXPENSE", expense.getId(), "EXPENSE_SUBMITTED", expense.getId(), "{}"));
    audit.record(actor, "EXPENSE_SUBMITTED", "EXPENSE", expense.getId(), expense.getSourceFingerprint());
    return expense;
  }

  @Transactional
  public OperationalExpense approveExpense(String expenseId, String actor) {
    OperationalExpense expense = expenses.findById(expenseId)
        .orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y chi phÃƒÆ’Ã‚Â­."));
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
        .orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y chi phÃƒÆ’Ã‚Â­."));
    if (duplicate != null) return expense;
    if (!List.of("APPROVED", "PARTIALLY_PAID").contains(expense.getState())) {
      throw ApiException.badRequest("Chi phÃƒÆ’Ã‚Â­ phÃƒÂ¡Ã‚ÂºÃ‚Â£i Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c duyÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡t trÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºc khi chi tiÃƒÂ¡Ã‚Â»Ã‚Ân.");
    }
    BigDecimal paid = positive(amount, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân chi phÃƒÂ¡Ã‚ÂºÃ‚Â£i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºn hÃƒâ€ Ã‚Â¡n 0.");
    BigDecimal outstanding = expense.getAmount().subtract(expense.getPaidAmount());
    if (paid.compareTo(outstanding) > 0) throw ApiException.badRequest("SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ tiÃƒÂ¡Ã‚Â»Ã‚Ân chi vÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£t cÃƒÆ’Ã‚Â´ng nÃƒÂ¡Ã‚Â»Ã‚Â£ nhÃƒÆ’Ã‚Â  cung cÃƒÂ¡Ã‚ÂºÃ‚Â¥p.");
    postSimpleDocument(expense.getBookingId(), "EXPENSE_PAYMENT", paid, "VENDOR_PAYABLE", "CASH_MAIN",
        key, actor, "Chi nhÃƒÆ’Ã‚Â  cung cÃƒÂ¡Ã‚ÂºÃ‚Â¥p " + required(payoutReference, "MÃƒÆ’Ã‚Â£ chi tiÃƒÂ¡Ã‚Â»Ã‚Ân"));
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
        .orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y chÃƒÂ¡Ã‚Â»Ã‚Â©ng tÃƒÂ¡Ã‚Â»Ã‚Â«."));
    if (!"POSTED".equals(original.getStatus()) || documents.existsByReversalOfDocumentId(documentId)) {
      throw ApiException.badRequest("ChÃƒÂ¡Ã‚Â»Ã‚Â©ng tÃƒÂ¡Ã‚Â»Ã‚Â« Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â£o hoÃƒÂ¡Ã‚ÂºÃ‚Â·c khÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â²n ÃƒÂ¡Ã‚Â»Ã…Â¸ trÃƒÂ¡Ã‚ÂºÃ‚Â¡ng thÃƒÆ’Ã‚Â¡i POSTED.");
    }
    assertPostingPeriodOpen(LocalDateTime.now());
    FinancialDocument reversal = new FinancialDocument("DOC-" + compactId(), original.getBookingId(),
        "REVERSAL_" + original.getType(), original.getTotalDebit(), original.getCorrelationId(), key, actor,
        required(reason, "LÃƒÆ’Ã‚Â½ do Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â£o chÃƒÂ¡Ã‚Â»Ã‚Â©ng tÃƒÂ¡Ã‚Â»Ã‚Â«"));
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
    catch (Exception exception) { throw ApiException.badRequest("KÃƒÂ¡Ã‚Â»Ã‚Â³ tÃƒÆ’Ã‚Â i chÃƒÆ’Ã‚Â­nh phÃƒÂ¡Ã‚ÂºÃ‚Â£i cÃƒÆ’Ã‚Â³ dÃƒÂ¡Ã‚ÂºÃ‚Â¡ng YYYY-MM."); }
    FinancialPeriod period = periods.findById(periodId).orElseGet(() -> new FinancialPeriod(periodId,
        month.atDay(1), month.atEndOfMonth()));
    switch (required(state, "TrÃƒÂ¡Ã‚ÂºÃ‚Â¡ng thÃƒÆ’Ã‚Â¡i kÃƒÂ¡Ã‚Â»Ã‚Â³").toUpperCase()) {
      case "OPEN" -> period.reopen();
      case "SOFT_LOCKED" -> period.softLock(actor);
      case "HARD_LOCKED" -> {
        if (findings.countBySeverityAndState("CRITICAL", "OPEN") > 0) {
          throw ApiException.badRequest("KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ khÃƒÆ’Ã‚Â³a cÃƒÂ¡Ã‚Â»Ã‚Â©ng khi cÃƒÆ’Ã‚Â²n lÃƒÂ¡Ã‚Â»Ã¢â‚¬â€i Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœi soÃƒÆ’Ã‚Â¡t Critical.");
        }
        period.hardLock(actor);
      }
      default -> throw ApiException.badRequest("TrÃƒÂ¡Ã‚ÂºÃ‚Â¡ng thÃƒÆ’Ã‚Â¡i kÃƒÂ¡Ã‚Â»Ã‚Â³ khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
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
      throw ApiException.badRequest("KÃƒÂ¡Ã‚Â»Ã‚Â³ tÃƒÆ’Ã‚Â i chÃƒÆ’Ã‚Â­nh " + id + " Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ khÃƒÆ’Ã‚Â³a; hÃƒÆ’Ã‚Â£y ghi Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã‚Âu chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â°nh ÃƒÂ¡Ã‚Â»Ã…Â¸ kÃƒÂ¡Ã‚Â»Ã‚Â³ Ãƒâ€žÃ¢â‚¬Ëœang mÃƒÂ¡Ã‚Â»Ã…Â¸.");
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
    paymentAllocations.findAll().stream().filter(item -> CUSTOMER_RECOVERY.equals(item.getObligationType()))
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
    if (!bookings.existsById(bookingId)) throw ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y booking.");
    return new BookingFinanceView(snapshots.findById(bookingId).orElse(null),
        snapshotLines.findByBookingIdOrderByIdAsc(bookingId), payments.findByBookingIdOrderByReceivedAtAsc(bookingId),
        paymentAllocations.findByBookingIdOrderByAllocatedAtAsc(bookingId), charges.findByBookingIdOrderByCreatedAtAsc(bookingId),
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
    BigDecimal depositsHeld = paymentAllocations.findAll().stream()
        .filter(item -> List.of(RESERVATION_DEPOSIT, SECURITY_DEPOSIT).contains(item.getObligationType()))
        .map(PaymentAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
        .subtract(refunds.findAll().stream().filter(item -> "SUCCEEDED".equals(item.getState()))
            .map(RefundRequest::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)).max(BigDecimal.ZERO);
    BigDecimal unearned = paymentAllocations.findAll().stream()
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
      createFinding(bookingId, "CRITICAL_INVARIANT", "CRITICAL", "Settlement Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â³ng nhÃƒâ€ Ã‚Â°ng cÃƒÆ’Ã‚Â²n nghÃƒâ€žÃ‚Â©a vÃƒÂ¡Ã‚Â»Ã‚Â¥ tiÃƒÂ¡Ã‚Â»Ã‚Ân.");
    }
    BigDecimal allocated = assetAllocations.findByBookingIdOrderByProductIdAscAssetIdAsc(bookingId).stream()
        .map(AssetRevenueAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (settlement.isRevenueRecognized() && allocated.compareTo(settlement.getRecognizedRevenue()) != 0) {
      createFinding(bookingId, "ASSET_ALLOCATION_IMBALANCE", "CRITICAL", "PhÃƒÆ’Ã‚Â¢n bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢ doanh thu theo asset bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ch.");
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
    return bookings.findByIdWithItemsForUpdate(id).orElseThrow(() -> ApiException.notFound("KhÃƒÆ’Ã‚Â´ng tÃƒÆ’Ã‚Â¬m thÃƒÂ¡Ã‚ÂºÃ‚Â¥y booking."));
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
    if (value == null || value.isBlank()) throw ApiException.badRequest(label + " lÃƒÆ’Ã‚Â  bÃƒÂ¡Ã‚ÂºÃ‚Â¯t buÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢c.");
    return value.trim();
  }
  private static String requiredKey(String value) {
    if (value == null || value.isBlank()) throw ApiException.badRequest("Idempotency key lÃƒÆ’Ã‚Â  bÃƒÂ¡Ã‚ÂºÃ‚Â¯t buÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢c.");
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
