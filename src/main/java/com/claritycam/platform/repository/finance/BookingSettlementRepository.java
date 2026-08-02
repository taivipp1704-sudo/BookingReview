package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.BookingSettlement;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingSettlementRepository extends JpaRepository<BookingSettlement, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from BookingSettlement s where s.bookingId = :bookingId")
  Optional<BookingSettlement> findForUpdate(@Param("bookingId") String bookingId);
  List<BookingSettlement> findByStateIn(List<String> states);
}
