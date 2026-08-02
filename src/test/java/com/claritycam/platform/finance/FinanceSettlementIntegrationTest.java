package com.claritycam.platform.finance;

import com.claritycam.platform.model.finance.AssetRevenueAllocation;
import com.claritycam.platform.model.finance.BookingCharge;
import com.claritycam.platform.model.finance.BookingSettlement;
import com.claritycam.platform.model.finance.FinancialDocument;
import com.claritycam.platform.model.finance.OperationalExpense;
import com.claritycam.platform.model.finance.Payment;
import com.claritycam.platform.model.finance.RefundRequest;
import com.claritycam.platform.repository.booking.BookingAllocationRepository;
import com.claritycam.platform.repository.booking.BookingRepository;
import com.claritycam.platform.repository.finance.FinancialDocumentRepository;
import com.claritycam.platform.repository.finance.FinancialLedgerEntryRepository;
import com.claritycam.platform.repository.finance.PaymentRepository;
import com.claritycam.platform.repository.inventory.InventoryAssetRepository;
import com.claritycam.platform.service.finance.FinanceSettlementService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.claritycam.platform.model.booking.AllocationRole;
import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingAllocation;
import com.claritycam.platform.model.booking.BookingLine;
import com.claritycam.platform.model.booking.BookingState;
import com.claritycam.platform.exception.ApiException;
import com.claritycam.platform.model.inventory.InventoryAsset;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinanceSettlementIntegrationTest {
  @Autowired private FinanceSettlementService finance;
  @Autowired private BookingRepository bookings;
  @Autowired private BookingAllocationRepository bookingAllocations;
  @Autowired private PaymentRepository payments;
  @Autowired private FinancialDocumentRepository documents;
  @Autowired private FinancialLedgerEntryRepository ledger;
  @Autowired private InventoryAssetRepository inventoryAssets;

  @Test
  void duplicatePaymentRetryCreatesOnePaymentAndOneDocument() {
    Booking booking = booking("IDEMPOTENT", BigDecimal.valueOf(800_000), BigDecimal.valueOf(100_000),
        List.of(line("GEAR-001", 1, 800_000)));
    finance.initializeCommercialSnapshot(booking, "TEST");

    Payment first = finance.recordPayment(booking.getId(), BigDecimal.valueOf(900_000), "BANK_TRANSFER",
        "BANK-TX-001-" + booking.getId(), "pay-key-" + booking.getId(), "test", "TEST");
    Payment retry = finance.recordPayment(booking.getId(), BigDecimal.valueOf(900_000), "BANK_TRANSFER",
        "BANK-TX-001-" + booking.getId(), "pay-key-" + booking.getId(), "test", "TEST");

    assertEquals(first.getId(), retry.getId());
    assertEquals(1, payments.findByBookingIdOrderByReceivedAtAsc(booking.getId()).size());
    assertEquals(1, documents.findByBookingIdOrderByPostedAtDesc(booking.getId()).size());
  }

  @Test
  void blocksCheckoutUntilTheFullRentalAndDepositsAreReceived() {
    Booking booking = booking("CHECKOUT", BigDecimal.valueOf(800_000), BigDecimal.valueOf(100_000),
        List.of(line("GEAR-001", 1, 800_000)));
    assertThrows(ApiException.class, () -> finance.assertCheckoutReady(booking));
    finance.recordPayment(booking.getId(), BigDecimal.valueOf(900_000), "CASH", null,
        "checkout-payment-" + booking.getId(), "signed receipt", "TEST");
    finance.assertCheckoutReady(booking);
  }

  @Test
  void allocatesRevenueExactlyAndRefundExecutionIsIdempotent() {
    Booking booking = booking("ALLOCATION", BigDecimal.valueOf(800_000), BigDecimal.valueOf(100_000), List.of(
        line("GEAR-001", 1, 400_000), line("GEAR-002", 1, 300_000), line("GEAR-003", 1, 300_000)));
    bookingAllocations.saveAll(List.of(
        new BookingAllocation(booking.getId(), "GEAR-001", "FX3-FIN-01", 1, AllocationRole.PRIMARY, "TEST"),
        new BookingAllocation(booking.getId(), "GEAR-002", "R5-FIN-01", 1, AllocationRole.PRIMARY, "TEST"),
        new BookingAllocation(booking.getId(), "GEAR-003", "MAVIC-FIN-01", 1, AllocationRole.PRIMARY, "TEST")));
    finance.recordPayment(booking.getId(), BigDecimal.valueOf(900_000), "BANK_TRANSFER", null,
        "allocation-payment-" + booking.getId(), "test", "TEST");
    booking.changeState(BookingState.COMPLETED, "Return inspection passed");
    bookings.save(booking);

    BookingSettlement settlement = finance.completeService(booking, "TEST");
    assertEquals(0, BigDecimal.valueOf(100_000).compareTo(settlement.getRefundDueNow()));
    List<AssetRevenueAllocation> allocations = finance.bookingView(booking.getId()).assetRevenueAllocations();
    assertEquals(List.of(320_000L, 240_000L, 240_000L),
        allocations.stream().map(item -> item.getAmount().longValueExact()).toList());

    RefundRequest refund = finance.approveSettlement(booking.getId(), "BANK_TRANSFER", "TEST");
    assertNotNull(refund);
    RefundRequest first = finance.executeRefund(refund.getId(), "PAYOUT-001", "refund-exec-001", "TEST");
    RefundRequest retry = finance.executeRefund(refund.getId(), "PAYOUT-001", "refund-exec-001", "TEST");
    assertEquals(first.getId(), retry.getId());
    assertEquals("SUCCEEDED", retry.getState());
    assertEquals("CLOSED", finance.closeSettlement(booking.getId(), "TEST").getState());
  }

  @Test
  void expenseRequiresApprovalSupportsPartialPaymentAndFeedsAssetProfitability() {
    String assetId = "ASSET-FIN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    inventoryAssets.save(new InventoryAsset(assetId, "GEAR-001", "AVAILABLE", 0, LocalDate.now(), 0));
    String fingerprint = "INV-" + UUID.randomUUID();
    OperationalExpense expense = finance.submitExpense(null, assetId, "REPAIR", BigDecimal.valueOf(600_000),
        "Repair Vendor", "INV-001", fingerprint, "Replace shutter", "evidence://invoice", "TEST");
    OperationalExpense duplicate = finance.submitExpense(null, assetId, "REPAIR", BigDecimal.valueOf(600_000),
        "Repair Vendor", "INV-001", fingerprint, "Replace shutter", "evidence://invoice", "TEST");
    assertEquals(expense.getId(), duplicate.getId());

    expense = finance.approveExpense(expense.getId(), "TEST");
    assertEquals("APPROVED", expense.getState());
    expense = finance.payExpense(expense.getId(), BigDecimal.valueOf(200_000), "PAY-REPAIR-1",
        "expense-payment-1-" + expense.getId(), "TEST");
    assertEquals("PARTIALLY_PAID", expense.getState());
    assertEquals(0, BigDecimal.valueOf(200_000).compareTo(expense.getPaidAmount()));
    String expenseId = expense.getId();
    assertThrows(ApiException.class, () -> finance.payExpense(expenseId, BigDecimal.valueOf(500_000),
        "PAY-OVER", "expense-overpayment-" + expenseId, "TEST"));
    assertEquals(0, BigDecimal.valueOf(600_000).compareTo(finance.assetProfitability().stream()
        .filter(item -> assetId.equals(item.assetId())).findFirst().orElseThrow().grossLifecycleCost()));
  }

  @Test
  void hardLockedPeriodBlocksPostingAndPostedDocumentCanOnlyBeReversed() {
    Booking booking = booking("CONTROL", BigDecimal.valueOf(500_000), BigDecimal.ZERO,
        List.of(line("GEAR-001", 1, 500_000)));
    finance.recordPayment(booking.getId(), BigDecimal.valueOf(500_000), "BANK_TRANSFER", null,
        "control-payment-" + booking.getId(), "test", "TEST");
    FinancialDocument original = documents.findByBookingIdOrderByPostedAtDesc(booking.getId()).getFirst();
    FinancialDocument reversal = finance.reverseDocument(original.getId(), "Wrong provider mapping",
        "reverse-control-" + booking.getId(), "TEST");
    FinancialDocument retry = finance.reverseDocument(original.getId(), "Wrong provider mapping",
        "reverse-control-" + booking.getId(), "TEST");
    assertEquals(reversal.getId(), retry.getId());
    assertEquals(original.getId(), reversal.getReversalOfDocumentId());
    assertEquals("REVERSED", documents.findById(original.getId()).orElseThrow().getStatus());
    assertEquals(2, ledger.findByDocumentIdOrderByIdAsc(reversal.getId()).size());

    String periodId = YearMonth.now().toString();
    finance.updatePeriod(periodId, "HARD_LOCKED", "TEST");
    Booking blocked = booking("LOCKED", BigDecimal.valueOf(100_000), BigDecimal.ZERO,
        List.of(line("GEAR-001", 1, 100_000)));
    assertThrows(ApiException.class, () -> finance.recordPayment(blocked.getId(), BigDecimal.valueOf(100_000),
        "CASH", null, "locked-payment-" + blocked.getId(), "test", "TEST"));
  }

  @Test
  void paidExtensionIncreasesRevenueWithoutBeingDeductedFromDepositAgain() {
    Booking booking = booking("EXTENSION", BigDecimal.valueOf(800_000), BigDecimal.valueOf(100_000),
        List.of(line("GEAR-001", 1, 800_000)));
    BookingCharge charge = finance.proposeCharge(booking.getId(), "EXTENSION", BigDecimal.valueOf(200_000),
        BigDecimal.ZERO, null, "Extend one rental day", null, null, "TEST");
    finance.reviewCharge(charge.getId(), true, BigDecimal.valueOf(200_000), "Approved extension", "TEST");
    finance.recordPayment(booking.getId(), BigDecimal.valueOf(1_100_000), "BANK_TRANSFER", null,
        "extension-payment-" + booking.getId(), "base, deposit and extension", "TEST");
    booking.changeState(BookingState.COMPLETED, "Return inspection passed");
    bookings.save(booking);

    BookingSettlement settlement = finance.completeService(booking, "TEST");
    assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(settlement.getRecognizedRevenue()));
    assertEquals(0, BigDecimal.valueOf(100_000).compareTo(settlement.getRefundDueNow()));
    assertEquals(0, BigDecimal.valueOf(1_000_000).compareTo(finance.bookingView(booking.getId())
        .assetRevenueAllocations().stream().map(AssetRevenueAllocation::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add)));
  }

  @Test
  void paidDamageRecoveryIsNotDeductedFromDepositTwice() {
    Booking booking = booking("RECOVERY", BigDecimal.valueOf(800_000), BigDecimal.valueOf(100_000),
        List.of(line("GEAR-001", 1, 800_000)));
    finance.recordPayment(booking.getId(), BigDecimal.valueOf(900_000), "BANK_TRANSFER", null,
        "base-payment-" + booking.getId(), "base and deposit", "TEST");
    BookingCharge charge = finance.proposeCharge(booking.getId(), "DAMAGE", BigDecimal.valueOf(200_000),
        BigDecimal.ZERO, "ASSET-DAMAGED-01", "Broken mount", "evidence://inspection", null, "TEST");
    finance.reviewCharge(charge.getId(), true, BigDecimal.valueOf(200_000), "Inspection confirmed", "TEST");
    finance.recordPayment(booking.getId(), BigDecimal.valueOf(200_000), "BANK_TRANSFER", null,
        "recovery-payment-" + booking.getId(), "damage recovery", "TEST");
    booking.changeState(BookingState.COMPLETED, "Return inspection passed");
    bookings.save(booking);

    BookingSettlement settlement = finance.completeService(booking, "TEST");
    assertEquals(0, BigDecimal.valueOf(100_000).compareTo(settlement.getRefundDueNow()));
    assertEquals(0, BigDecimal.valueOf(200_000).compareTo(finance.assetProfitability().stream()
        .filter(item -> "ASSET-DAMAGED-01".equals(item.assetId())).findFirst().orElseThrow().recoveryReceived()));
  }

  private Booking booking(String suffix, BigDecimal rental, BigDecimal deposit, List<BookingLine> lines) {
    String id = "FIN-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    Booking booking = new Booking(id, "Finance Test", "0900000000", "0900000000", BookingState.CONFIRMED,
        rental, deposit, LocalDateTime.now().plusDays(2), LocalDateTime.now().plusDays(3), null, "test", lines);
    return bookings.saveAndFlush(booking);
  }

  private BookingLine line(String productId, int quantity, long listedPrice) {
    BigDecimal price = BigDecimal.valueOf(listedPrice);
    return new BookingLine(productId, null, quantity, price, price, price, "DAY", 1, "RENTAL_V1");
  }
}
