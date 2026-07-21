package com.claritycam.platform.booking;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingReservationRepository extends JpaRepository<BookingReservation, String> {
  List<BookingReservation> findByBookingIdOrderByCreatedAtAsc(String bookingId);
  List<BookingReservation> findByBookingIdAndState(String bookingId, ReservationState state);
  List<BookingReservation> findByProductIdAndState(String productId, ReservationState state);
  List<BookingReservation> findByStateAndExpiresAtBefore(ReservationState state, LocalDateTime expiresAt);
  boolean existsByBookingIdAndState(String bookingId, ReservationState state);
}
