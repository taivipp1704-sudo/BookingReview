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
  // Loại các khoản phân bổ thuộc về một Payment đã bị đảo (ghi nhận nhầm rồi đảo
  // chứng từ) — nếu không, admin ghi lại đúng số tiền sẽ bị hệ thống coi là đã đủ
  // tiền từ lần ghi nhầm trước đó.
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a, Payment p "
      + "where a.paymentId = p.id and p.status <> 'REVERSED' and a.bookingId = :bookingId and a.obligationType = :type")
  BigDecimal sumByBookingAndType(@Param("bookingId") String bookingId, @Param("type") String type);
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a, Payment p "
      + "where a.paymentId = p.id and p.status <> 'REVERSED' and a.obligationId = :obligationId")
  BigDecimal sumByObligationId(@Param("obligationId") String obligationId);
  @Query("select coalesce(sum(a.amount), 0) from PaymentAllocation a, Payment p "
      + "where a.paymentId = p.id and p.status <> 'REVERSED' and a.bookingId = :bookingId")
  BigDecimal sumActiveByBooking(@Param("bookingId") String bookingId);
}
