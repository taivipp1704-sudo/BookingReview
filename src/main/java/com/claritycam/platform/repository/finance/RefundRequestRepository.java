package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.RefundRequest;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, String> {
  Optional<RefundRequest> findByIdempotencyKey(String idempotencyKey);
  List<RefundRequest> findByBookingIdOrderByRequestedAtAsc(String bookingId);
  List<RefundRequest> findByStateIn(List<String> states);
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RefundRequest r where r.id = :id")
  Optional<RefundRequest> findForUpdate(@Param("id") String id);
  @Query("select coalesce(sum(r.amount), 0) from RefundRequest r where r.bookingId = :bookingId and r.state = 'SUCCEEDED'")
  BigDecimal sumSucceeded(@Param("bookingId") String bookingId);
}
