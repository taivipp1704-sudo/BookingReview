package com.claritycam.platform.finance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class FinanceSettlementController {
  private final FinanceSettlementService finance;
  private final FinanceAuthorizationService authorization;

  public FinanceSettlementController(FinanceSettlementService finance, FinanceAuthorizationService authorization) {
    this.finance = finance;
    this.authorization = authorization;
  }

  @GetMapping("/dashboard")
  FinanceSettlementService.FinanceDashboard dashboard(Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.dashboard();
  }

  @GetMapping("/ledger")
  List<FinancialLedgerEntry> ledger(Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.ledgerEntries();
  }

  @GetMapping("/documents")
  List<FinancialDocument> documents(Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.documents();
  }

  @GetMapping("/expenses")
  List<OperationalExpense> expenses(Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.expenses();
  }

  @PostMapping("/expenses")
  OperationalExpense submitExpense(@Valid @RequestBody ExpenseRequest request, Authentication authentication) {
    authorization.require(authentication, "EXPENSE_SUBMIT");
    return finance.submitExpense(request.bookingId(), request.assetId(), request.category(), request.amount(),
        request.vendorName(), request.invoiceReference(), request.sourceFingerprint(), request.reason(),
        request.evidenceReference(), authentication.getName());
  }

  @PostMapping("/expenses/{expenseId}/approve")
  OperationalExpense approveExpense(@PathVariable String expenseId, Authentication authentication) {
    authorization.require(authentication, "EXPENSE_APPROVE");
    return finance.approveExpense(expenseId, authentication.getName());
  }

  @PostMapping("/expenses/{expenseId}/pay")
  OperationalExpense payExpense(@PathVariable String expenseId, @Valid @RequestBody ExpensePaymentRequest request,
      Authentication authentication) {
    authorization.require(authentication, "EXPENSE_PAY");
    return finance.payExpense(expenseId, request.amount(), request.payoutReference(), request.idempotencyKey(),
        authentication.getName());
  }

  @PostMapping("/documents/{documentId}/reverse")
  FinancialDocument reverseDocument(@PathVariable String documentId, @Valid @RequestBody ReversalRequest request,
      Authentication authentication) {
    authorization.require(authentication, "DOCUMENT_REVERSE");
    return finance.reverseDocument(documentId, request.reason(), request.idempotencyKey(), authentication.getName());
  }

  @GetMapping("/periods")
  List<FinancialPeriod> periods(Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.periods();
  }

  @PatchMapping("/periods/{periodId}")
  FinancialPeriod updatePeriod(@PathVariable String periodId, @Valid @RequestBody PeriodRequest request,
      Authentication authentication) {
    authorization.require(authentication, "PERIOD_CLOSE");
    if ("OPEN".equalsIgnoreCase(request.state())) authorization.require(authentication, "PERIOD_REOPEN");
    return finance.updatePeriod(periodId, request.state(), authentication.getName());
  }

  @GetMapping("/asset-profitability")
  List<FinanceSettlementService.AssetProfitability> assetProfitability(Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.assetProfitability();
  }

  @GetMapping("/bookings/{bookingId}")
  FinanceSettlementService.BookingFinanceView booking(@PathVariable String bookingId, Authentication authentication) {
    authorization.require(authentication, "FINANCE_VIEW");
    return finance.bookingView(bookingId);
  }

  @PostMapping("/payments")
  Payment recordPayment(@Valid @RequestBody PaymentRequest request, Authentication authentication) {
    authorization.require(authentication, "PAYMENT_RECORD");
    return finance.recordPayment(request.bookingId(), request.amount(), request.method(), request.providerReference(),
        request.idempotencyKey(), request.note(), authentication.getName());
  }

  @PostMapping("/bookings/{bookingId}/charges")
  FinanceSettlementService.BookingFinanceView proposeCharge(@PathVariable String bookingId,
      @Valid @RequestBody ChargeRequest request, Authentication authentication) {
    authorization.require(authentication, "CHARGE_CONFIRM");
    finance.proposeCharge(bookingId, request.type(), request.amount(), request.temporaryHoldAmount(), request.assetId(),
        request.reason(), request.evidenceReference(), request.expectedResolutionAt(), authentication.getName());
    return finance.bookingView(bookingId);
  }

  @PatchMapping("/charges/{chargeId}")
  BookingCharge reviewCharge(@PathVariable String chargeId, @Valid @RequestBody ChargeReviewRequest request,
      Authentication authentication) {
    authorization.require(authentication, "CHARGE_CONFIRM");
    return finance.reviewCharge(chargeId, request.approved(), request.confirmedAmount(), request.reason(),
        authentication.getName());
  }

  @PostMapping("/bookings/{bookingId}/settlement/approve")
  FinanceSettlementService.BookingFinanceView approveSettlement(@PathVariable String bookingId,
      @Valid @RequestBody SettlementApprovalRequest request, Authentication authentication) {
    authorization.require(authentication, "SETTLEMENT_APPROVE");
    finance.approveSettlement(bookingId, request.refundMethod(), authentication.getName());
    return finance.bookingView(bookingId);
  }

  @PostMapping("/bookings/{bookingId}/settlement/calculate")
  FinanceSettlementService.BookingFinanceView calculateSettlement(@PathVariable String bookingId,
      Authentication authentication) {
    authorization.require(authentication, "SETTLEMENT_APPROVE");
    finance.calculateSettlement(bookingId, authentication.getName());
    return finance.bookingView(bookingId);
  }

  @PostMapping("/refunds/{refundId}/execute")
  RefundRequest executeRefund(@PathVariable String refundId, @Valid @RequestBody RefundExecutionRequest request,
      Authentication authentication) {
    authorization.require(authentication, "REFUND_EXECUTE");
    return finance.executeRefund(refundId, request.payoutReference(), request.idempotencyKey(), authentication.getName());
  }

  @PostMapping("/bookings/{bookingId}/settlement/close")
  FinanceSettlementService.BookingFinanceView closeSettlement(@PathVariable String bookingId,
      Authentication authentication) {
    authorization.require(authentication, "FINANCIAL_CLOSE");
    finance.closeSettlement(bookingId, authentication.getName());
    return finance.bookingView(bookingId);
  }

  @PostMapping("/bookings/{bookingId}/reconcile")
  List<ReconciliationFinding> reconcile(@PathVariable String bookingId, Authentication authentication) {
    authorization.require(authentication, "RECONCILE");
    return finance.reconcile(bookingId);
  }

  public record PaymentRequest(@NotBlank String bookingId, @NotNull @DecimalMin("1") BigDecimal amount,
      @NotBlank @Size(max = 40) String method, @Size(max = 160) String providerReference,
      @NotBlank @Size(max = 160) String idempotencyKey, @Size(max = 500) String note) {}
  public record ChargeRequest(@NotBlank String type, @NotNull @DecimalMin("1") BigDecimal amount,
      @DecimalMin("0") BigDecimal temporaryHoldAmount, @Size(max = 64) String assetId,
      @NotBlank @Size(max = 500) String reason, @Size(max = 1000) String evidenceReference,
      LocalDateTime expectedResolutionAt) {}
  public record ChargeReviewRequest(boolean approved, @DecimalMin("0") BigDecimal confirmedAmount,
      @NotBlank @Size(max = 500) String reason) {}
  public record SettlementApprovalRequest(@NotBlank @Size(max = 40) String refundMethod) {}
  public record RefundExecutionRequest(@NotBlank @Size(max = 160) String payoutReference,
      @NotBlank @Size(max = 160) String idempotencyKey) {}
  public record ExpenseRequest(@Size(max = 96) String bookingId, @Size(max = 64) String assetId,
      @NotBlank @Size(max = 40) String category, @NotNull @DecimalMin("1") BigDecimal amount,
      @NotBlank @Size(max = 180) String vendorName, @NotBlank @Size(max = 160) String invoiceReference,
      @NotBlank @Size(max = 160) String sourceFingerprint, @NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 1000) String evidenceReference) {}
  public record ExpensePaymentRequest(@NotNull @DecimalMin("1") BigDecimal amount,
      @NotBlank @Size(max = 160) String payoutReference,
      @NotBlank @Size(max = 160) String idempotencyKey) {}
  public record ReversalRequest(@NotBlank @Size(max = 500) String reason,
      @NotBlank @Size(max = 160) String idempotencyKey) {}
  public record PeriodRequest(@NotBlank @Size(max = 32) String state) {}
}
