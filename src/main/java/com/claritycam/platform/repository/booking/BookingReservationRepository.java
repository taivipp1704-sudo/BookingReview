package com.claritycam.platform.repository.booking;

import com.claritycam.platform.model.booking.Booking;
import com.claritycam.platform.model.booking.BookingReservation;
import com.claritycam.platform.model.booking.ReservationState;
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
