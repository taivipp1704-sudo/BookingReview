package com.claritycam.platform.finance;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CommercialSnapshotRepository extends JpaRepository<CommercialSnapshot, String> {}

interface CommercialSnapshotLineRepository extends JpaRepository<CommercialSnapshotLine, String> {
  List<CommercialSnapshotLine> findByBookingIdOrderByIdAsc(String bookingId);
}

interface FinancialDocumentRepository extends JpaRepository<FinancialDocument, String> {
  Optional<FinancialDocument> findByIdempotencyKey(String idempotencyKey);
  List<FinancialDocument> findByBookingIdOrderByPostedAtDesc(String bookingId);
  boolean existsByReversalOfDocumentId(String reversalOfDocumentId);
}

interface FinancialLedgerEntryRepository extends JpaRepository<FinancialLedgerEntry, String> {
  List<FinancialLedgerEntry> findByBookingIdOrderByPostedAtAsc(String bookingId);
  List<FinancialLedgerEntry> findByDocumentIdOrderByIdAsc(String documentId);
  List<FinancialLedgerEntry> findAllByOrderByPostedAtDesc();
}

interface FinancialPeriodRepository extends JpaRepository<FinancialPeriod, String> {}

interface OperationalExpenseRepository extends JpaRepository<OperationalExpense, String> {
  Optional<OperationalExpense> findBySourceFingerprint(String sourceFingerprint);
  List<OperationalExpense> findAllByOrderByCreatedAtDesc();
  List<OperationalExpense> findByStateIn(List<String> states);
}

interface AssetCostAllocationRepository extends JpaRepository<AssetCostAllocation, String> {
  List<AssetCostAllocation> findByAssetIdOrderByAllocatedAtAsc(String assetId);
  List<AssetCostAllocation> findAllByOrderByAllocatedAtAsc();
}

interface PaymentRepository extends JpaRepository<Payment, String> {
  Optional<Payment> findByProviderTransactionId(String providerTransactionId);
  Optional<Payment> findByIdempotencyKey(String idempotencyKey);
  List<Payment> findByBookingIdOrderByReceivedAtAsc(String bookingId);
  List<Payment> findByStatus(String status);
}

interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, String> {
  List<PaymentAllocation> findByBookingIdOrderByAllocatedAtAsc(String bookingId);
  List<PaymentAllocation> findByPaymentId(String paymentId);
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.bookingId = :bookingId and a.obligationType = :type")
  BigDecimal sumByBookingAndType(@Param("bookingId") String bookingId, @Param("type") String type);
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.obligationId = :obligationId")
  BigDecimal sumByObligationId(@Param("obligationId") String obligationId);
}

interface BookingChargeRepository extends JpaRepository<BookingCharge, String> {
  List<BookingCharge> findByBookingIdOrderByCreatedAtAsc(String bookingId);
  boolean existsByBookingIdAndType(String bookingId, String type);
}

interface BookingSettlementRepository extends JpaRepository<BookingSettlement, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from BookingSettlement s where s.bookingId = :bookingId")
  Optional<BookingSettlement> findForUpdate(@Param("bookingId") String bookingId);
  List<BookingSettlement> findByStateIn(List<String> states);
}

interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {
  Optional<RefundRequest> findByIdempotencyKey(String idempotencyKey);
  List<RefundRequest> findByBookingIdOrderByRequestedAtAsc(String bookingId);
  List<RefundRequest> findByStateIn(List<String> states);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RefundRequest r where r.id = :id")
  Optional<RefundRequest> findForUpdate(@Param("id") String id);
  @Query("select coalesce(sum(r.amount), 0) from RefundRequest r where r.bookingId = :bookingId and r.state = 'SUCCEEDED'")
  BigDecimal sumSucceeded(@Param("bookingId") String bookingId);
}

interface CustomerReceivableRepository extends JpaRepository<CustomerReceivable, String> {
  List<CustomerReceivable> findByBookingIdOrderByCreatedAtAsc(String bookingId);
  List<CustomerReceivable> findByStateIn(List<String> states);
}

interface AssetRevenueAllocationRepository extends JpaRepository<AssetRevenueAllocation, String> {
  List<AssetRevenueAllocation> findByBookingIdOrderByProductIdAscAssetIdAsc(String bookingId);
  void deleteByBookingId(String bookingId);
}

interface FinanceOutboxEventRepository extends JpaRepository<FinanceOutboxEvent, String> {
  List<FinanceOutboxEvent> findByAggregateIdOrderByCreatedAtAsc(String aggregateId);
}

interface ReconciliationFindingRepository extends JpaRepository<ReconciliationFinding, String> {
  List<ReconciliationFinding> findByBookingIdAndStateOrderByDetectedAtAsc(String bookingId, String state);
  List<ReconciliationFinding> findByStateOrderByDetectedAtDesc(String state);
  boolean existsByBookingIdAndCodeAndState(String bookingId, String code, String state);
  long countBySeverityAndState(String severity, String state);
}
