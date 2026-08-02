package com.claritycam.platform.repository.finance;

import com.claritycam.platform.model.finance.BookingCharge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingChargeRepository extends JpaRepository<BookingCharge, String> {
  List<BookingCharge> findByBookingIdOrderByCreatedAtAsc(String bookingId);
  boolean existsByBookingIdAndType(String bookingId, String type);
}
