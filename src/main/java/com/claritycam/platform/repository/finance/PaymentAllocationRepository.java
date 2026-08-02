package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.PaymentAllocation;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, String> {
  List<PaymentAllocation> findByBookingIdOrderByAllocatedAtAsc(String bookingId);
  List<PaymentAllocation> findByPaymentId(String paymentId);
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.bookingId = :bookingId and a.obligationType = :type")
  BigDecimal sumByBookingAndType(@Param("bookingId") String bookingId, @Param("type") String type);
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a where a.obligationId = :obligationId")
  BigDecimal sumByObligationId(@Param("obligationId") String obligationId);
}
