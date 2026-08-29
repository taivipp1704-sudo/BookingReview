package com.claritycam.platform.repository.booking;

import com.claritycam.platform.model.booking.BookingFeedback;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingFeedbackRepository extends JpaRepository<BookingFeedback, String> {
  Optional<BookingFeedback> findByBookingId(String bookingId);

  List<BookingFeedback> findAllByOrderByCreatedAtDesc();
}
